const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');
const plist = require('@expo/plist').default;

const moduleRoot = path.resolve(__dirname, '../..');

// Exercise the real generated plugin and apple-targets mods without writing
// generated files into this checkout or an installed package's target folder.
function fixture(t) {
  const projectRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'call-screening-test-'));
  t.after(() => fs.rmSync(projectRoot, { recursive: true, force: true }));
  const installedModule = path.join(projectRoot, 'module');
  fs.mkdirSync(installedModule);
  for (const file of ['package.json', 'plugin/build', 'targets']) {
    fs.cpSync(path.join(moduleRoot, file), path.join(installedModule, file), { recursive: true });
  }
  fs.symlinkSync(path.join(moduleRoot, 'node_modules'), path.join(installedModule, 'node_modules'), 'dir');
  const plugin = require(path.join(installedModule, 'plugin/build')).default;
  const config = () => ({
    name: 'Caller ID test', slug: 'caller-id-test',
    _internal: { projectRoot },
  });
  async function runMod(config, platform, name, modResults = {}) {
    return config.mods[platform][name]({
      ...config,
      modResults,
      modRequest: {
        projectRoot,
        platformProjectRoot: path.join(projectRoot, platform),
        platform, modName: name, introspect: false,
      },
    });
  }
  return { projectRoot, installedModule, plugin, config, runMod };
}

test('Android-only config registers the service and permissions without iOS settings', async (t) => {
  const f = fixture(t);
  const config = f.plugin({ ...f.config(), android: { package: 'com.example.android' } });
  assert.equal(config.mods?.ios, undefined);
  const { modResults } = await f.runMod(config, 'android', 'manifest', {
    manifest: {
      $: { 'xmlns:android': 'http://schemas.android.com/apk/res/android' },
      application: [{ $: { 'android:name': '.MainApplication' } }],
    },
  });
  const service = modResults.manifest.application[0].service[0];
  assert.equal(service.$['android:name'], 'expo.modules.callscreening.ExpoCallScreeningService');
  assert.equal(service.$['android:permission'], 'android.permission.BIND_SCREENING_SERVICE');
  assert.equal(service.$['android:exported'], 'true');
  const permissions = modResults.manifest['uses-permission'].map((p) => p.$['android:name']);
  assert.ok(permissions.includes('android.permission.READ_PHONE_STATE'));
  assert.ok(permissions.includes('android.permission.SYSTEM_ALERT_WINDOW'));
});

test('explicit Android platforms skip unused iOS settings; incomplete iOS config still fails', (t) => {
  const f = fixture(t);
  assert.equal(f.plugin({ ...f.config(), platforms: ['android'], ios: {} }).mods?.ios, undefined);
  assert.throws(() => f.plugin({ ...f.config(), ios: {} }), /ios.bundleIdentifier/);
});

test('prebuild shares the exact App Group, preserves extension metadata and updates on rerun', async (t) => {
  const f = fixture(t);
  const plistPath = path.join(f.installedModule, 'targets/call-directory/Info.plist');
  fs.rmSync(plistPath, { force: true });
  for (const appGroup of ['group.com.example.app', 'group.com.example.shared']) {
    const config = f.plugin({
      ...f.config(),
      ios: {
        bundleIdentifier: 'com.example.app', appleTeamId: 'TESTTEAM01',
        entitlements: { 'com.apple.security.application-groups': ['group.com.example.other'] },
      },
    }, appGroup.endsWith('.shared') ? { ios: {
      appGroup, extensionBundleIdentifier: 'com.example.app.extensions.Directory',
    } } : undefined);
    await f.runMod(config, 'ios', 'dangerous');
    const info = plist.parse(fs.readFileSync(plistPath, 'utf8'));
    assert.equal(info.ExpoCallScreeningAppGroup, appGroup);
    assert.equal(info.ExpoCallScreeningAppGroup, config.ios.infoPlist.ExpoCallScreeningAppGroup);
    assert.equal(info.NSExtension.NSExtensionPointIdentifier, 'com.apple.callkit.call-directory');
    assert.equal(info.NSExtension.NSExtensionPrincipalClass, '$(PRODUCT_MODULE_NAME).CallDirectoryHandler');
    const target = config.extra.eas.build.experimental.ios.appExtensions[0];
    assert.equal(target.bundleIdentifier, config.ios.infoPlist.ExpoCallScreeningExtensionBundleIdentifier);
    assert.ok(target.entitlements['com.apple.security.application-groups'].includes(appGroup));
    assert.ok(config.ios.entitlements['com.apple.security.application-groups'].includes('group.com.example.other'));
  }
});
