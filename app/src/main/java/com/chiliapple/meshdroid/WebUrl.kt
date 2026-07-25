package com.chiliapple.meshdroid

import android.net.Uri

/**
 * Hilfsfunktionen fuer User-Agent und URL-Aufbau.
 *
 * Hintergrund Desktop/Mobil-Umschaltung:
 *
 * MeshCentral entscheidet serverseitig ueber die auszuliefernde Handlebars-Vorlage
 * (`default.handlebars` vs. `default-mobile.handlebars`). Die Erkennung erfolgt in
 * `webserver.js`:
 *
 *   - `isMobileBrowser(req)` prueft schlicht, ob der User-Agent die Zeichenfolge
 *     "mobile" enthaelt.
 *   - `getRenderPage()` erlaubt zusaetzlich eine explizite Ueberschreibung per
 *     Query-Parameter: `?mobile=1` erzwingt die Mobil-, `?mobile=0` die
 *     Desktop-Ansicht.
 *
 * Die App nutzt beide Wege gleichzeitig: Der Query-Parameter wirkt sofort auf die
 * angeforderte Seite, der User-Agent sorgt dafuer, dass auch Folgeanfragen ohne
 * Parameter (Redirects nach Login, interne Links) in der gewuenschten Ansicht
 * bleiben.
 */
object WebUrl {

    private const val PARAM_MOBILE = "mobile"

    /**
     * Baut aus dem System-User-Agent des WebViews einen Desktop-User-Agent.
     *
     * Es wird bewusst eine vollstaendig neutrale Desktop-Kennung erzeugt statt nur
     * das Token "Mobile" zu entfernen: Manche Geraetemodelle tragen "mobile" im
     * Modellnamen, was die serverseitige Substring-Pruefung sonst weiter ausloesen
     * wuerde.
     */
    fun desktopUserAgent(defaultUa: String): String {
        val chromeVersion = Regex("Chrome/([0-9.]+)").find(defaultUa)?.groupValues?.get(1)
            ?: "120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    /**
     * Setzt bzw. ersetzt den `mobile`-Query-Parameter in [url].
     * Fragment und alle uebrigen Parameter bleiben erhalten.
     */
    fun withViewModeParam(url: String, desktop: Boolean): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (uri.scheme == null || uri.host == null) return url

        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name.equals(PARAM_MOBILE, ignoreCase = true)) continue
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }
        builder.appendQueryParameter(PARAM_MOBILE, if (desktop) "0" else "1")
        return builder.build().toString()
    }

    /**
     * Baut das Skript, das die Seite auf eine feste Viewport-Breite zwingt.
     *
     * MeshCentral liefert `width=device-width` aus. Ein aufgeklapptes Foldable
     * kommt damit auf rund 690 CSS-Pixel, worauf MeshCentral seine
     * Navigationsleiste einklappt - unabhaengig davon, welche Vorlage der Server
     * schickt. Genau deshalb wirkte der Desktop-Umschalter zuvor folgenlos:
     * User-Agent und `?mobile=0` bestimmen die Vorlage, nicht die Rechenbreite
     * des WebViews.
     *
     * Dieselbe Technik nutzt Chrome fuer "Desktopseite anfordern".
     *
     * Das Skript wird ausschliesslich hier erzeugt, laeuft in eine Richtung und
     * gibt nichts an die App zurueck - es gibt weiterhin keine JavaScript-Bruecke.
     */
    fun viewportScript(widthPx: Int): String = """
        (function() {
            var apply = function() {
                var head = document.head || document.getElementsByTagName('head')[0];
                if (!head) { return; }
                var meta = head.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.setAttribute('name', 'viewport');
                    head.appendChild(meta);
                }
                var want = 'width=$widthPx';
                if (meta.getAttribute('content') !== want) {
                    meta.setAttribute('content', want);
                }
            };
            apply();
            document.addEventListener('DOMContentLoaded', apply);
            window.addEventListener('load', apply);
        })();
    """.trimIndent()

    /**
     * Normalisiert eine Benutzereingabe zu einer https-Basis-URL.
     * Gibt null zurueck, wenn daraus keine gueltige https-URL mit Host wird.
     */
    fun normalizeServerUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> return null // kein Klartext
            trimmed.contains("://") -> return null                          // kein anderes Schema
            else -> "https://$trimmed"
        }

        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (host.isBlank() || !host.contains('.')) return null
        if (!uri.userInfo.isNullOrEmpty()) return null // keine Credentials in der URL

        return withScheme
    }

    /**
     * Prueft, ob [url] zum konfigurierten Server gehoert. Nur solche URLs duerfen im
     * eingebetteten WebView geoeffnet werden; alles andere geht an den System-Browser.
     *
     * Erlaubt sind ausschliesslich https und exakt der konfigurierte Host inklusive
     * Port. Subdomains gelten bewusst als fremd.
     */
    fun isSameServer(url: String, serverUrl: String): Boolean {
        val target = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val base = runCatching { Uri.parse(serverUrl) }.getOrNull() ?: return false

        if (!target.scheme.equals("https", ignoreCase = true)) return false
        val targetHost = target.host ?: return false
        val baseHost = base.host ?: return false
        if (!targetHost.equals(baseHost, ignoreCase = true)) return false

        val targetPort = if (target.port == -1) 443 else target.port
        val basePort = if (base.port == -1) 443 else base.port
        return targetPort == basePort
    }
}
