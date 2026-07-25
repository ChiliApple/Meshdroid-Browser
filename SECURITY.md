<!-- Language: **English** · [Deutsch](SECURITY.de.md) -->

# Security notes

## Threat model

The app is a remote-management console. Whoever opens it usually has access to other
people's systems. The session itself is therefore the asset worth protecting — not the app.

Covered:

- Eavesdropping or tampering with the connection on the network
- Redirecting the session to a foreign host
- Other apps or cloud backups reading the session
- Someone accessing an unattended, unlocked device

Not covered:

- A compromised MeshCentral server
- A rooted or otherwise compromised Android system
- An attacker who knows the device PIN and biometrics

## Implementation

| Measure | Where |
|---|---|
| HTTPS only, no cleartext | `res/xml/network_security_config.xml`, `usesCleartextTraffic="false"` |
| System CAs only, no user certificates | `network_security_config.xml` |
| Certificate errors not overridable | `MainActivity` — `onReceivedSslError` deliberately not implemented |
| No mixed content | `MIXED_CONTENT_NEVER_ALLOW` |
| Navigation limited to the configured host | `WebUrl.isSameServer()`, `shouldOverrideUrlLoading` |
| Blocked schemes (`javascript:`, `file:`, `content:`, `data:`, `intent:`) | `MainActivity.openExternally()` |
| No JavaScript bridge | `addJavascriptInterface` is never called |
| Touch spoof is one-way | `DocumentStartJavaScript` injects a script; nothing is returned to the app |
| No file/content access from the WebView | `allowFileAccess`, `allowContentAccess` = false |
| No popups, no multiple windows | `javaScriptCanOpenWindowsAutomatically` = false |
| Camera, microphone, location denied | `onPermissionRequest`, `onGeolocationPermissionsShowPrompt` |
| No cloud backup of the session | `allowBackup="false"`, `data_extraction_rules.xml` |
| Screenshot and recents protection | `FLAG_SECURE`, on by default |
| Biometric lock | `AppLock`, opt-in, locks after 60 s in the background |
| No WebView remote debugging in release | `App.kt`, only under `BuildConfig.DEBUG` |
| Downloads only from the configured host | `MainActivity.handleDownload()` |
| Session data wiped on server change | `SettingsActivity.bindServerUrl()` |

## The touch spoof and its scope

To make the navigation visible on foldables, the app injects a `DocumentStartJavaScript`
that overrides `window.matchMedia` for `(pointer: coarse)` / `(hover: none)` and
`navigator.maxTouchPoints`. This is scoped to the configured origin only and runs one-way —
it exposes no callback from the page back into the app, so it is not a JavaScript bridge.
It touches nothing else; `prefers-color-scheme` and every other media query pass through
unchanged.

## Running behind a TLS-inspecting proxy

By default the app trusts system CAs only. If a firewall sits in the path that breaks TLS
and re-signs with its own CA, every connection fails — by design, because from the outside
that case is indistinguishable from an attack.

If you need it, add to `res/xml/network_security_config.xml`:

```xml
<trust-anchors>
    <certificates src="system" />
    <certificates src="user" />
</trust-anchors>
```

That makes the app accept any user-installed certificate. Better to pin just the one CA you
need rather than opening the user store wholesale.

## Signing key

The APKs are self-signed. The key belongs in a password manager and in the repository
secrets — not in the repository. If it is lost, no app update can be installed; the only
way out is uninstall and reinstall, which removes all app data including the session.

## Reporting

Please report security-relevant issues via a private security advisory, not a public issue.
