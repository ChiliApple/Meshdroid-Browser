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
     * Skript, das VOR jedem Seitenskript laeuft (DocumentStartJavaScript).
     *
     * StylishUIs custom.js entscheidet einmalig beim Laden anhand von
     * `matchMedia('(pointer: coarse)')` und `navigator.maxTouchPoints`, ob es das
     * Geraet als mobil einstuft - und blendet dann die gesamte Navigation aus
     * (Zeile 7: `if (isMobile) return;`). Es gibt keinen Listener, der das spaeter
     * revidiert.
     *
     * Im Desktop-Modus melden wir daher `(pointer: coarse)` als nicht zutreffend
     * und `maxTouchPoints` als 0. StylishUI laeuft dann als Desktop durch und zeigt
     * die normale Navigation. Alle anderen Media-Queries - insbesondere
     * `prefers-color-scheme` fuer den Dark-Mode - bleiben unveraendert.
     *
     * Der Eingriff ist bewusst minimal und laeuft in eine Richtung; es entsteht
     * keine Bruecke von der Seite zur App.
     */
    val desktopSpoofScript: String = """
        (function() {
            try {
                var nativeMatchMedia = window.matchMedia.bind(window);
                window.matchMedia = function(query) {
                    if (typeof query === 'string' &&
                        (query.indexOf('pointer: coarse') !== -1 ||
                         query.indexOf('pointer:coarse') !== -1 ||
                         query.indexOf('any-pointer: coarse') !== -1 ||
                         query.indexOf('hover: none') !== -1)) {
                        var real = nativeMatchMedia(query);
                        return {
                            matches: false,
                            media: query,
                            onchange: null,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return false; }
                        };
                    }
                    return nativeMatchMedia(query);
                };
                try {
                    Object.defineProperty(navigator, 'maxTouchPoints', {
                        get: function() { return 0; },
                        configurable: true
                    });
                } catch (e) {}
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Liest den Sichtbarkeitszustand der MeshCentral-Navigationselemente aus.
     * Reines Auslesen fuer die Diagnose - veraendert nichts an der Seite.
     * Gibt eine JSON-Zeichenkette zurueck (oder "null", wenn Elemente fehlen).
     */
    val diagnosticsScript: String = """
        (function() {
            function info(id) {
                var el = document.getElementById(id);
                if (!el) { return { present: false }; }
                var cs = getComputedStyle(el);
                return {
                    present: true,
                    display: cs.display,
                    visibility: cs.visibility,
                    inlineStyle: el.getAttribute('style') || '',
                    parentDisplay: el.parentElement ? getComputedStyle(el.parentElement).display : ''
                };
            }
            return JSON.stringify({
                innerWidth: window.innerWidth,
                devicePixelRatio: window.devicePixelRatio,
                bodyClass: document.body ? document.body.className : '',
                mainMenu: info('MainMenuSpan'),
                leftbar: info('page_leftbar'),
                topbar: info('topbar'),
                columnL: info('column_l')
            });
        })();
    """.trimIndent()

    /**
     * Blendet MeshCentrals native obere Tab-Leiste (MainMenuSpan) ein.
     * Nutzt bewusst MeshCentrals eigene Elemente statt eines Nachbaus.
     * Wird nur wirksam, wenn die Diagnose zeigt, dass das der richtige Hebel ist.
     */
    /**
     * Schaltet MeshCentrals gestapeltes Menue (menu_stack) ab und blendet damit
     * die normale Navigation ein.
     *
     * menu_stack haengt an der localStorage-Variable webPageStackMenu. Der WebView
     * hat einen eigenen, leeren localStorage - deshalb steht die App standardmaessig
     * im gestapelten Modus, unabhaengig von der Bildschirmbreite. Aufgerufen wird
     * MeshCentrals eigene Funktion toggleStackMenu(1); nur falls die fehlt, wird die
     * Klasse direkt entfernt und der Zustand selbst persistiert.
     */
    fun toggleStackMenuScript(): String = """
        (function() {
            try {
                if (typeof toggleStackMenu === 'function') {
                    toggleStackMenu(1);
                    return 'native';
                }
            } catch (e) {}
            try {
                document.body.classList.remove('menu_stack');
                if (window.localStorage) {
                    localStorage.setItem('webPageStackMenu', 'false');
                }
                return 'fallback';
            } catch (e) { return 'error:' + e; }
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
