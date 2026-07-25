<!-- Language: **English** · [Deutsch](README.de.md) -->

# Meshdroid Browser

A lean, hardened Android client for [MeshCentral](https://github.com/Ylianst/MeshCentral).

MeshCentral ships only a web interface and an Android **agent** — there is no admin app.
This app fills that gap: it wraps the MeshCentral web interface in a dedicated, hardened
WebView with toggles for desktop/mobile view and appearance, built for foldables.

> Not an official project and in no way affiliated with MeshCentral or Anthropic.

---

## Features

| | |
|---|---|
| **Desktop ⇄ Mobile** | One tap in the toolbar. Sets a fixed viewport width so MeshCentral doesn't collapse into its mobile layout. |
| **Light / Dark / System** | MeshCentral follows the app appearance automatically via `prefers-color-scheme`. |
| **Foldable-ready** | Folding and unfolding does not recreate the activity — the login and any running remote session survive. |
| **Navigation always visible** | StylishUI hides all navigation on touch devices; the app neutralises that so the navigation shows on the Fold too (see below). |
| **Full screen** | Hides the toolbar and system bars, returns via a discreet button. |
| **File upload** | Through the system file picker, e.g. for the MeshCentral file manager. |
| **Full screen** memory | Off / remember last state / always start in full screen — applies globally. |
| **App lock** | Optional biometric lock with device PIN as a fallback. |

## The foldable navigation fix

The central problem this app solves: on the Samsung Fold (and any touch device),
MeshCentral with the StylishUI theme hides its entire navigation — both the sidebar and
the top tab bar. The cause is a single check in StylishUI's `custom.js`:

```js
const isMobile = window.matchMedia('(pointer: coarse)').matches
  || navigator.maxTouchPoints > 1;
if (isMobile) return;   // skips rendering the navigation
```

It classifies **any** touch device as mobile, regardless of screen width, user agent, or
layout mode — which is why none of those levers help. The check runs once on load and is
never revisited.

The app registers a `DocumentStartJavaScript` (androidx.webkit) that runs **before** any
page script and reports `(pointer: coarse)` as `false` and `maxTouchPoints` as `0`.
StylishUI then treats the device as a desktop and renders the navigation normally. Only
those two touch queries are spoofed; `prefers-color-scheme` is left untouched, so dark
mode keeps working.

## Security

Built as a remote-management console, so it is deliberately restrictive:

- **HTTPS only.** Cleartext is disabled via `networkSecurityConfig` and `usesCleartextTraffic="false"`.
- **System CAs only.** User-installed certificates are ignored.
- **Certificate errors abort.** `onReceivedSslError` is deliberately not overridden — there is no "proceed anyway" button.
- **Navigation lock.** Only the configured host loads in the WebView; everything else goes to the system browser. A slipped-in link can't take the session with it.
- **No JavaScript bridge.** `addJavascriptInterface` is never used. The touch spoof is a one-way `DocumentStartJavaScript`, not a bridge.
- **No backup.** Cookies and settings are excluded from cloud backup and device transfer.
- **Screenshot protection** (`FLAG_SECURE`) is on by default and can be turned off.
- **No permissions** beyond internet, network state and biometrics. Camera, microphone and location requests from the page are denied.
- The server address is **not in the source**; it is asked for on first launch.

More detail, and how to adapt for TLS-inspecting proxies: [SECURITY.md](SECURITY.md)

## Installation

Prebuilt APKs are under [Releases](../../releases). The APK is self-signed — the first
time you'll need to allow installation from unknown sources. Verify against the bundled
`.sha256` file.

On first launch you'll be asked for the server address, e.g. `mesh.example.org`.

## Build it yourself

Requirements: JDK 21, Android SDK with platform 36.

```bash
./gradlew :app:assembleDebug
```

For a signed release build, create a `keystore.properties` in the project root
(it is in `.gitignore`):

```properties
storeFile=release.jks
storePassword=...
keyAlias=meshdroid
keyPassword=...
```

```bash
./gradlew :app:assembleRelease
```

Without a keystore the release build still runs, but produces an unsigned APK.

## CI

`.github/workflows/build.yml` builds on every push to `main` and publishes a release with
APK and checksum on a `v*` tag. For signed builds these repository secrets are expected:

| Secret | Content |
|---|---|
| `KEYSTORE_B64` | keystore, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

If the secrets are missing, CI still builds — just unsigned.

## Technical

Kotlin, AGP 8.13.2, Gradle 8.14.5, `compileSdk`/`targetSdk` 36, `minSdk` 29.

## Known limitations

- **Downloads from the MeshCentral file manager** are generated as `blob:` URLs. WebViews
  can't hand those to the Android DownloadManager; the app points this out and offers
  "Open in browser". Ordinary `https` downloads (agent installers and the like) work.
- Switching appearance recreates the activity. The session persists via cookies, but the
  page reloads.

## Disclaimer

Use at your own risk. This software is provided "as is", without warranty of any kind,
express or implied (see the Apache 2.0 license). It is an independent, unofficial client
and has no affiliation with, endorsement by, or support from the MeshCentral project or
Anthropic. You are responsible for how you connect it and to which server. The author
accepts no liability for any damage, data loss, or security incident arising from its use.

## License

[Apache License 2.0](LICENSE) — the same license as MeshCentral.
