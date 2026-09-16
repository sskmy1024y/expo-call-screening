import { assertValidCallerIdentities } from './callerIdentities';
import ExpoCallScreeningModule from './ExpoCallScreeningModule';
import type { CallerIdentity, CallerIdStatus } from './ExpoCallScreening.types';

export * from './ExpoCallScreening.types';

/**
 * Replaces the stored list. On iOS, call {@link reload} once the extension is enabled.
 * Android reads the list on each incoming call.
 *
 * @throws if any `phoneNumber` is not in E.164 form (`+819012345678`). Nothing is stored when it
 * throws, so a rejected call leaves the previous list in place.
 */
export async function setCallerIdentities(entries: CallerIdentity[]): Promise<void> {
  assertValidCallerIdentities(entries);
  return ExpoCallScreeningModule.setCallerIdentities(entries);
}

/**
 * iOS: rebuilds the directory; rejects if disabled or entries fail validation.
 * Android: no-op.
 */
export async function reload(): Promise<void> {
  return ExpoCallScreeningModule.reload();
}

/**
 * Reports whether the iOS extension is enabled or Android holds the screening role.
 * Returns `unknown` when the platform cannot determine the status.
 */
export async function getStatus(): Promise<CallerIdStatus> {
  return ExpoCallScreeningModule.getStatus();
}

/**
 * iOS: opens Settings for manual enablement. Android: requests phone-state permission and the screening role.
 * Resolution does not imply permission was granted. Recheck {@link getStatus} on return.
 */
export async function requestPermission(): Promise<void> {
  return ExpoCallScreeningModule.requestPermission();
}

/**
 * Android: checks the display-over-other-apps permission. iOS: always true.
 */
export async function hasOverlayPermission(): Promise<boolean> {
  return ExpoCallScreeningModule.hasOverlayPermission();
}

/**
 * Android: opens overlay settings and resolves immediately. iOS: no-op.
 * Recheck {@link hasOverlayPermission} when the app returns to the foreground.
 */
export async function requestOverlayPermission(): Promise<void> {
  return ExpoCallScreeningModule.requestOverlayPermission();
}
