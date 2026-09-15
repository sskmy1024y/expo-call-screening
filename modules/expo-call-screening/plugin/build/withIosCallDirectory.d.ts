import { type ConfigPlugin } from 'expo/config-plugins';
import type { ExpoCallScreeningPluginProps } from './types';
/**
 * Adds the CallKit Call Directory Extension and the App Group it shares with the app.
 * Running it twice is a no-op, which keeps a duplicated plugin entry from
 * registering the extension target twice.
 */
export declare const withIosCallDirectory: ConfigPlugin<ExpoCallScreeningPluginProps['ios']>;
