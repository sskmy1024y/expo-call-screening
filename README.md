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

## Staging an alpha for npm publication

[publish.yml](.github/workflows/publish.yml) stages the module for approval when
a GitHub Release is published, including pre-releases. A maintainer then approves
the package on npmjs.com with 2FA before it becomes available. It uses npm Trusted Publishing
(OIDC); no `NPM_TOKEN` secret is needed. Releases must use `v<module version>`,
and the module version must have the form `0.0.1-alpha.0`. The npm dist-tag is
always `alpha`; this workflow does not update `latest`.

Configure a GitHub Actions trusted publisher in the npm package's **Settings**:

| Field | Value |
| --- | --- |
| Organization or user | `sskmy1024y` |
| Repository | `expo-call-screening` |
| Workflow filename | `publish.yml` |
| Environment name | Leave empty |
| Allowed actions | Leave direct publishing unchecked; allow staged publishing only |

A new package cannot be registered as an empty entry on npmjs.com. OIDC requires
an existing package with a trusted publisher configured; `npm stage publish`
also cannot create a new package. For the first release, create the package by
publishing locally with interactive authentication (no `NPM_TOKEN` secret):

```sh
pnpm install --frozen-lockfile
pnpm test
pnpm pack:plugin
npm login --registry=https://registry.npmjs.org
version=$(node -p "require('./modules/expo-call-screening/package.json').version")
npm publish "./dist/expo-call-screening-${version}.tgz" --tag alpha --access public --registry=https://registry.npmjs.org
```

Complete the browser login and any 2FA prompt, then configure the trusted
publisher above. Subsequent releases can be staged through CI without a stored token.
Approval requires interactive authentication with 2FA. After manually publishing `0.0.1-alpha.0`, use `0.0.1-alpha.1` for the
first CI release; npm versions cannot be overwritten. If npm reports a package
ownership conflict, resolve that before publishing.

After the workflow is on GitHub and the trusted publisher is configured:

1. Update `modules/expo-call-screening/package.json` to an unused alpha version
   (the current version is `0.0.1-alpha.1`), commit, and push it.
2. Create a GitHub Release with the matching tag, e.g. `v0.0.1-alpha.1`, targeting
   that commit. Select **Set as a pre-release** and publish the release.
3. Check the **Stage alpha on npm** run in Actions. It validates the version,
   installs dependencies, runs tests and type checks, builds the tarball, and
   submits it to npm for approval.
4. Open **Staged Packages** on npmjs.com, review the package/version and select
   **Approve**. Complete 2FA to publish it with the `alpha` tag. Alternatively,
   after `npm login`, use `npm stage list expo-call-screening`,
   `npm stage view <stage-id>`, and `npm stage approve <stage-id>`.
5. Verify with `npm view expo-call-screening dist-tags --json`. Install with
   `pnpm expo install expo-call-screening@alpha`.

Creating a tag or saving a draft release alone does not publish the package.
The job uses Node 24 and npm 11.19.1. Staged publishing requires npm 11.15.0+
and Node 22.14.0+. A successful workflow means the package is staged, not yet
published. Staged versions reserve their version number; approve or reject an
existing stage before retrying that version.
See [npm staged publishing](https://docs.npmjs.com/staged-publishing/) and
[Trusted Publishing](https://docs.npmjs.com/trusted-publishers/) for setup
and troubleshooting. This workflow does not run native builds or device tests.
