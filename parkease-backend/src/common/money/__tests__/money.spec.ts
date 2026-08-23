import { Money } from '../money';

describe('Money', () => {
  it('adds and subtracts within the same currency', () => {
    const a = Money.of(10000, 'INR'); // ₹100.00
    const b = Money.of(2550, 'INR'); // ₹25.50
    expect(a.add(b).toDisplayString()).toBe('INR 125.50');
    expect(a.subtract(b).toDisplayString()).toBe('INR 74.50');
  });

  it('throws on currency mismatch', () => {
    const inr = Money.of(100, 'INR');
    const usd = Money.of(100, 'USD');
    expect(() => inr.add(usd)).toThrow(/Currency mismatch/);
  });

  it('rejects non-integer minor units', () => {
    expect(() => Money.of(10.5 as unknown as number, 'INR')).toThrow();
  });

  describe('percentage (commission / GST boundary cases)', () => {
    it('computes 10% commission on ₹100 exactly', () => {
      const fee = Money.of(10000, 'INR'); // ₹100.00
      expect(fee.percentage(10).toMinorUnitsNumber()).toBe(1000); // ₹10.00
    });

    it('computes 18% GST with HALF_UP rounding on an odd amount', () => {
      // ₹33.33 * 18% = 599.94 paise -> rounds to 600 (HALF_UP)
      const amount = Money.of(3333, 'INR');
      expect(amount.percentage(18, 'HALF_UP').toMinorUnitsNumber()).toBe(600);
    });

    it('FLOOR rounding never overcharges at the boundary', () => {
      const amount = Money.of(3333, 'INR');
      expect(amount.percentage(18, 'FLOOR').toMinorUnitsNumber()).toBe(599);
    });

    it('handles exactly-half boundary with HALF_EVEN (banker\'s rounding)', () => {
      // Construct an amount where percentage produces an exact .5 paise remainder.
      // 100 minor units * 2.5% = 2.5 -> HALF_EVEN rounds to nearest even (2).
      const amount = Money.of(100, 'INR');
      expect(amount.percentage(2.5, 'HALF_EVEN').toMinorUnitsNumber()).toBe(2);
      // 300 * 2.5% = 7.5 -> nearest even is 8
      expect(Money.of(300, 'INR').percentage(2.5, 'HALF_EVEN').toMinorUnitsNumber()).toBe(8);
    });

    it('zero fee boundary case', () => {
      expect(Money.of(5000, 'INR').percentage(0).toMinorUnitsNumber()).toBe(0);
    });

    it('minimum fee (1 paise) boundary case', () => {
      const amount = Money.of(1, 'INR');
      expect(amount.percentage(50, 'HALF_UP').toMinorUnitsNumber()).toBe(1);
    });
  });

  describe('distribute (owner/commission/tax split integrity)', () => {
    it('splits ₹100 three ways summing back to exactly ₹100', () => {
      const total = Money.of(10000, 'INR');
      const shares = total.distribute(3);
      const sum = shares.reduce((acc, s) => acc.add(s), Money.zero('INR'));
      expect(sum.equals(total)).toBe(true);
      expect(shares.map((s) => s.toMinorUnitsNumber())).toEqual([3334, 3333, 3333]);
    });

    it('splits an amount with no remainder evenly', () => {
      const total = Money.of(9000, 'INR');
      const shares = total.distribute(3);
      expect(shares.every((s) => s.toMinorUnitsNumber() === 3000)).toBe(true);
    });
  });

  it('compareTo orders amounts correctly', () => {
    expect(Money.of(100).compareTo(Money.of(200))).toBe(-1);
    expect(Money.of(200).compareTo(Money.of(100))).toBe(1);
    expect(Money.of(100).compareTo(Money.of(100))).toBe(0);
  });

  it('isNegative / isZero report correctly', () => {
    expect(Money.of(-1).isNegative()).toBe(true);
    expect(Money.zero().isZero()).toBe(true);
  });
});
