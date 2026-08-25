/** Mirrors the backend/mobile Money type's display formatting — minor units (paise) as a BigInt-safe string in, a locale-formatted string out. Never floating-point math on the raw value. */
export function formatMinorUnits(minorUnitsString: string, currency = "INR"): string {
  const minorUnits = BigInt(minorUnitsString);
  const negative = minorUnits < 0n;
  const abs = negative ? -minorUnits : minorUnits;
  const major = abs / 100n;
  const minor = abs % 100n;
  const sign = negative ? "-" : "";
  const symbol = currency === "INR" ? "₹" : `${currency} `;
  return `${sign}${symbol}${major.toLocaleString("en-IN")}.${minor.toString().padStart(2, "0")}`;
}
