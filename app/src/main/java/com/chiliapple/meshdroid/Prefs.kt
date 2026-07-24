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

    var viewMode: ViewMode
        get() = ViewMode.fromKey(sp.getString(KEY_VIEW_MODE, null))
        set(value) = sp.edit().putString(KEY_VIEW_MODE, value.name).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(sp.getString(KEY_THEME_MODE, null))
        set(value) = sp.edit().putString(KEY_THEME_MODE, value.name).apply()

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
    }
}
