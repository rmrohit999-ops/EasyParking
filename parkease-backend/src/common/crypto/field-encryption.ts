import { createCipheriv, createDecipheriv, randomBytes } from 'crypto';

/**
 * Application-level field encryption for the two OwnerPayoutAccount
 * columns the Milestone 0 ERD names `*_encrypted`
 * (`bank_account_number_encrypted`, `upi_vpa_encrypted`, Milestone 9).
 * AES-256-GCM: a real, standard authenticated cipher, not a placeholder —
 * `PAYOUT_ENCRYPTION_KEY` (32 raw bytes, base64) never leaves env config,
 * and every ciphertext carries its own random 12-byte IV and 16-byte GCM
 * auth tag so two encryptions of the same account number never look alike
 * and tampering is detected at decrypt time rather than silently accepted.
 *
 * Stored format is `${ivBase64}.${authTagBase64}.${ciphertextBase64}` — a
 * single self-describing string that fits the existing `String?` columns
 * with no schema change needed.
 */
const ALGORITHM = 'aes-256-gcm';

function loadKey(base64Key: string): Buffer {
  const key = Buffer.from(base64Key, 'base64');
  if (key.length !== 32) {
    throw new Error('PAYOUT_ENCRYPTION_KEY must decode (base64) to exactly 32 bytes for AES-256-GCM.');
  }
  return key;
}

export function encryptField(plaintext: string, base64Key: string): string {
  const key = loadKey(base64Key);
  const iv = randomBytes(12);
  const cipher = createCipheriv(ALGORITHM, key, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return `${iv.toString('base64')}.${authTag.toString('base64')}.${ciphertext.toString('base64')}`;
}

export function decryptField(stored: string, base64Key: string): string {
  const key = loadKey(base64Key);
  const [ivB64, tagB64, ciphertextB64] = stored.split('.');
  if (!ivB64 || !tagB64 || !ciphertextB64) {
    throw new Error('Malformed encrypted field value.');
  }
  const decipher = createDecipheriv(ALGORITHM, key, Buffer.from(ivB64, 'base64'));
  decipher.setAuthTag(Buffer.from(tagB64, 'base64'));
  const plaintext = Buffer.concat([decipher.update(Buffer.from(ciphertextB64, 'base64')), decipher.final()]);
  return plaintext.toString('utf8');
}

/** Last-4-digits-style display helper — never logs or returns the full value. */
export function maskAccountNumber(value: string): string {
  if (value.length <= 4) return '*'.repeat(value.length);
  return `${'*'.repeat(value.length - 4)}${value.slice(-4)}`;
}
