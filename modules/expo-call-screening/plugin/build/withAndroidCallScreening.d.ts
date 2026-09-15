import { ConfigPlugin } from 'expo/config-plugins';
import type { ExpoCallScreeningPluginProps } from './types';
/**
 * Writes the Android half of `expo-call-screening` into the app project:
 *
 * 1. registers {@link SERVICE_NAME} in `AndroidManifest.xml`,
 * 2. requests {@link OVERLAY_PERMISSION}, which the caller ID band needs to be drawn at all, and
 *    {@link CALL_STATE_PERMISSION}, which lets the band close itself when the call ends instead of
 *    waiting out a timeout,
 * 3. copies `overlayLayout` over the band's default layout, when one is given.
 *
 * The service and the permissions are declared here rather than in the module's own
 * `android/src/main/AndroidManifest.xml` so that the whole native surface of this feature is visible
 * in one place and can be turned off by dropping the plugin. A library manifest would be the
 * alternative and would merge automatically, at the cost of every consumer getting a call screener
 * and both permissions whether they want them or not. These two are exactly the kind that should be
 * opted into: `SYSTEM_ALERT_WINDOW` shows up in the Play Console listing and in the app's settings
 * screen, and `READ_PHONE_STATE` is a runtime permission the user is asked about by name.
 *
 * Running the plugin twice is safe: neither the service entry nor either permission is added a
 * second time, and the layout copy overwrites its own previous output.
 */
export declare const withAndroidCallScreening: ConfigPlugin<ExpoCallScreeningPluginProps['android']>;
