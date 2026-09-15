# expo-call-screening

[English](./README.md)

Expo アプリ向けの Caller ID。JavaScript から「電話番号 → 表示名」を登録すると、着信時に OS がその名前を表示します。

| プラットフォーム | 仕組み | 表示先 |
| --- | --- | --- |
| iOS | CallKit Call Directory Extension | iOS 標準の着信画面 |
| Android | `CallScreeningService` | 着信画面に重ねて表示するオーバーレイ |

必要なネイティブ設定は Config Plugin が `pnpm expo prebuild` 時にすべて生成します。iOS の Extension Target と App Group、Android の Service 登録が対象です。Xcode や Android Studio での手作業は不要で、`pnpm expo prebuild --clean` で再生成しても設定は復元されます。

## 動作要件

- Expo SDK 52 以上
- iOS 15.1 以上（Expo SDK 52 の下限。CallKit Call Directory 自体は iOS 10 から利用可能）
- Android 10（API 29）以上。着信スクリーニング権限（`ROLE_CALL_SCREENING`）の取得に必要です
- Android では「他のアプリの上に重ねて表示」権限（`SYSTEM_ALERT_WINDOW`）が必要です。ユーザーが端末の設定画面で許可します。未許可の場合オーバーレイは表示されません
- Android では `READ_PHONE_STATE`（実行時権限）も使います。`requestPermission()` が要求します。用途は通話終了時にオーバーレイを閉じることのみで、拒否された場合はタイムアウトで閉じる動作にフォールバックします
- SIM の入った実機。シミュレータ / エミュレータには着信が来ません
- iOS Extension の署名に使う Apple Team ID

Web は非対応です。

## インストール

```sh
pnpm expo install expo-call-screening
```

Expo config に Plugin を追加します。

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

ネイティブプロジェクトを再生成します。

```sh
pnpm expo prebuild --clean
```

### Plugin オプション

いずれも省略可能です。iOS のオプションの既定値は `ios.bundleIdentifier` から導出されます。

```ts
plugins: [
  [
    'expo-call-screening',
    {
      ios: {
        appGroup: 'group.com.example.app',                 // 既定: group.<bundleIdentifier>
        extensionBundleIdentifier: 'com.example.app.CallDirectory', // 既定: <bundleIdentifier>.CallDirectory
      },
      android: {
        overlayLayout: './assets/caller-id-overlay.xml',   // 既定: モジュール同梱のレイアウト
      },
    },
  ],
],
```

`appGroup` は既定値のまま使うことを推奨します。Extension は自身の Bundle ID から App Group を導出するため、独自の値にすると Extension が空のリストを読むことになります。不一致があると Plugin が警告を出します。

`android.overlayLayout` には、既定のオーバーレイを差し替えるレイアウト XML のパスをプロジェクトルートからの相対パスで指定します。`expo_call_screening_label` と `expo_call_screening_phone_number` の id を持つ TextView が必須で、Service はこの 2 つに表示名と電話番号を書き込みます。指定したファイルは Plugin が `expo_call_screening_overlay` というレイアウトリソースとして配置します。同名のリソースをアプリ側に置いてもモジュール既定のレイアウトを上書きできるため、このオプションは `android/` の外にファイルを置きたい場合の手段です。

## 使い方

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

// 対応表を登録する。前回のリストは置き換えられる
await setCallerIdentities([
  { phoneNumber: '+819012345678', label: '田中 太郎 / Example Inc.' },
]);

// iOS は reload しないと反映されない。Android では何もしない
await reload();

// OS 側で有効になっているか確認する
if ((await getStatus()) !== 'enabled') {
  await requestPermission();
}

// Android は自前でラベルを描画するため、オーバーレイの権限が必要
if (Platform.OS === 'android' && !(await hasOverlayPermission())) {
  await requestOverlayPermission();
}
```

電話番号は E.164 形式（`+819012345678`）で登録してください。照合時は数字以外を除去し、iOS は国番号を含む完全な番号で比較するため、`090-1234-5678` のような国内形式は一致しません。

## API

すべての関数は両プラットフォームに存在するため、呼び出し側で `Platform.OS` の分岐は不要です。各関数の下のタグは、実際に処理が行われるプラットフォームを示します。

| 関数 | iOS | Android |
| --- | :---: | :---: |
| `setCallerIdentities` | ✅ | ✅ |
| `reload` | ✅ | 何もしない |
| `getStatus` | ✅ | ✅ |
| `requestPermission` | ✅ 設定を開く | ✅ 権限ダイアログ |
| `hasOverlayPermission` | 常に `true` | ✅ |
| `requestOverlayPermission` | 何もしない | ✅ 設定を開く |

### `setCallerIdentities(entries: CallerIdentity[]): Promise<void>`

`iOS` `Android`

保存済みリストを置き換えます。`CallerIdentity` は `{ phoneNumber: string; label: string }` です。

### `reload(): Promise<void>`

`iOS` `Android（何もしない）`

iOS: Extension を再実行して OS のデータベースを更新します。Extension が設定で無効のときは reject されます。Android: 何もしません。Service は着信ごとにリストを読みます。

### `getStatus(): Promise<'enabled' | 'disabled' | 'unknown'>`

`iOS` `Android`

| 値 | iOS | Android |
| --- | --- | --- |
| `enabled` | Extension が設定で有効 | アプリが権限を保持 |
| `disabled` | Extension が無効 | 権限はあるが未取得 |
| `unknown` | 状態を取得できない（シミュレータなど） | API 29 未満、または電話機能なし |

### `requestPermission(): Promise<void>`

`iOS` `Android`

iOS: 「設定 > 電話 > 着信拒否設定と着信 ID」を開きます。有効化はユーザーの手動操作です。Android: まず `READ_PHONE_STATE` の実行時権限を要求し、その結果にかかわらず続けてシステムの権限要求ダイアログを表示します。`READ_PHONE_STATE` は通話終了時にオーバーレイを閉じられるかどうかを決めるだけなので、拒否されても着信スクリーニング権限の要求は止まりません。ユーザーが拒否しても resolve されるため、結果は `getStatus()` で確認してください。

### `hasOverlayPermission(): Promise<boolean>`

`iOS（常に true）` `Android`

Android: `Settings.canDrawOverlays` を読みます。オーバーレイの表示には `SYSTEM_ALERT_WINDOW` 権限が必要なため、ユーザーが許可するまで `false` を返します。iOS: 常に `true` です。ラベルは OS が描画するため、許可すべきオーバーレイが存在しません。

### `requestOverlayPermission(): Promise<void>`

`iOS（何もしない）` `Android`

Android: 「他のアプリの上に重ねて表示」の設定画面を開きます。画面を開いた時点で resolve されるため、アプリがフォアグラウンドに戻ったときに `hasOverlayPermission()` で再確認してください。iOS: 何もしません。

## 実機での確認

iOS:

1. 対応表を保存し `reload()` を呼ぶ
2. 「設定 > 電話 > 着信拒否設定と着信 ID」でアプリをオンにする
3. 登録した番号から端末に電話をかける。着信画面の番号がラベルに置き換わります

Android:

1. `requestPermission()` を呼び、権限要求ダイアログで許可する
2. `requestOverlayPermission()` を呼び、「他のアプリの上に重ねて表示」を許可する
3. 対応表を保存し、その番号から端末に電話をかける
4. ラベルが着信画面に重ねて表示されます。拒否・不在着信・通話終了のいずれでも通話が終わった時点で消え、タップすればそれより早く閉じられます。`READ_PHONE_STATE` が拒否されている場合は 30 秒のタイムアウトで消えます。`adb logcat -s ExpoCallScreening:*` でも確認できます

電話網を経由した本物の着信が必要です。他アプリが CallKit 経由で発生させる擬似的な VoIP 着信では Call Directory は動作しません。

## TODO（未対応範囲）

以下は未実装です。

- [ ] 着信ブロック（iOS: `CXCallDirectoryExtensionContext.addBlockingEntry`、Android: `CallResponse.setDisallowCall`）
- [ ] OEM 製ダイアラーを問わずロック画面でもオーバーレイを確実に表示する
- [ ] 発信時の Caller ID
- [ ] iOS の Live Caller ID Lookup（サーバー照合方式、iOS 18 以上）
- [ ] 数字除去以外の電話番号正規化（libphonenumber など）
- [ ] 対応表のバックグラウンド同期
- [ ] Config Plugin の自動テスト

## ライセンス

MIT
