import {
  AndroidConfig,
  ConfigPlugin,
  withAndroidManifest,
  withDangerousMod,
} from 'expo/config-plugins';
import fs from 'fs';
import path from 'path';

import type { ExpoCallScreeningPluginProps } from './types';

const SERVICE_NAME = 'expo.modules.callscreening.ExpoCallScreeningService';

const SERVICE_PERMISSION = 'android.permission.BIND_SCREENING_SERVICE';

const SERVICE_ACTION = 'android.telecom.CallScreeningService';

const OVERLAY_PERMISSION = 'android.permission.SYSTEM_ALERT_WINDOW';

const CALL_STATE_PERMISSION = 'android.permission.READ_PHONE_STATE';

const OVERLAY_LAYOUT_TARGET = 'app/src/main/res/layout/expo_call_screening_overlay.xml';

// Expo does not export ManifestService directly.
type ManifestService = NonNullable<AndroidConfig.Manifest.ManifestApplication['service']>[number];

// Register via the plugin so installing the module alone does not add services or permissions.
export const withAndroidCallScreening: ConfigPlugin<ExpoCallScreeningPluginProps['android']> = (
  config,
  props
) => {
  const configWithManifest = withAndroidManifest(config, (config) => {
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
    application.service = withCallScreeningService(application.service ?? []);
    // ensurePermission avoids duplicates when prebuild runs again.
    AndroidConfig.Permissions.ensurePermission(config.modResults, OVERLAY_PERMISSION);
    AndroidConfig.Permissions.ensurePermission(config.modResults, CALL_STATE_PERMISSION);
    return config;
  });

  return props?.overlayLayout
    ? withOverlayLayout(configWithManifest, props.overlayLayout)
    : configWithManifest;
};

function withCallScreeningService(services: ManifestService[]): ManifestService[] {
  const isDeclared = services.some((service) => service.$['android:name'] === SERVICE_NAME);
  return isDeclared ? services : [...services, createCallScreeningService()];
}

function createCallScreeningService(): ManifestService {
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
const withOverlayLayout: ConfigPlugin<string> = (config, overlayLayout) =>
  withDangerousMod(config, [
    'android',
    async (config) => {
      const source = path.resolve(config.modRequest.projectRoot, overlayLayout);
      if (!fs.existsSync(source)) {
        throw new Error(
          `[expo-call-screening] The overlay layout "${overlayLayout}" does not exist. It is resolved ` +
            `relative to the project root, which puts it at "${source}".`
        );
      }

      const destination = path.join(config.modRequest.platformProjectRoot, OVERLAY_LAYOUT_TARGET);
      await fs.promises.mkdir(path.dirname(destination), { recursive: true });
      await fs.promises.copyFile(source, destination);
      return config;
    },
  ]);
