import { ForbiddenException } from '@nestjs/common';
import { PaymentsService } from '../payments.service';

/**
 * Milestone 12 (release hardening): targeted tests for the two idempotency
 * guarantees Milestone 0's threat model (T8, "gateway retries a webhook
 * delivery") and API design (§7.7, "every payment-order-creating endpoint
 * is idempotency-key-safe") call out by name.
 *
 * Both properties are enforced with real, narrow mechanisms already in
 * payments.service.ts — a DB-unique constraint on
 * (gateway, gateway_event_id) for webhook replay dedup, and an
 * idempotency_key lookup-before-create for order creation — so these tests
 * exercise the service's public methods against a hand-rolled fake Prisma
 * (same style as vehicles.service.spec.ts) rather than re-deriving the
 * guarantee some other way. Full-flow success paths (dispatching to a real
 * gateway, computing a commission/tax breakdown, crediting the owner
 * ledger) are deliberately out of scope here — they're exercised in the
 * integration suite against a real Postgres, not re-mocked piece by piece
 * in a unit test.
 */

function buildFakePrisma() {
  const paymentOrders: any[] = [];
  const gatewayEvents: any[] = [];
  let orderCounter = 0;

  const paymentOrderDelegate = {
    findUnique: async ({ where }: any) => {
      if (where.idempotency_key !== undefined) {
        return paymentOrders.find((o) => o.idempotency_key === where.idempotency_key) ?? null;
      }
      if (where.id !== undefined) {
        return paymentOrders.find((o) => o.id === where.id) ?? null;
      }
      return null;
    },
    findFirst: async ({ where }: any) =>
      paymentOrders.find(
        (o) =>
          (where.booking_id === undefined || o.booking_id === where.booking_id) &&
          (where.gateway_order_id === undefined || o.gateway_order_id === where.gateway_order_id) &&
          (where.status === undefined || where.status.in.includes(o.status)),
      ) ?? null,
    create: async ({ data }: any) => {
      const record = { id: `order-${++orderCounter}`, ...data };
      paymentOrders.push(record);
      return record;
    },
    update: async ({ where, data }: any) => {
      const record = paymentOrders.find((o) => o.id === where.id);
      Object.assign(record, data);
      return record;
    },
    updateMany: async ({ where, data }: any) => {
      const matching = paymentOrders.filter(
        (o) => o.id === where.id && (where.status === undefined || where.status.in.includes(o.status)),
      );
      matching.forEach((o) => Object.assign(o, data));
      return { count: matching.length };
    },
  };

  const gatewayEventDelegate = {
    create: async ({ data }: any) => {
      const duplicate = gatewayEvents.find((e) => e.gateway === data.gateway && e.gateway_event_id === data.gateway_event_id);
      if (duplicate) {
        const err: any = new Error('Unique constraint failed on the (gateway, gateway_event_id) fields');
        err.code = 'P2002';
        throw err;
      }
      const record = { id: `event-${gatewayEvents.length + 1}`, ...data };
      gatewayEvents.push(record);
      return record;
    },
  };

  return {
    prisma: {
      paymentOrder: paymentOrderDelegate,
      paymentGatewayEvent: gatewayEventDelegate,
    } as any,
    paymentOrders,
    gatewayEvents,
  };
}

function buildService(prisma: any, gatewayName = 'razorpay', verifySignature = true) {
  const configService = {} as any;
  const bookingService = {} as any; // not reached by any path under test — see file doc comment
  const ledgerService = {} as any; // not reached by any path under test
  const provider = {
    gatewayName,
    verifyWebhookSignature: () => verifySignature,
    createOrder: async () => ({ gatewayOrderId: 'unused-in-these-tests' }),
  } as any;
  return new PaymentsService(prisma, configService, bookingService, ledgerService, provider);
}

describe('PaymentsService.createOrder — idempotency-key replay', () => {
  it('returns the existing order instead of creating a second one for the same key', async () => {
    const { prisma, paymentOrders } = buildFakePrisma();
    const service = buildService(prisma);
    await prisma.paymentOrder.create({
      data: {
        booking_id: 'booking-1',
        driver_id: 'driver-1',
        gateway: 'razorpay',
        amount: 50000n,
        currency: 'INR',
        status: 'PROCESSING',
        idempotency_key: 'idem-key-1',
        gateway_order_id: 'gw-order-1',
        created_at: new Date(),
      },
    });

    const result = await service.createOrder('driver-1', { bookingId: 'booking-1', idempotencyKey: 'idem-key-1' } as any);

    expect(result.id).toBe(paymentOrders[0].id);
    expect(paymentOrders).toHaveLength(1); // no second row created for the replayed key
  });

  it('refuses a replayed idempotency key that belongs to a different driver', async () => {
    const { prisma } = buildFakePrisma();
    const service = buildService(prisma);
    await prisma.paymentOrder.create({
      data: {
        booking_id: 'booking-1',
        driver_id: 'driver-1',
        gateway: 'razorpay',
        amount: 50000n,
        currency: 'INR',
        status: 'PROCESSING',
        idempotency_key: 'idem-key-1',
        created_at: new Date(),
      },
    });

    await expect(
      service.createOrder('driver-2', { bookingId: 'booking-1', idempotencyKey: 'idem-key-1' } as any),
    ).rejects.toThrow(ForbiddenException);
  });
});

describe('PaymentsService.handleWebhook — gateway delivery idempotency (T8)', () => {
  const capturedPayload = (eventId: string, amount: number, orderId: string) => ({
    id: eventId,
    event: 'payment.captured',
    payload: { payment: { entity: { id: `pay-${eventId}`, order_id: orderId, status: 'captured', amount, currency: 'INR' } } },
  });

  it('treats a second delivery of the same gateway event id as a safe no-op', async () => {
    const { prisma } = buildFakePrisma();
    const service = buildService(prisma);
    const rawBody = Buffer.from(JSON.stringify(capturedPayload('evt-1', 50000, 'gw-order-unknown')));

    const first = await service.handleWebhook('razorpay', rawBody, 'sig');
    const second = await service.handleWebhook('razorpay', rawBody, 'sig');

    expect(second).toEqual({ ok: true, reason: 'duplicate' });
    // The first delivery still ran its own (non-duplicate) path — proves
    // the dedup is specifically about the *second* delivery, not a
    // trivial "always returns duplicate" stub.
    expect(first).not.toEqual({ ok: true, reason: 'duplicate' });
  });

  it('never marks an order SUCCESSFUL when the webhook amount does not match what was quoted', async () => {
    const { prisma, paymentOrders } = buildFakePrisma();
    const service = buildService(prisma);
    await prisma.paymentOrder.create({
      data: {
        booking_id: 'booking-1',
        driver_id: 'driver-1',
        gateway: 'razorpay',
        amount: 50000n,
        currency: 'INR',
        status: 'PROCESSING',
        idempotency_key: 'idem-key-2',
        gateway_order_id: 'gw-order-1',
        created_at: new Date(),
      },
    });
    // Tampered/forged amount — 999900 paise instead of the quoted 50000.
    const rawBody = Buffer.from(JSON.stringify(capturedPayload('evt-2', 999900, 'gw-order-1')));

    const result = await service.handleWebhook('razorpay', rawBody, 'sig');

    expect(result).toEqual({ ok: false, reason: 'amount_mismatch' });
    expect(paymentOrders[0].status).toBe('FAILED');
    expect(paymentOrders[0].status).not.toBe('SUCCESSFUL');
  });
});
