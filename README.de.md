<!-- Sprache: [English](README.md) · **Deutsch** -->

# Meshdroid Browser

Ein schlanker, gehärteter Android-Client für [MeshCentral](https://github.com/Ylianst/MeshCentral).

MeshCentral bringt selbst nur eine Weboberfläche und einen Android-**Agent** mit — eine
Admin-App gibt es nicht. Diese App schließt die Lücke: Sie kapselt die MeshCentral-Weboberfläche
in einem eigenständigen, abgesicherten WebView mit Umschaltern für Desktop-/Mobil-Ansicht und
Erscheinungsbild, ausgelegt auf Foldables.

> Kein offizielles Projekt und in keiner Weise mit MeshCentral oder Anthropic verbunden.

---

## Funktionen

| | |
|---|---|
| **Desktop ⇄ Mobil** | Ein Tap in der Toolbar. Nutzt den offiziellen `?mobile=0/1`-Override von MeshCentral plus passenden User-Agent. |
| **Hell / Dunkel / System** | MeshCentral folgt über `prefers-color-scheme` automatisch dem App-Design. |
| **Foldable-tauglich** | Auf- und Zuklappen erzeugt die Activity nicht neu — Anmeldung und laufende Remote-Sitzung bleiben bestehen. |
| **Automatik-Ansicht** | Ab 600dp Breite wird die Desktop-Ansicht vorgeschlagen — nie erzwungen, immer per Rückfrage. |
| **Vollbild** | Blendet Toolbar und Systemleisten aus, Rückkehr über eine dezente Schaltfläche. |
| **Datei-Upload** | Über den System-Dateiauswahldialog, z. B. für den MeshCentral-Dateimanager. |
| **Vollbild** merken | Aus / letzten Zustand merken / immer im Vollbild starten — gilt global. |
| **App-Sperre** | Optionale biometrische Sperre mit Geräte-PIN als Rückfallebene. |

## Sicherheit

Die App ist als Fernwartungskonsole gedacht und entsprechend restriktiv gebaut:

- **Nur HTTPS.** Klartext ist per `networkSecurityConfig` und `usesCleartextTraffic="false"` ausgeschlossen.
- **Nur System-CAs.** Nachträglich installierte Benutzer-Zertifikate werden ignoriert.
- **Zertifikatsfehler brechen ab.** `onReceivedSslError` wird bewusst nicht überschrieben — es gibt keinen „Trotzdem fortfahren"-Knopf.
- **Navigationssperre.** Nur der konfigurierte Host wird im WebView geladen; alles andere geht an den Systembrowser. Ein untergeschobener Link kann die Sitzung also nicht mitnehmen.
- **Keine JavaScript-Bridge.** `addJavascriptInterface` wird nirgends verwendet.
- **Kein Backup.** Cookies und Einstellungen landen nicht im Cloud-Backup oder in der Geräteübertragung.
- **Screenshot-Sperre** (`FLAG_SECURE`) ist standardmäßig aktiv und abschaltbar.
- **Keine Berechtigungen** außer Internet, Netzwerkstatus und Biometrie. Kamera-, Mikrofon- und Standortanfragen der Webseite werden abgelehnt.
- Die Serveradresse steht **nicht im Quellcode**, sondern wird beim ersten Start abgefragt.

Details und die Anpassung für TLS-inspizierende Proxys: [SECURITY.de.md](SECURITY.de.md)

## Installation

Fertige APKs liegen unter [Releases](../../releases). Die APK ist selbstsigniert — beim ersten Mal
muss die Installation aus unbekannten Quellen erlaubt werden. Prüfsumme gegen die
mitgelieferte `.sha256`-Datei vergleichen.

Beim ersten Start wird die Serveradresse abgefragt, zum Beispiel `mesh.example.org`.

## Selbst bauen

Voraussetzungen: JDK 21, Android SDK mit Platform 36.

```bash
./gradlew :app:assembleDebug
```

Für einen signierten Release-Build eine `keystore.properties` im Projektstamm anlegen
(steht in `.gitignore`):

```properties
storeFile=release.jks
storePassword=...
keyAlias=meshdroid
keyPassword=...
```

```bash
./gradlew :app:assembleRelease
```

Ohne Keystore läuft der Release-Build durch, erzeugt aber eine unsignierte APK.

## CI

`.github/workflows/build.yml` baut bei jedem Push auf `main` und veröffentlicht bei einem
`v*`-Tag ein Release samt APK und Prüfsumme. Für signierte Builds werden diese
Repository-Secrets erwartet:

| Secret | Inhalt |
|---|---|
| `KEYSTORE_B64` | Keystore, base64-kodiert (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore-Passwort |
| `KEY_ALIAS` | Alias des Schlüssels |
| `KEY_PASSWORD` | Passwort des Schlüssels |

Fehlen die Secrets, baut die CI weiter — dann eben unsigniert.

## Technisches

Kotlin, AGP 8.13.2, Gradle 8.14.5, `compileSdk`/`targetSdk` 36, `minSdk` 29.

Die Desktop-/Mobil-Umschaltung stützt sich auf zwei Mechanismen in MeshCentrals `webserver.js`:
`isMobileBrowser()` prüft schlicht, ob der User-Agent die Zeichenfolge „mobile" enthält, und
`getRenderPage()` erlaubt mit `?mobile=1` bzw. `?mobile=0` eine ausdrückliche Überschreibung.
Die App setzt beides gleichzeitig — der Parameter wirkt auf die angeforderte Seite, der
User-Agent auf alle Folgeanfragen ohne Parameter.

## Bekannte Grenzen

- **Downloads aus dem MeshCentral-Dateimanager** werden als `blob:`-URL erzeugt. WebViews können
  solche URLs nicht an den Android-DownloadManager übergeben; die App weist darauf hin und
  bietet „Im Browser öffnen" an. Gewöhnliche `https`-Downloads (Agent-Installer und Ähnliches)
  funktionieren.
- Ein Wechsel des Erscheinungsbilds erzeugt die Activity neu. Die Sitzung bleibt über die
  Cookies erhalten, die Seite wird aber neu geladen.

## Haftungsausschluss

Nutzung auf eigenes Risiko. Diese Software wird „wie besehen" bereitgestellt, ohne jede
ausdrückliche oder stillschweigende Gewährleistung (siehe Apache-2.0-Lizenz). Es handelt
sich um einen eigenständigen, inoffiziellen Client ohne Verbindung zu, Billigung durch oder
Unterstützung vom MeshCentral-Projekt oder Anthropic. Für die Art der Verbindung und den
gewählten Server ist der Nutzer selbst verantwortlich. Der Autor übernimmt keine Haftung
für Schäden, Datenverlust oder Sicherheitsvorfälle, die aus der Nutzung entstehen.

## Lizenz

[Apache License 2.0](LICENSE) — dieselbe Lizenz wie MeshCentral.
