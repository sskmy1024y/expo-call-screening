# call-screening-example

[日本語](./README.ja.md)

Example app for [`expo-call-screening`](./modules/expo-call-screening/README.md), a
local Expo Module that shows caller identities on incoming calls through the
iOS Call Directory Extension and the Android `CallScreeningService`.

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

The config plugin is TypeScript. After editing
`modules/expo-call-screening/plugin/src`, rebuild it before running prebuild:

```sh
pnpm build:plugin
```
