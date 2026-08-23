import { createHash, createHmac, timingSafeEqual } from 'crypto';

/**
 * The QR pass token is `${qrPassId}.${hmacHex}` — it encodes a signed
 * reference to the qr_passes row, never raw booking data (Milestone 0
 * §5.11). It is deliberately never persisted anywhere: because the HMAC is
 * over `${qrPassId}:${issuedAtEpochMs}` and both of those live on the
 * QrPass row already, the exact same token can always be recomputed on
 * demand from the row alone — so re-requesting a pass (GET
 * /bookings/:id/pass) is naturally idempotent without a raw-token column,
 * and there's nothing sensitive sitting in the database for a read replica
 * or backup leak to expose beyond a hash of it (`payload_hash`).
 */
export function signQrToken(qrPassId: string, issuedAtMs: number, secret: string): string {
  const hmac = createHmac('sha256', secret).update(`${qrPassId}:${issuedAtMs}`).digest('hex');
  return `${qrPassId}.${hmac}`;
}

export function hashQrToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

export function parseQrToken(token: string): { qrPassId: string; hmac: string } | null {
  const idx = token.indexOf('.');
  if (idx <= 0 || idx === token.length - 1) return null;
  return { qrPassId: token.slice(0, idx), hmac: token.slice(idx + 1) };
}

/** Constant-time signature check — see razorpay-provider.service.ts's identical rationale (Milestone 0 §13, T7/T9). */
export function verifyQrTokenSignature(token: string, qrPassId: string, issuedAtMs: number, secret: string): boolean {
  const expected = signQrToken(qrPassId, issuedAtMs, secret);
  const expectedBuf = Buffer.from(expected, 'utf8');
  const actualBuf = Buffer.from(token, 'utf8');
  if (expectedBuf.length !== actualBuf.length) return false;
  return timingSafeEqual(expectedBuf, actualBuf);
}
