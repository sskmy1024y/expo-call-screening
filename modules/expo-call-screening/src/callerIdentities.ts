import type { CallerIdentity } from './ExpoCallScreening.types';

/**
 * Spaces, dashes, dots and brackets are cosmetic: iOS keeps only the digits and Android's
 * comparison parses them away, so a number is checked with them removed.
 */
const SEPARATORS = /[\s\-().]/g;

/**
 * E.164: a `+`, a country code that never starts with `0`, then 7 to 15 digits in total.
 *
 * This is a shape check. It does not confirm that the country code exists or that the number is
 * in service; both platforms find that out for themselves at call time.
 */
const E164 = /^\+[1-9]\d{6,14}$/;

/**
 * Rejects entries the platforms cannot match, rather than letting them fail silently later.
 *
 * iOS leaves no room to be lenient: the Call Directory extension hands CallKit an `Int64`, so a
 * domestic `09012345678` is stored as `9012345678` — a different number, registered without
 * complaint, that simply never matches.
 */
export function assertValidCallerIdentities(entries: CallerIdentity[]): void {
  const invalid = entries
    .map((entry, index) => ({ entry, index }))
    .filter(({ entry }) => !E164.test(entry.phoneNumber.replace(SEPARATORS, '')));

  if (invalid.length === 0) {
    return;
  }

  // The numbers themselves are left out: this message can end up in a crash reporter.
  throw new Error(
    `[expo-call-screening] Every phoneNumber must be in E.164 form, like +819012345678. ` +
      `${invalid.length} of ${entries.length} entries are not, at index ` +
      `${invalid.map(({ index }) => index).join(', ')}. ` +
      `The numbers are omitted here to keep them out of logs.`
  );
}
