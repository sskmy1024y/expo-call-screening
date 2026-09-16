# expo-call-screening

[日本語](./README.ja.md)

Caller ID for Expo apps. Register phone number / display name pairs from
JavaScript and let the OS show the name on incoming calls.

<table>
  <tr>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/sskmy1024y/expo-call-screening/main/docs/screenshot-ios1.jpg" alt="Incoming call on iOS showing the registered name" width="200"></td>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/sskmy1024y/expo-call-screening/main/docs/screenshot-ios2.jpg" alt="iOS keypad showing the registered name" width="200"></td>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/sskmy1024y/expo-call-screening/main/docs/screenshot-android1.jpg" alt="Android band sitting below the incoming-call notification" width="200"></td>
    <td align="center" width="25%"><img src="https://raw.githubusercontent.com/sskmy1024y/expo-call-screening/main/docs/screenshot-android2.jpg" alt="Android band on the full-screen incoming call" width="200"></td>
  </tr>
  <tr>
    <td align="center">iOS — incoming call</td>
    <td align="center">iOS — keypad</td>
    <td align="center">Android — alongside the notification</td>
    <td align="center">Android — incoming call</td>
  </tr>
</table>

| Platform | Mechanism | Shown in |
| --- | --- | --- |
| iOS | CallKit Call Directory Extension | The native incoming call screen |
| Android | `CallScreeningService` | An overlay band over the incoming call screen |

The config plugin generates everything native at `pnpm expo prebuild` time: the
iOS extension target with its App Group, and the Android service registration.
No manual Xcode or Android Studio setup, and safe to regenerate with
`pnpm expo prebuild --clean`.

## Requirements

- Expo SDK 52 or newer
- iOS 15.1 or newer (the Expo SDK 52 minimum; the CallKit Call Directory API itself is available from iOS 10)
- Android 10 (API 29) or newer to acquire the call screening role
- Android: the "Display over other apps" permission (`SYSTEM_ALERT_WINDOW`),
  which the user grants in system settings. Without it the overlay never shows.
- Android: the `READ_PHONE_STATE` runtime permission, which `requestPermission()`
  asks for. It is used only to close the overlay when the call ends; denied, the
  overlay falls back to a timeout.
- A physical device with a SIM. Simulators and emulators do not receive calls.
- An Apple Team ID for signing the iOS extension

Web is not supported.

## Install

```sh
pnpm expo install expo-call-screening
```

Add the plugin to your Expo config:

```ts
// app.config.ts
export default {
  expo: {
    ios: {
      bundleIdentifier: 'com.example.app',
      appleTeamId: 'XXXXXXXXXX',
    },
    android: {
      package: 'com.example.app',
    },
    plugins: ['expo-call-screening'],
  },
};
```

Then regenerate the native projects:

```sh
pnpm expo prebuild --clean
```

### Plugin options

Every option is optional. The iOS ones are derived from `ios.bundleIdentifier`
by default.

```ts
plugins: [
  [
    'expo-call-screening',
    {
      ios: {
        appGroup: 'group.com.example.app',                 // default: group.<bundleIdentifier>
        extensionBundleIdentifier: 'com.example.app.CallDirectory', // default: <bundleIdentifier>.CallDirectory
      },
      android: {
        overlayLayout: './assets/caller-id-overlay.xml',   // default: the layout shipped by the module
      },
    },
  ],
],
```

The plugin writes `appGroup` into both the host app and extension Info.plist,
so custom App Groups and extension bundle identifiers are supported.
Android-only apps can omit `ios`; set `platforms: ['android']` when keeping
unused iOS settings in the config.

`android.overlayLayout` is a path, relative to the project root, of a layout XML
that replaces the default overlay. It must contain TextViews with the ids
`expo_call_screening_label` and `expo_call_screening_phone_number`, which is where the
service writes the display name and the number. The plugin copies the file to
the layout resource `expo_call_screening_overlay`; an app resource of that name
overrides the module default just as well, so the option is only a convenience
for projects that would rather keep the file outside `android/`.

### Placing the band

The band starts `expo_call_screening_overlay_top_offset` from the top of the screen, 280dp by
default, and is inset from each side by `expo_call_screening_overlay_side_margin`, 12dp by
default. The top offset clears the incoming-call heads-up notification on a Pixel 7, which
occupies roughly the first 185dp and is drawn above the band; dialers differ, so an app whose
band lands underneath its own notification redeclares the dimensions in
`android/app/src/main/res/values/dimens.xml`:

```xml
<resources>
  <dimen name="expo_call_screening_overlay_top_offset">260dp</dimen>
  <dimen name="expo_call_screening_overlay_side_margin">16dp</dimen>
</resources>
```

The user drags the band up and down, and where they leave it is remembered for later calls,
taking precedence over `expo_call_screening_overlay_top_offset` — so raising the dimension does
nothing once the band has been dragged. Drag it back, or clear the `expo_call_screening_overlay`
preferences, to return to the default. The band is dismissed with the
close button, by the call ending, or by the timeouts. `expo_call_screening_close` is optional in a
replacement layout — leave it out and only the latter two apply. Its spoken label comes from the
`expo_call_screening_close_description` string, which an app redeclares to localise.

## Usage

```ts
import { Platform } from 'react-native';

import {
  getStatus,
  hasOverlayPermission,
  reload,
  requestOverlayPermission,
  requestPermission,
  setCallerIdentities,
} from 'expo-call-screening';

// Register the identities. This replaces the previous list.
await setCallerIdentities([
  { phoneNumber: '+819012345678', label: 'Taro Tanaka / Example Inc.' },
]);

// Reload only when enabled; a fresh iOS installation starts disabled.
if ((await getStatus()) === 'enabled') {
  await reload();
} else {
  await requestPermission();
}

// Android draws the label itself, which needs the overlay permission.
if (Platform.OS === 'android' && !(await hasOverlayPermission())) {
  await requestOverlayPermission();
}
```

On iOS, `requestPermission()` resolves when Settings opens, before the user
has enabled the extension. Re-check and reload when the app returns to the
foreground. For example, install this listener in a mounted React component:

```ts
useEffect(() => {
  const subscription = AppState.addEventListener('change', (state) => {
    if (state === 'active') {
      void (async () => {
        if ((await getStatus()) === 'enabled') await reload();
      })().catch(console.error);
    }
  });
  return () => subscription.remove();
}, []);
```

Import `useEffect` from `react` and `AppState` from `react-native`. Handle errors
in your app's UI as appropriate. An unwritten or cleared list registers no numbers.

## Phone number format

Store numbers in **E.164**: a `+`, the country code, then the national number —
`+819012345678`. Spaces, dashes, dots and brackets are fine, since both platforms ignore them;
the `+` and the country code are not optional. `setCallerIdentities` throws on anything else,
naming the offending indexes, and stores nothing.

That strictness comes from iOS, not from taste. The Call Directory extension hands CallKit an
`Int64`, so a domestic `09012345678` is registered as `9012345678` — a different number,
accepted without complaint, that never matches.

Incoming calls are the other half, and they are not under anyone's control: carriers deliver
`09012345678` or `+819012345678` as they please. Android reconciles the two with
`PhoneNumberUtils.areSamePhoneNumber` against the SIM's country on Android 12 (API 31) and
newer, and with its trailing-digit `compare` below that, where short numbers such as internal
extensions can collide. iOS leaves the same job to CallKit. So one E.164 entry matches a call
arriving in either form.

Turning free-form input into E.164 is the app's job: it knows which region its users type
numbers for, and this module does not. Doing it in the input form also lets you reject a bad
number there, rather than at call time.

## API

Every function exists on both platforms so call sites need no `Platform.OS`
checks. The tags below each function show where it does real work.

| Function | iOS | Android |
| --- | :---: | :---: |
| `setCallerIdentities` | ✅ | ✅ |
| `reload` | ✅ | no-op |
| `getStatus` | ✅ | ✅ |
| `requestPermission` | ✅ opens Settings | ✅ role dialog |
| `hasOverlayPermission` | always `true` | ✅ |
| `requestOverlayPermission` | no-op | ✅ opens Settings |

### `setCallerIdentities(entries: CallerIdentity[]): Promise<void>`

`iOS` `Android`

Replaces the stored list. `CallerIdentity` is `{ phoneNumber: string; label: string }`.
Throws when a `phoneNumber` is not [E.164](#phone-number-format), leaving the stored list as it
was.

### `reload(): Promise<void>`

`iOS` `Android (no-op)`

iOS: asks the system to re-run the extension and rebuild its database. Rejects
while the extension is disabled in Settings. Android: no-op, the service reads
the list on every call.

### `getStatus(): Promise<'enabled' | 'disabled' | 'unknown'>`

`iOS` `Android`

| Value | iOS | Android |
| --- | --- | --- |
| `enabled` | Extension enabled in Settings | App holds the call screening role |
| `disabled` | Extension disabled | Role available but not held |
| `unknown` | State unavailable (e.g. simulator) | Below API 29, or no telephony |

### `requestPermission(): Promise<void>`

`iOS` `Android`

iOS: opens Settings > Phone > Call Blocking & Identification, where the user
enables the extension by hand. Android: asks for the `READ_PHONE_STATE` runtime
permission, then shows the system role request dialog whatever the answer was.
`READ_PHONE_STATE` only decides whether the overlay can close itself when the
call ends, so a denial never stops the role request. Resolves whether or not the
user accepts, so check `getStatus()` afterwards.

### `hasOverlayPermission(): Promise<boolean>`

`iOS (always true)` `Android`

Android: reads `Settings.canDrawOverlays`. The overlay band needs
`SYSTEM_ALERT_WINDOW`, so this is `false` until the user grants it. iOS: always
`true`, because the system draws the label and there is no overlay to permit.

### `requestOverlayPermission(): Promise<void>`

`iOS (no-op)` `Android`

Android: opens the system "Display over other apps" settings page for this app.
Resolves as soon as the page is opened, so re-check `hasOverlayPermission()`
when the app returns to the foreground. iOS: no-op.

## Verifying on a device

iOS:

1. Save an identity.
2. Enable the app under Settings > Phone > Call Blocking & Identification.
3. Return to the app, check that `getStatus()` is `enabled`, and call `reload()`.
4. Call the device from the registered number. The label replaces the number on
   the incoming call screen.

Android:

1. Call `requestPermission()` and accept the role dialog.
2. Call `requestOverlayPermission()` and allow "Display over other apps".
3. Save an identity, then call the device from that number.
4. The label appears as a band over the incoming call screen. It disappears when
   the call ends, whether it was declined, missed or hung up, and a tap dismisses
   it sooner. With `READ_PHONE_STATE` denied it stays until a 30 second timeout
   instead. `adb logcat -s ExpoCallScreening:*` shows the match.

A real call over the phone network is required. Simulated VoIP calls raised by
another app through CallKit do not exercise Call Directory.

## TODO

Not implemented yet. Contributions welcome.

- [ ] Call blocking (`CXCallDirectoryExtensionContext.addBlockingEntry` on iOS, `CallResponse.setDisallowCall` on Android)
- [ ] Show the overlay reliably on the lock screen across OEM dialers
- [ ] Outgoing caller ID
- [ ] iOS Live Caller ID Lookup (server-side lookup, iOS 18+)
- [ ] Background sync of the identity list

## License

MIT
