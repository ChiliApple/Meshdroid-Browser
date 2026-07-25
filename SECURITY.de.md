<!-- Sprache: [English](SECURITY.md) · **Deutsch** -->

# Sicherheitshinweise

## Bedrohungsmodell

Die App ist eine Fernwartungskonsole. Wer sie öffnet, hat in aller Regel Zugriff auf
fremde Systeme. Entsprechend gilt die Sitzung selbst als das schützenswerte Gut —
nicht die App.

Abgedeckt:

- Abhören oder Manipulation der Verbindung im Netz
- Weiterleitung der Sitzung auf einen fremden Host
- Auslesen der Sitzung durch andere Apps oder über Cloud-Backups
- Fremder Zugriff auf ein unbeaufsichtigtes, entsperrtes Gerät

Nicht abgedeckt:

- Kompromittierter MeshCentral-Server
- Gerootetes oder anderweitig kompromittiertes Android-System
- Angreifer, die Geräte-PIN und Biometrie kennen

## Umsetzung

| Maßnahme | Ort |
|---|---|
| Nur HTTPS, kein Klartext | `res/xml/network_security_config.xml`, `usesCleartextTraffic="false"` |
| Nur System-CAs, keine Benutzerzertifikate | `network_security_config.xml` |
| Zertifikatsfehler nicht überschreibbar | `MainActivity` — `onReceivedSslError` bewusst nicht implementiert |
| Kein Mixed Content | `MIXED_CONTENT_NEVER_ALLOW` |
| Navigation nur zum konfigurierten Host | `WebUrl.isSameServer()`, `shouldOverrideUrlLoading` |
| Gesperrte Schemata (`javascript:`, `file:`, `content:`, `data:`, `intent:`) | `MainActivity.openExternally()` |
| Keine JavaScript-Bridge | `addJavascriptInterface` wird nirgends aufgerufen |
| Kein Datei-/Content-Zugriff aus dem WebView | `allowFileAccess`, `allowContentAccess` = false |
| Keine Popups, keine Mehrfachfenster | `javaScriptCanOpenWindowsAutomatically` = false |
| Kamera, Mikrofon, Standort abgelehnt | `onPermissionRequest`, `onGeolocationPermissionsShowPrompt` |
| Kein Cloud-Backup der Sitzung | `allowBackup="false"`, `data_extraction_rules.xml` |
| Screenshot- und Übersichtsschutz | `FLAG_SECURE`, standardmäßig aktiv |
| Biometrische Sperre | `AppLock`, opt-in, Sperre nach 60 s im Hintergrund |
| Kein WebView-Remote-Debugging im Release | `App.kt`, nur bei `BuildConfig.DEBUG` |
| Downloads nur vom konfigurierten Host | `MainActivity.handleDownload()` |
| Sitzungsdaten beim Serverwechsel verworfen | `SettingsActivity.bindServerUrl()` |

## Betrieb hinter einem TLS-inspizierenden Proxy

Standardmäßig vertraut die App ausschließlich den System-CAs. Steht eine Firewall im Weg,
die TLS aufbricht und mit eigener CA neu signiert, schlägt jede Verbindung fehl — beabsichtigt,
denn genau dieser Fall ist von außen nicht von einem Angriff zu unterscheiden.

Wer das braucht, ergänzt in `res/xml/network_security_config.xml`:

```xml
<trust-anchors>
    <certificates src="system" />
    <certificates src="user" />
</trust-anchors>
```

Damit akzeptiert die App jedes vom Nutzer installierte Zertifikat. Besser ist es, nur die
eine benötigte CA einzupinnen, statt den Benutzerspeicher pauschal zu öffnen.

## Signaturschlüssel

Die APKs sind selbstsigniert. Der Schlüssel gehört in einen Passwortmanager und in die
Repository-Secrets — nicht ins Repository. Geht er verloren, lässt sich keine App-Aktualisierung
mehr installieren; dann hilft nur Deinstallieren und Neuinstallieren, was alle App-Daten
mitsamt Sitzung entfernt.

## Lücken melden

Fehler mit Sicherheitsbezug bitte per privatem Security Advisory melden, nicht als
öffentliches Issue.
