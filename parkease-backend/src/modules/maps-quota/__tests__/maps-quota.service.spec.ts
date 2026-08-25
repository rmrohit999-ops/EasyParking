import { MapsQuotaService } from '../maps-quota.service';

/**
 * In-memory fake standing in for ioredis — enough of the real command
 * surface (GET/SET with EX+NX/INCR/EXPIRE, plus `status`/`connect`/`on`/
 * `quit` for the service's own lifecycle calls) to drive the service's
 * actual logic through real Redis semantics, rather than mocking the
 * service's own methods and testing nothing.
 */
class FakeRedis {
  status = 'ready';
  private store = new Map<string, string>();

  async connect() {
    this.status = 'ready';
  }
  on() {
    /* no-op */
  }
  async quit() {
    /* no-op */
  }

  async get(key: string): Promise<string | null> {
    return this.store.has(key) ? this.store.get(key)! : null;
  }

  async incr(key: string): Promise<number> {
    const next = (parseInt(this.store.get(key) ?? '0', 10) || 0) + 1;
    this.store.set(key, String(next));
    return next;
  }

  async expire(): Promise<number> {
    return 1; // TTL bookkeeping isn't meaningful within a single test run
  }

  // Mirrors ioredis's overloaded SET signature closely enough for this
  // service's two call shapes: plain SET ... EX ..., and SET ... EX ... NX.
  async set(key: string, value: string, ...args: unknown[]): Promise<'OK' | null> {
    const hasNx = args.includes('NX');
    if (hasNx && this.store.has(key)) return null;
    this.store.set(key, value);
    return 'OK';
  }
}

jest.mock('ioredis', () => ({
  __esModule: true,
  default: jest.fn().mockImplementation(() => new FakeRedis()),
}));

function buildService(cap = 5) {
  const sentNotifications: Array<{ userId: string; title: string }> = [];
  const configService = {
    get: (key: string) => {
      if (key === 'redis') return { url: 'redis://fake' };
      if (key === 'maps') return { quotaDailySafetyCap: cap };
      throw new Error(`unexpected config key in test: ${key}`);
    },
  } as any;
  const prisma = {
    auditLog: { create: async () => undefined },
    userRoleAssignment: {
      findMany: async () => [{ user_id: 'super-admin-1' }],
    },
  } as any;
  const notificationsService = {
    send: async (params: { userId: string; title: string }) => {
      sentNotifications.push({ userId: params.userId, title: params.title });
    },
  } as any;

  const service = new MapsQuotaService(configService, prisma, notificationsService);
  service.onModuleInit();
  return { service, sentNotifications };
}

describe('MapsQuotaService', () => {
  it('allows requests under the daily safety cap and increments the counter', async () => {
    const { service } = buildService(5);
    const first = await service.checkAndIncrement('directions');
    expect(first).toEqual({ allowed: true, sku: 'directions', count: 1, cap: 5, percentUsed: 20 });
    const second = await service.checkAndIncrement('directions');
    expect(second.count).toBe(2);
  });

  it('blocks once the cap is reached, without incrementing further', async () => {
    const { service } = buildService(2);
    await service.checkAndIncrement('places');
    const atCap = await service.checkAndIncrement('places');
    expect(atCap.allowed).toBe(true); // the call that reaches the cap itself is still allowed
    expect(atCap.count).toBe(2);

    const blocked = await service.checkAndIncrement('places');
    expect(blocked.allowed).toBe(false);
    expect(blocked.count).toBe(2); // never incremented past the cap
  });

  it('tracks each SKU independently', async () => {
    const { service } = buildService(1);
    const directions = await service.checkAndIncrement('directions');
    const geocoding = await service.checkAndIncrement('geocoding');
    expect(directions.count).toBe(1);
    expect(geocoding.count).toBe(1); // not shared with directions' counter
  });

  it('notifies every active SUPER_ADMIN exactly once when the cap is first reached', async () => {
    const { service, sentNotifications } = buildService(1);
    await service.checkAndIncrement('geocoding'); // reaches the cap on the first call
    await service.checkAndIncrement('geocoding'); // blocked — must not re-alert
    await service.checkAndIncrement('geocoding'); // blocked again — must not re-alert

    expect(sentNotifications).toHaveLength(1);
    expect(sentNotifications[0].userId).toBe('super-admin-1');
  });

  it('a global trip blocks every SKU immediately, regardless of its own counter', async () => {
    const { service } = buildService(1000);
    await service.tripGlobalBreaker('budget alert test');

    const result = await service.checkAndIncrement('directions');
    expect(result.allowed).toBe(false);
    expect(result.percentUsed).toBe(100);
  });

  it('reports a full usage snapshot for the admin dashboard', async () => {
    const { service } = buildService(4);
    await service.checkAndIncrement('directions');
    await service.checkAndIncrement('directions');
    await service.checkAndIncrement('places');

    const snapshot = await service.getUsageSnapshot();
    expect(snapshot.globallyTripped).toBe(false);
    const directions = snapshot.skus.find((s) => s.sku === 'directions')!;
    const places = snapshot.skus.find((s) => s.sku === 'places')!;
    const geocoding = snapshot.skus.find((s) => s.sku === 'geocoding')!;
    expect(directions).toMatchObject({ count: 2, cap: 4, percentUsed: 50, capReached: false });
    expect(places).toMatchObject({ count: 1, cap: 4 });
    expect(geocoding).toMatchObject({ count: 0, cap: 4, capReached: false });
  });

  it('fails open (allows the request) if Redis is unavailable', async () => {
    const { service } = buildService(5);
    // Force every Redis call this service makes to throw.
    (service as any).client = {
      status: 'ready',
      get: async () => {
        throw new Error('connection refused');
      },
      incr: async () => {
        throw new Error('connection refused');
      },
      connect: async () => undefined,
    };

    const result = await service.checkAndIncrement('directions');
    expect(result.allowed).toBe(true);
  });
});
