/**
 * `@bacons/apple-targets` target definition for the CallKit Call Directory Extension.
 *
 * The values below are read from the host app's Expo config, which the
 * `expo-call-screening` config plugin fills in before it hands control to
 * `@bacons/apple-targets`. Keeping a single source of truth matters: the
 * extension derives the shared App Group from its own bundle identifier at
 * runtime (see `CallDirectoryHandler.swift`).
 *
 * No `Info.plist` is checked in on purpose. `@bacons/apple-targets` generates
 * one for the `call-directory` type with
 * `NSExtensionPrincipalClass = $(PRODUCT_MODULE_NAME).CallDirectoryHandler`,
 * which resolves to the class in `CallDirectoryHandler.swift`.
 *
 * @type {import('@bacons/apple-targets/app.plugin').ConfigFunction}
 */
module.exports = (config) => ({
  type: 'call-directory',
  name: 'CallDirectory',
  frameworks: ['CallKit'],
  bundleIdentifier: config.ios?.infoPlist?.ExpoCallScreeningExtensionBundleIdentifier,
  entitlements: {
    // `call-directory` is not one of the target types that mirror the app's App
    // Groups automatically, so the mirroring has to be spelled out here.
    'com.apple.security.application-groups':
      config.ios?.entitlements?.['com.apple.security.application-groups'] ?? [],
  },
  deploymentTarget: '15.1',
});
