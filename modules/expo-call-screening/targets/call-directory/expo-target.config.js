/** @type {import('@bacons/apple-targets/app.plugin').ConfigFunction} */
module.exports = (config) => ({
  type: 'call-directory',
  name: 'CallDirectory',
  frameworks: ['CallKit'],
  bundleIdentifier: config.ios?.infoPlist?.ExpoCallScreeningExtensionBundleIdentifier,
  entitlements: {
    // call-directory targets do not inherit App Groups automatically.
    'com.apple.security.application-groups':
      config.ios?.entitlements?.['com.apple.security.application-groups'] ?? [],
  },
  deploymentTarget: '15.1',
});
