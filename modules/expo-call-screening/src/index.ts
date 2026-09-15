import ExpoCallScreeningModule from './ExpoCallScreeningModule';
import type { CallerIdentity, CallerIdStatus } from './ExpoCallScreening.types';

export * from './ExpoCallScreening.types';

/**
 * Replaces the stored caller identities with `entries`.
 *
 * The list is stored where the platform's call handling component can read it,
 * it is not handed to the OS directly:
 *
 * - iOS writes the entries to the App Group `UserDefaults` shared with the Call
 *   Directory Extension. The extension reads them the next time the system asks
 *   it to build its database, so follow this call with {@link reload}.
 * - Android writes the entries to the app's `SharedPreferences`. The call
 *   screening service reads them when a call comes in, so no reload is needed.
 *
 * Entries replace the previous list rather than adding to it.
 */
export async function setCallerIdentities(entries: CallerIdentity[]): Promise<void> {
  return ExpoCallScreeningModule.setCallerIdentities(entries);
}

/**
 * Asks the OS to rebuild its caller identity database from the stored entries.
 *
 * - iOS calls `CXCallDirectoryManager.reloadExtension`, which rejects if the
 *   extension is disabled in Settings or if its data fails validation.
 * - Android is a no-op, because the screening service reads the stored entries
 *   on every call.
 */
export async function reload(): Promise<void> {
  return ExpoCallScreeningModule.reload();
}

/**
 * Reports whether the OS is currently using the registered identities.
 *
 * - iOS returns the enabled state of the Call Directory Extension from
 *   `CXCallDirectoryManager.getEnabledStatusForExtension`.
 * - Android returns `enabled` while the app holds `ROLE_CALL_SCREENING`.
 *
 * Returns `unknown` where the platform cannot answer, such as on the iOS
 * simulator.
 */
export async function getStatus(): Promise<CallerIdStatus> {
  return ExpoCallScreeningModule.getStatus();
}

/**
 * Sends the user to the place where they grant this app the right to identify
 * calls.
 *
 * - iOS opens Settings, because a Call Directory Extension can only be enabled
 *   by hand under Phone > Call Blocking & Identification.
 * - Android requests `ROLE_CALL_SCREENING` through `RoleManager`, which shows
 *   the system dialog.
 *
 * Resolving means the request was shown, not that permission was granted. Call
 * {@link getStatus} afterwards to read the result.
 */
export async function requestPermission(): Promise<void> {
  return ExpoCallScreeningModule.requestPermission();
}

/**
 * Reports whether the app may draw the caller identity over other apps.
 *
 * - Android reads `Settings.canDrawOverlays`. The overlay band shown on an
 *   incoming call needs `SYSTEM_ALERT_WINDOW`, which the user grants by hand in
 *   system settings rather than through a runtime dialog.
 * - iOS always returns `true`. There is no overlay on iOS: the label is drawn
 *   by the system from the Call Directory Extension, so nothing has to be
 *   granted.
 */
export async function hasOverlayPermission(): Promise<boolean> {
  return ExpoCallScreeningModule.hasOverlayPermission();
}

/**
 * Sends the user to the place where they allow this app to draw over other
 * apps.
 *
 * - Android opens the system "Display over other apps" settings page for this
 *   app and resolves right away.
 * - iOS is a no-op, because the incoming call screen needs no overlay.
 *
 * Resolving means the settings page was opened, not that permission was
 * granted. Call {@link hasOverlayPermission} again once the app returns to the
 * foreground to read the result.
 */
export async function requestOverlayPermission(): Promise<void> {
  return ExpoCallScreeningModule.requestOverlayPermission();
}
