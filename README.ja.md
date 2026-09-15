# call-screening-example

[English](./README.md)

[`expo-call-screening`](./modules/expo-call-screening/README.ja.md) のサンプルアプリです。iOS の Call Directory Extension と Android の `CallScreeningService` を使い、着信時に発信者名を表示するローカル Expo Module を動かします。

## 実行

```sh
pnpm install
EXPO_APPLE_TEAM_ID=XXXXXXXXXX pnpm expo prebuild --clean
pnpm expo run:ios --device
pnpm expo run:android --device
```

着信時の名前表示を確認するには SIM の入った実機が必要です。

画面は `src/app/index.tsx` の 1 つだけで、電話番号と表示名の保存、現在のステータス表示、モジュールの reload と権限要求の操作ができます。Android ではオーバーレイ権限の状態表示と、その権限を要求するボタンも表示されます。

## モジュールを開発するとき

Config Plugin は TypeScript です。`modules/expo-call-screening/plugin/src` を編集したら、prebuild の前に再ビルドしてください。

```sh
pnpm build:plugin
```
