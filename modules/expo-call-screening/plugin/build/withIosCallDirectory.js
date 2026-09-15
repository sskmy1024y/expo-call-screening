"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.withIosCallDirectory = void 0;
const config_plugins_1 = require("expo/config-plugins");
const plist_1 = __importDefault(require("@expo/plist"));
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const pkg = require('../../package.json');
const APP_GROUPS_ENTITLEMENT = 'com.apple.security.application-groups';
const APP_GROUP_KEY = 'ExpoCallScreeningAppGroup';
const EXTENSION_BUNDLE_IDENTIFIER_KEY = 'ExpoCallScreeningExtensionBundleIdentifier';
// Resolve from the package, not the consuming app; src/ and build/ have the same depth.
const TARGETS_DIR = path_1.default.resolve(__dirname, '../../targets');
// apple-targets exports the plugin directly via module.exports.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const withTargetsDir = require('@bacons/apple-targets/app.plugin');
// apple-targets evaluates its target config synchronously, after the host settings below are set.
const withIosCallDirectoryPlugin = (config, props) => {
    if (config.platforms && !config.platforms.includes('ios')) {
        return config;
    }
    if (!config.ios && !props) {
        return config;
    }
    const bundleIdentifier = config.ios?.bundleIdentifier;
    if (!bundleIdentifier) {
        throw new Error('[expo-call-screening] `ios.bundleIdentifier` is missing from the Expo config. The App Group and ' +
            'the Call Directory Extension identifier are both derived from it, so set it in app.json ' +
            'before adding this plugin.');
    }
    const defaultAppGroup = `group.${bundleIdentifier}`;
    const appGroup = props?.appGroup ?? defaultAppGroup;
    const extensionBundleIdentifier = props?.extensionBundleIdentifier ?? `${bundleIdentifier}.CallDirectory`;
    config.ios = config.ios ?? {};
    const configuredAppGroups = config.ios.entitlements?.[APP_GROUPS_ENTITLEMENT];
    const existingAppGroups = Array.isArray(configuredAppGroups) ? configuredAppGroups : [];
    config.ios.entitlements = {
        ...config.ios.entitlements,
        [APP_GROUPS_ENTITLEMENT]: existingAppGroups.includes(appGroup)
            ? existingAppGroups
            : [...existingAppGroups, appGroup],
    };
    config.ios.infoPlist = {
        ...config.ios.infoPlist,
        [APP_GROUP_KEY]: appGroup,
        [EXTENSION_BUNDLE_IDENTIFIER_KEY]: extensionBundleIdentifier,
    };
    const projectRoot = config._internal?.projectRoot ?? process.cwd();
    // Mods run in reverse registration order. Register before apple-targets so
    // its generated Info.plist exists before we update it, including on --clean.
    config = (0, config_plugins_1.withDangerousMod)(config, ['ios', async (config) => {
            const plistPath = path_1.default.join(TARGETS_DIR, 'call-directory/Info.plist');
            const info = plist_1.default.parse(await fs_1.default.promises.readFile(plistPath, 'utf8'));
            await fs_1.default.promises.writeFile(plistPath, plist_1.default.build({ ...info, [APP_GROUP_KEY]: appGroup }));
            return config;
        }]);
    return withTargetsDir(config, {
        // apple-targets requires a project-relative glob path with forward slashes.
        root: path_1.default.relative(projectRoot, TARGETS_DIR).split(path_1.default.sep).join('/'),
        appleTeamId: config.ios?.appleTeamId,
    });
};
exports.withIosCallDirectory = (0, config_plugins_1.createRunOncePlugin)(withIosCallDirectoryPlugin, 'expo-call-screening:ios-call-directory', pkg.version);
