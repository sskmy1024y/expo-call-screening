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

モジュールの JavaScript と Config Plugin は TypeScript で、使用前にコンパイルが必要です。`pnpm install` 時に `postinstall` で JavaScript がコンパイルされます。`modules/expo-call-screening/src` または `plugin/src` を編集したら、両方を再ビルドして prebuild をやり直してください。

```sh
pnpm build:plugin
```

ルートの pnpm workspace がモジュールのビルド用依存関係もインストールします。Config Plugin の回帰テストは次のコマンドで実行できます。

```sh
pnpm test
```

モジュール単体のリリース用 tarball を作る場合（`dist/` に出力）:

```sh
pnpm pack:plugin
```

## alpha版を承認待ちとしてnpmへ送る

[publish.yml](.github/workflows/publish.yml) が GitHub Release の公開時（pre-release を含む）にモジュールを承認待ちとして送信します。npmjsでメンテナーが内容を確認し、2FA認証で承認すると一般公開されます。npm Trusted Publishing（OIDC）を使うため、`NPM_TOKEN` シークレットは不要です。Release タグは `v<モジュールのバージョン>`、バージョンは `0.0.1-alpha.0` の形式に限定します。npm の dist-tag は常に `alpha` で、`latest` は更新しません。

npm パッケージの **Settings** で GitHub Actions の Trusted Publisher を登録します。

| 項目 | 値 |
| --- | --- |
| Organization or user | `sskmy1024y` |
| Repository | `expo-call-screening` |
| Workflow filename | `publish.yml` |
| Environment name | 空欄 |
| Allowed actions | 直接公開のチェックを外し、staged publishingだけ許可する |

npmjs.com に空のパッケージを事前登録する方法はありません。OIDC には既存パッケージへの Trusted Publisher 設定が必要で、`npm stage publish` も新規パッケージの作成には使えません。初回はローカルで対話認証して公開し、パッケージを作成します。`NPM_TOKEN` シークレットは不要です。

```sh
pnpm install --frozen-lockfile
pnpm test
pnpm pack:plugin
npm login --registry=https://registry.npmjs.org
version=$(node -p "require('./modules/expo-call-screening/package.json').version")
npm publish "./dist/expo-call-screening-${version}.tgz" --tag alpha --access public --registry=https://registry.npmjs.org
```

ブラウザーでのログインと必要な2FA認証を完了し、その後で上記の Trusted Publisher を登録します。以降はトークンを保存せずCIから承認待ちとして送信できます。承認には対話認証と2FAが必要です。初回に `0.0.1-alpha.0` を公開した場合、CI用は `0.0.1-alpha.1` に更新してください。同じバージョンは上書きできません。所有権の競合が表示された場合は、先にnpm上の所有権を解決する必要があります。

ワークフローを GitHub に反映し、Trusted Publisher を設定した後の手順:

1. `modules/expo-call-screening/package.json` を未公開のalphaバージョンに更新し、コミット・pushします（現在は `0.0.1-alpha.1`）。
2. そのコミットを対象に、同じバージョンのタグ（例: `v0.0.1-alpha.1`）で GitHub Release を作成します。**Set as a pre-release** を選択して公開します。
3. Actions の **Stage alpha on npm** を確認します。バージョン検証 → 依存関係のインストール → テスト・型チェック → tarball作成 → npmへの承認待ち送信の順に実行されます。
4. npmjsの **Staged Packages** でパッケージ・バージョンを確認し、**Approve** を選択します。2FA認証を完了すると `alpha` タグで公開されます。CLIの場合は `npm login` 後、`npm stage list expo-call-screening` → `npm stage view <stage-id>` → `npm stage approve <stage-id>` の順でも操作できます。
5. `npm view expo-call-screening dist-tags --json` で公開結果を確認します。利用者は `pnpm expo install expo-call-screening@alpha` で導入できます。

タグ作成やReleaseの下書き保存だけでは公開されません。Node 24 と npm 11.19.1 を使います（staged publishingの要件は npm 11.15.0以上・Node 22.14.0以上）。CIの成功は承認待ちへの送信完了を意味し、一般公開はまだされていません。承認待ちのバージョン番号は予約されるため、同じバージョンを再送する前に既存のstageを承認または却下してください。詳しくは [npm staged publishing](https://docs.npmjs.com/staged-publishing/) と [npm Trusted Publishing](https://docs.npmjs.com/trusted-publishers/) を参照してください。このワークフローではネイティブビルド・実機テストは実行しません。
