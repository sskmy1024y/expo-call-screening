"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.withIosCallDirectory = void 0;
const config_plugins_1 = require("expo/config-plugins");
const path_1 = __importDefault(require("path"));
const pkg = require('../../package.json');
/** Entitlement holding the App Groups an app and its extensions share. */
const APP_GROUPS_ENTITLEMENT = 'com.apple.security.application-groups';
/** Info.plist keys read by `ExpoCallScreeningModule.swift`. */
const APP_GROUP_KEY = 'ExpoCallScreeningAppGroup';
const EXTENSION_BUNDLE_IDENTIFIER_KEY = 'ExpoCallScreeningExtensionBundleIdentifier';
/**
 * Directory holding `call-directory/expo-target.config.js`.
 *
 * Resolved from this file rather than from the project root so the module keeps
 * working wherever it is installed. The compiled plugin lives in `plugin/build/`
 * and its sources in `plugin/src/`, and both are two levels below the module
 * root, so the same relative path is correct either way.
 */
const TARGETS_DIR = path_1.default.resolve(__dirname, '../../targets');
// `@bacons/apple-targets/app.plugin` assigns the plugin to `module.exports`
// directly, so the required value is the plugin itself, not a namespace.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const withTargetsDir = require('@bacons/apple-targets/app.plugin');
/**
 * Wires up the iOS half of `expo-call-screening`:
 *
 * 1. shares an App Group between the app and the Call Directory Extension,
 * 2. records the App Group and the extension bundle identifier in the app's
 *    `Info.plist` so the native module can find them at runtime,
 * 3. hands the extension target itself to `@bacons/apple-targets`.
 *
 * The order matters. `@bacons/apple-targets` evaluates
 * `targets/call-directory/expo-target.config.js` synchronously while resolving
 * plugins, and that file reads both the entitlement and the `Info.plist` key
 * written in the steps above.
 */
const withIosCallDirectoryPlugin = (config, props) => {
    const bundleIdentifier = config.ios?.bundleIdentifier;
    if (!bundleIdentifier) {
        throw new Error('[expo-call-screening] `ios.bundleIdentifier` is missing from the Expo config. The App Group and ' +
            'the Call Directory Extension identifier are both derived from it, so set it in app.json ' +
            'before adding this plugin.');
    }
    const defaultAppGroup = `group.${bundleIdentifier}`;
    const appGroup = props?.appGroup ?? defaultAppGroup;
    const extensionBundleIdentifier = props?.extensionBundleIdentifier ?? `${bundleIdentifier}.CallDirectory`;
    if (appGroup !== defaultAppGroup) {
        // The extension has no way to read the host app's Info.plist, so it rebuilds
        // the App Group name from its own bundle identifier. See the comment on
        // `sharedAppGroupIdentifier()` in `CallDirectoryHandler.swift`.
        console.warn(`[expo-call-screening] The custom App Group "${appGroup}" does not match the convention ` +
            `"${defaultAppGroup}" that the Call Directory Extension derives at runtime. The extension ` +
            'will read an empty store until `CallDirectoryHandler.swift` is taught about it.');
    }
    config.ios = config.ios ?? {};
    // `entitlements` is loosely typed, so a hand-written string is guarded against here.
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
    return withTargetsDir(config, {
        // `@bacons/apple-targets` interpolates this into a glob run from the project
        // root, so it has to be a relative path with forward slashes.
        root: path_1.default.relative(projectRoot, TARGETS_DIR).split(path_1.default.sep).join('/'),
        appleTeamId: config.ios.appleTeamId,
    });
};
/**
 * Adds the CallKit Call Directory Extension and the App Group it shares with the app.
 * Running it twice is a no-op, which keeps a duplicated plugin entry from
 * registering the extension target twice.
 */
exports.withIosCallDirectory = (0, config_plugins_1.createRunOncePlugin)(withIosCallDirectoryPlugin, 'expo-call-screening:ios-call-directory', pkg.version);
