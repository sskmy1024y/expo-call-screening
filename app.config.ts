import type { ConfigContext, ExpoConfig } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'Caller ID PoC',
  slug: 'call-screening-example',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/images/icon.png',
  scheme: 'callscreening',
  userInterfaceStyle: 'automatic',
  ios: {
    icon: './assets/expo.icon',
    bundleIdentifier: 'com.example.callerid-test',
    // @bacons/apple-targets needs a team id to sign the Call Directory
    // Extension target. Export EXPO_APPLE_TEAM_ID before prebuilding for iOS.
    appleTeamId: process.env.EXPO_APPLE_TEAM_ID,
  },
  android: {
    package: 'com.example.callerid',
    adaptiveIcon: {
      backgroundColor: '#E6F4FE',
      foregroundImage: './assets/images/android-icon-foreground.png',
      backgroundImage: './assets/images/android-icon-background.png',
      monochromeImage: './assets/images/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
  },
  web: {
    output: 'static',
    favicon: './assets/images/favicon.png',
  },
  plugins: [
    'expo-router',
    [
      'expo-splash-screen',
      {
        backgroundColor: '#208AEF',
        image: './assets/images/splash-icon.png',
        imageWidth: 76,
      },
    ],
    ['./modules/expo-call-screening/app.plugin.js', {}],
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
});
