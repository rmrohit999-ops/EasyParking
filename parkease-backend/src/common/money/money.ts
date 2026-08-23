/**
 * Money is always represented as an integer count of minor units (paise for
 * INR) plus an ISO 4217 currency code. Never a float. This is what the
 * Milestone 0 spec means by "never use floating-point numbers for money" —
 * every arithmetic op below stays in integer space, and the only place a
 * fraction ever appears is inside deterministic percentage rounding, which
 * is resolved back to an integer before it's stored anywhere.
 */
export type RoundingMode = 'HALF_UP' | 'HALF_EVEN' | 'FLOOR' | 'CEIL';

export class Money {
  private constructor(
    public readonly minorUnits: bigint,
    public readonly currency: string,
  ) {
    if (!Number.isInteger(Number(minorUnits))) {
      throw new Error('Money.minorUnits must be an integer number of minor units');
    }
  }

  static of(minorUnits: number | bigint, currency = 'INR'): Money {
    return new Money(BigInt(minorUnits), currency.toUpperCase());
  }

  static zero(currency = 'INR'): Money {
    return Money.of(0, currency);
  }

  private assertSameCurrency(other: Money) {
    if (other.currency !== this.currency) {
      throw new Error(`Currency mismatch: ${this.currency} vs ${other.currency}`);
    }
  }

  add(other: Money): Money {
    this.assertSameCurrency(other);
    return new Money(this.minorUnits + other.minorUnits, this.currency);
  }

  subtract(other: Money): Money {
    this.assertSameCurrency(other);
    return new Money(this.minorUnits - other.minorUnits, this.currency);
  }

  isNegative(): boolean {
    return this.minorUnits < 0n;
  }

  isZero(): boolean {
    return this.minorUnits === 0n;
  }

  equals(other: Money): boolean {
    return this.currency === other.currency && this.minorUnits === other.minorUnits;
  }

  compareTo(other: Money): number {
    this.assertSameCurrency(other);
    if (this.minorUnits === other.minorUnits) return 0;
    return this.minorUnits > other.minorUnits ? 1 : -1;
  }

  /**
   * Applies a percentage (e.g. 18 for 18% GST, or 10 for 10% commission)
   * using deterministic rounding on the integer minor-unit amount. Never
   * touches a floating-point representation of the money itself — only the
   * percentage multiplication happens in higher precision internally, and
   * the result is rounded back to an integer before being returned.
   */
  percentage(percent: number, mode: RoundingMode = 'HALF_UP'): Money {
    if (percent < 0) throw new Error('percent must be >= 0');
    // Work in hundredths-of-a-percent integer space to stay deterministic:
    // amount * percent*100 / 10000, rounded per `mode`.
    const percentScaled = BigInt(Math.round(percent * 100)); // e.g. 18% -> 1800
    const numerator = this.minorUnits * percentScaled;
    const denominator = 10000n;
    return new Money(roundDivide(numerator, denominator, mode), this.currency);
  }

  /**
   * Splits this amount into `parts` shares that sum back exactly to the
   * original (the classic "distribute the leftover paise" problem — e.g.
   * splitting ₹100 three ways must total exactly ₹100, not ₹99.99 or
   * ₹100.02). Extra minor units go to the first shares in order.
   */
  distribute(parts: number): Money[] {
    if (parts <= 0) throw new Error('parts must be > 0');
    const base = this.minorUnits / BigInt(parts);
    const remainder = this.minorUnits % BigInt(parts);
    const shares: Money[] = [];
    for (let i = 0; i < parts; i++) {
      const extra = BigInt(i) < remainder ? 1n : 0n;
      shares.push(new Money(base + extra, this.currency));
    }
    return shares;
  }

  toMinorUnitsNumber(): number {
    return Number(this.minorUnits);
  }

  /** Human-readable major-unit string for display only — never for storage or further arithmetic. */
  toDisplayString(): string {
    const negative = this.minorUnits < 0n;
    const abs = negative ? -this.minorUnits : this.minorUnits;
    const major = abs / 100n;
    const minor = abs % 100n;
    return `${negative ? '-' : ''}${this.currency} ${major}.${minor.toString().padStart(2, '0')}`;
  }

  toJSON() {
    return { minorUnits: this.minorUnits.toString(), currency: this.currency };
  }
}

function roundDivide(numerator: bigint, denominator: bigint, mode: RoundingMode): bigint {
  const negative = (numerator < 0n) !== (denominator < 0n);
  const n = numerator < 0n ? -numerator : numerator;
  const d = denominator < 0n ? -denominator : denominator;
  const quotient = n / d;
  const remainder = n % d;

  let rounded: bigint;
  switch (mode) {
    case 'FLOOR':
      rounded = quotient;
      break;
    case 'CEIL':
      rounded = remainder > 0n ? quotient + 1n : quotient;
      break;
    case 'HALF_EVEN': {
      const twiceRemainder = remainder * 2n;
      if (twiceRemainder < d) rounded = quotient;
      else if (twiceRemainder > d) rounded = quotient + 1n;
      else rounded = quotient % 2n === 0n ? quotient : quotient + 1n;
      break;
    }
    case 'HALF_UP':
    default: {
      const twiceRemainder = remainder * 2n;
      rounded = twiceRemainder >= d ? quotient + 1n : quotient;
      break;
    }
  }
  return negative ? -rounded : rounded;
}
