"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.withAndroidCallScreening = void 0;
const config_plugins_1 = require("expo/config-plugins");
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
/** Fully qualified name of the `CallScreeningService` shipped by this module. */
const SERVICE_NAME = 'expo.modules.callscreening.ExpoCallScreeningService';
/** Permission the telecom stack requires a call screening service to be protected by. */
const SERVICE_PERMISSION = 'android.permission.BIND_SCREENING_SERVICE';
/** Action the telecom stack uses to discover and bind a call screening service. */
const SERVICE_ACTION = 'android.telecom.CallScreeningService';
/** Permission `CallerIdOverlay` needs to draw the caller ID band over the incoming call screen. */
const OVERLAY_PERMISSION = 'android.permission.SYSTEM_ALERT_WINDOW';
/** Permission `CallStateWatcher` needs to notice that the call has ended. */
const CALL_STATE_PERMISSION = 'android.permission.READ_PHONE_STATE';
/** Where a custom overlay layout is written, relative to `android/`. */
const OVERLAY_LAYOUT_TARGET = 'app/src/main/res/layout/expo_call_screening_overlay.xml';
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
const withAndroidCallScreening = (config, props) => {
    const configWithManifest = (0, config_plugins_1.withAndroidManifest)(config, (config) => {
        const application = config_plugins_1.AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
        application.service = withCallScreeningService(application.service ?? []);
        // `<uses-permission>` is a child of `<manifest>` rather than of `<application>`, so this takes
        // the manifest root. `ensurePermission` no-ops when the permission is already requested, which
        // `addPermission` does not.
        config_plugins_1.AndroidConfig.Permissions.ensurePermission(config.modResults, OVERLAY_PERMISSION);
        config_plugins_1.AndroidConfig.Permissions.ensurePermission(config.modResults, CALL_STATE_PERMISSION);
        return config;
    });
    return props?.overlayLayout
        ? withOverlayLayout(configWithManifest, props.overlayLayout)
        : configWithManifest;
};
exports.withAndroidCallScreening = withAndroidCallScreening;
/** Returns [services] with the call screening service appended, unless it is already declared. */
function withCallScreeningService(services) {
    const isDeclared = services.some((service) => service.$['android:name'] === SERVICE_NAME);
    return isDeclared ? services : [...services, createCallScreeningService()];
}
/** Builds the `<service>` entry the telecom stack expects for a call screener. */
function createCallScreeningService() {
    return {
        $: {
            'android:name': SERVICE_NAME,
            'android:permission': SERVICE_PERMISSION,
            'android:exported': 'true',
        },
        'intent-filter': [{ action: [{ $: { 'android:name': SERVICE_ACTION } }] }],
    };
}
/**
 * Copies the layout at `overlayLayout`, a path relative to the project root, into the app's
 * resources as `res/layout/expo_call_screening_overlay.xml`.
 *
 * An app resource shadows the library resource of the same name, so this replaces the band that
 * `expo-call-screening` ships without the app having to fork the module. The XML has to keep the two ids
 * `expo_call_screening_label` and `expo_call_screening_phone_number` on text views, because `CallerIdOverlay`
 * writes the caller name and number into exactly those and shows nothing when either is missing. It
 * also has to inflate without AppCompat or Material, since the band is added straight to the
 * `WindowManager` rather than to an activity.
 *
 * This runs as a dangerous mod because there is no Expo mod for arbitrary Android resource files.
 * The copy therefore has to happen after prebuild has laid down `android/`, and it is redone on
 * every prebuild rather than tracked.
 */
const withOverlayLayout = (config, overlayLayout) => (0, config_plugins_1.withDangerousMod)(config, [
    'android',
    async (config) => {
        const source = path_1.default.resolve(config.modRequest.projectRoot, overlayLayout);
        if (!fs_1.default.existsSync(source)) {
            throw new Error(`[expo-call-screening] The overlay layout "${overlayLayout}" does not exist. It is resolved ` +
                `relative to the project root, which puts it at "${source}".`);
        }
        const destination = path_1.default.join(config.modRequest.platformProjectRoot, OVERLAY_LAYOUT_TARGET);
        await fs_1.default.promises.mkdir(path_1.default.dirname(destination), { recursive: true });
        await fs_1.default.promises.copyFile(source, destination);
        return config;
    },
]);
