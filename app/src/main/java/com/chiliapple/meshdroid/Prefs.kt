package com.chiliapple.meshdroid

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Ansichtsmodus der Weboberflaeche.
 *
 * AUTO orientiert sich an der aktuellen Fensterbreite: ab 600dp (aufgeklapptes
 * Foldable, Tablet, Multi-Window gross) wird die Desktop-Ansicht verwendet,
 * darunter die Mobil-Ansicht.
 */
enum class ViewMode {
    AUTO, DESKTOP, MOBILE;

    companion object {
        fun fromKey(key: String?): ViewMode = entries.firstOrNull { it.name == key } ?: AUTO
    }
}

/**
 * Startverhalten des Vollbildmodus.
 * OFF  - startet normal, Vollbild ist eine reine Sitzungsaktion.
 * LAST - der letzte Zustand wird gemerkt und beim Start wiederhergestellt.
 * ALWAYS - startet immer im Vollbild.
 */
enum class FullscreenMode {
    OFF, LAST, ALWAYS;

    companion object {
        fun fromKey(key: String?): FullscreenMode = entries.firstOrNull { it.name == key } ?: LAST
    }
}

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Basis-URL des MeshCentral-Servers, z. B. "https://mesh.example.org". Leer = nicht eingerichtet. */
    var serverUrl: String
        get() = sp.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = sp.edit().putString(KEY_SERVER_URL, value).apply()

    /**
     * Optionaler Cloudflare-Access-Host (Team-Domain, z. B. "team.cloudflareaccess.com").
     * Leer = kein Access, altes Verhalten. Nur der Host, ohne Schema.
     */
    var accessAuthHost: String
        get() = sp.getString(KEY_ACCESS_HOST, "").orEmpty().trim()
        set(value) = sp.edit().putString(KEY_ACCESS_HOST, value.trim()).apply()

    /**
     * Ob Third-Party-Cookies zugelassen werden. Nur fuer den Access-OAuth-Redirect
     * ueber zwei Hosts relevant. Standardmaessig aus (Haertung). Greift ohnehin nur,
     * wenn ein Access-Host gesetzt ist.
     */
    var allowAccessCookies: Boolean
        get() = sp.getBoolean(KEY_ACCESS_COOKIES, false)
        set(value) = sp.edit().putBoolean(KEY_ACCESS_COOKIES, value).apply()

    val hasAccessHost: Boolean
        get() = accessAuthHost.isNotBlank()

    var viewMode: ViewMode
        get() = ViewMode.fromKey(sp.getString(KEY_VIEW_MODE, null))
        set(value) = sp.edit().putString(KEY_VIEW_MODE, value.name).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(sp.getString(KEY_THEME_MODE, null))
        set(value) = sp.edit().putString(KEY_THEME_MODE, value.name).apply()

    /**
     * Virtuelle Seitenbreite in CSS-Pixeln fuer die Desktop-Ansicht.
     *
     * Der WebView rechnet ohne Zutun mit `width=device-width` - auf einem
     * aufgeklappten Foldable sind das trotz hoher Aufloesung nur rund 690
     * CSS-Pixel, weil die Pixeldichte hineinrechnet. MeshCentral klappt seine
     * Navigationsleiste unterhalb von etwa 1000px weg. Die Desktop-Ansicht
     * ueberschreibt daher das Viewport-Meta auf diesen Wert.
     */
    var desktopWidth: Int
        get() = sp.getInt(KEY_DESKTOP_WIDTH, DEFAULT_DESKTOP_WIDTH)
        set(value) = sp.edit().putInt(KEY_DESKTOP_WIDTH, value).apply()

    /** Startverhalten des Vollbildmodus (Aus / Merken / Immer). */
    var fullscreenMode: FullscreenMode
        get() = FullscreenMode.fromKey(sp.getString(KEY_FULLSCREEN_MODE, null))
        set(value) = sp.edit().putString(KEY_FULLSCREEN_MODE, value.name).apply()

    /** Zuletzt aktiver Vollbildzustand - nur fuer den Modus "Merken" relevant. */
    var lastFullscreen: Boolean
        get() = sp.getBoolean(KEY_LAST_FULLSCREEN, false)
        set(value) = sp.edit().putBoolean(KEY_LAST_FULLSCREEN, value).apply()

    /**
     * Ob beim Start in den Vollbild gewechselt werden soll - aus Modus und
     * gemerktem Zustand abgeleitet.
     */
    val startInFullscreen: Boolean
        get() = when (fullscreenMode) {
            FullscreenMode.OFF -> false
            FullscreenMode.ALWAYS -> true
            FullscreenMode.LAST -> lastFullscreen
        }

    /** FLAG_SECURE: blockiert Screenshots und Vorschau in der App-Uebersicht. */
    var screenProtection: Boolean
        get() = sp.getBoolean(KEY_SCREEN_PROTECTION, true)
        set(value) = sp.edit().putBoolean(KEY_SCREEN_PROTECTION, value).apply()

    /** Biometrische App-Sperre beim Start und nach laengerer Pause. */
    var appLock: Boolean
        get() = sp.getBoolean(KEY_APP_LOCK, false)
        set(value) = sp.edit().putBoolean(KEY_APP_LOCK, value).apply()

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank()

    companion object {
        private const val FILE = "meshdroid_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SCREEN_PROTECTION = "screen_protection"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_ACCESS_HOST = "access_auth_host"
        private const val KEY_ACCESS_COOKIES = "allow_access_cookies"
        private const val KEY_FULLSCREEN_MODE = "fullscreen_mode"
        private const val KEY_LAST_FULLSCREEN = "last_fullscreen"
        private const val KEY_DESKTOP_WIDTH = "desktop_width"

        const val DEFAULT_DESKTOP_WIDTH = 1280
        val DESKTOP_WIDTHS = intArrayOf(1024, 1280, 1440)
    }
}
