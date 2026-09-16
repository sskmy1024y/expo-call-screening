# call-screening-example

[日本語](./README.ja.md)

Example app for [`expo-call-screening`](./modules/expo-call-screening/README.md), a
local Expo Module that shows caller identities on incoming calls through the
iOS Call Directory Extension and the Android `CallScreeningService`.

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshot-ios1.jpg" alt="Incoming call on iOS showing the registered name" width="200"></td>
    <td align="center" width="25%"><img src="docs/screenshot-ios2.jpg" alt="iOS keypad showing the registered name" width="200"></td>
    <td align="center" width="25%"><img src="docs/screenshot-android1.jpg" alt="Android band sitting below the incoming-call notification" width="200"></td>
    <td align="center" width="25%"><img src="docs/screenshot-android2.jpg" alt="Android band on the full-screen incoming call" width="200"></td>
  </tr>
  <tr>
    <td align="center">iOS — incoming call</td>
    <td align="center">iOS — keypad</td>
    <td align="center">Android — alongside the notification</td>
    <td align="center">Android — incoming call</td>
  </tr>
</table>

## Run

```sh
pnpm install
EXPO_APPLE_TEAM_ID=XXXXXXXXXX pnpm expo prebuild --clean
pnpm expo run:ios --device
pnpm expo run:android --device
```

A physical device with a SIM is required to see the caller identity on an
incoming call.

The single screen in `src/app/index.tsx` lets you save a phone number and a
display name, shows the current status, and exposes the reload and permission
actions of the module. On Android it also shows whether the overlay permission
is granted, with a button to request it.

## Working on the module

The module's JavaScript and its config plugin are TypeScript and are compiled
before use. `pnpm install` compiles the JavaScript through `postinstall`; after
editing anything under `modules/expo-call-screening/src` or `plugin/src`, rebuild
both and re-run prebuild:

```sh
pnpm build:plugin
```

The root pnpm workspace installs the module's build dependencies as well. Run
the config plugin regression tests with:

```sh
pnpm test
```

To produce the release tarball for the module alone (written to `dist/`):

```sh
pnpm pack:plugin
```

## TODO

- [ ] Normalise the phone number field to E.164 before saving it, using a region taken from the
  device locale, so a mistyped number is caught in the form rather than thrown by
  `setCallerIdentities`. The module deliberately leaves this to the app, which is the side that
  knows the region its users type numbers for.
