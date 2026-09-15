import { ConfigPlugin, createRunOncePlugin, withPlugins } from 'expo/config-plugins';

import type { ExpoCallScreeningPluginProps } from './types';
import { withAndroidCallScreening } from './withAndroidCallScreening';
import { withIosCallDirectory } from './withIosCallDirectory';

const pkg = require('../../package.json') as { name: string; version: string };

const withExpoCallScreening: ConfigPlugin<ExpoCallScreeningPluginProps | undefined> = (config, props = {}) =>
  withPlugins(config, [
    [withIosCallDirectory, props.ios],
    [withAndroidCallScreening, props.android],
  ]);

export default createRunOncePlugin(withExpoCallScreening, pkg.name, pkg.version);

export type { ExpoCallScreeningPluginProps };
