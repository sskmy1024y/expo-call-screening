import { createRunOncePlugin, withDangerousMod, type ConfigPlugin } from 'expo/config-plugins';
import plist, { type PlistObject } from '@expo/plist';
import fs from 'fs';
import path from 'path';

import type { ExpoCallScreeningPluginProps } from './types';

const pkg = require('../../package.json') as { version: string };

const APP_GROUPS_ENTITLEMENT = 'com.apple.security.application-groups';

const APP_GROUP_KEY = 'ExpoCallScreeningAppGroup';
const EXTENSION_BUNDLE_IDENTIFIER_KEY = 'ExpoCallScreeningExtensionBundleIdentifier';

// Resolve from the package, not the consuming app; src/ and build/ have the same depth.
const TARGETS_DIR = path.resolve(__dirname, '../../targets');

type AppleTargetsProps = {
  root?: string;
  appleTeamId?: string;
};

// apple-targets exports the plugin directly via module.exports.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const withTargetsDir = require('@bacons/apple-targets/app.plugin') as ConfigPlugin<AppleTargetsProps>;

// apple-targets evaluates its target config synchronously, after the host settings below are set.
const withIosCallDirectoryPlugin: ConfigPlugin<ExpoCallScreeningPluginProps['ios']> = (config, props) => {
  if (config.platforms && !config.platforms.includes('ios')) {
    return config;
  }
  if (!config.ios && !props) {
    return config;
  }
  const bundleIdentifier = config.ios?.bundleIdentifier;

  if (!bundleIdentifier) {
    throw new Error(
      '[expo-call-screening] `ios.bundleIdentifier` is missing from the Expo config. The App Group and ' +
        'the Call Directory Extension identifier are both derived from it, so set it in app.json ' +
        'before adding this plugin.'
    );
  }

  const defaultAppGroup = `group.${bundleIdentifier}`;
  const appGroup = props?.appGroup ?? defaultAppGroup;
  const extensionBundleIdentifier =
    props?.extensionBundleIdentifier ?? `${bundleIdentifier}.CallDirectory`;

  config.ios = config.ios ?? {};

  const configuredAppGroups = config.ios.entitlements?.[APP_GROUPS_ENTITLEMENT];
  const existingAppGroups: string[] = Array.isArray(configuredAppGroups) ? configuredAppGroups : [];
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

  const projectRoot =
    (config._internal as { projectRoot?: string } | undefined)?.projectRoot ?? process.cwd();

  // Mods run in reverse registration order. Register before apple-targets so
  // its generated Info.plist exists before we update it, including on --clean.
  config = withDangerousMod(config, ['ios', async (config) => {
    const plistPath = path.join(TARGETS_DIR, 'call-directory/Info.plist');
    const info = plist.parse(await fs.promises.readFile(plistPath, 'utf8')) as PlistObject;
    await fs.promises.writeFile(plistPath, plist.build({ ...info, [APP_GROUP_KEY]: appGroup }));
    return config;
  }]);

  return withTargetsDir(config, {
    // apple-targets requires a project-relative glob path with forward slashes.
    root: path.relative(projectRoot, TARGETS_DIR).split(path.sep).join('/'),
    appleTeamId: config.ios?.appleTeamId,
  });
};

export const withIosCallDirectory: ConfigPlugin<ExpoCallScreeningPluginProps['ios']> =
  createRunOncePlugin(withIosCallDirectoryPlugin, 'expo-call-screening:ios-call-directory', pkg.version);
