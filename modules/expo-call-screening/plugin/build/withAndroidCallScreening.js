"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.withAndroidCallScreening = void 0;
const config_plugins_1 = require("expo/config-plugins");
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const SERVICE_NAME = 'expo.modules.callscreening.ExpoCallScreeningService';
const SERVICE_PERMISSION = 'android.permission.BIND_SCREENING_SERVICE';
const SERVICE_ACTION = 'android.telecom.CallScreeningService';
const OVERLAY_PERMISSION = 'android.permission.SYSTEM_ALERT_WINDOW';
const CALL_STATE_PERMISSION = 'android.permission.READ_PHONE_STATE';
const OVERLAY_LAYOUT_TARGET = 'app/src/main/res/layout/expo_call_screening_overlay.xml';
// Register via the plugin so installing the module alone does not add services or permissions.
const withAndroidCallScreening = (config, props) => {
    const configWithManifest = (0, config_plugins_1.withAndroidManifest)(config, (config) => {
        const application = config_plugins_1.AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
        application.service = withCallScreeningService(application.service ?? []);
        // ensurePermission avoids duplicates when prebuild runs again.
        config_plugins_1.AndroidConfig.Permissions.ensurePermission(config.modResults, OVERLAY_PERMISSION);
        config_plugins_1.AndroidConfig.Permissions.ensurePermission(config.modResults, CALL_STATE_PERMISSION);
        return config;
    });
    return props?.overlayLayout
        ? withOverlayLayout(configWithManifest, props.overlayLayout)
        : configWithManifest;
};
exports.withAndroidCallScreening = withAndroidCallScreening;
function withCallScreeningService(services) {
    const isDeclared = services.some((service) => service.$['android:name'] === SERVICE_NAME);
    return isDeclared ? services : [...services, createCallScreeningService()];
}
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
// App resources override library resources. Custom layouts must retain both TextView IDs.
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
