import { ConfigPlugin, createRunOncePlugin, withPlugins } from 'expo/config-plugins';

import type { ExpoCallScreeningPluginProps } from './types';
import { withAndroidCallScreening } from './withAndroidCallScreening';
import { withIosCallDirectory } from './withIosCallDirectory';

const pkg = require('../../package.json') as { name: string; version: string };

/**
 * Generates the native pieces the caller identification PoC needs:
 * an iOS Call Directory Extension target plus its App Group, and the Android
 * call screening service with its manifest entries.
 */
const withExpoCallScreening: ConfigPlugin<ExpoCallScreeningPluginProps | undefined> = (config, props = {}) =>
  withPlugins(config, [
    [withIosCallDirectory, props.ios],
    [withAndroidCallScreening, props.android],
  ]);

export default createRunOncePlugin(withExpoCallScreening, pkg.name, pkg.version);

export type { ExpoCallScreeningPluginProps };
