package com.chiliapple.meshdroid

import android.app.Application
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Gespeicherte Theme-Auswahl moeglichst frueh anwenden, damit die erste
        // Activity bereits im richtigen Modus startet.
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode.nightMode)

        // Remote-Debugging niemals in Release-Builds aktivieren: es erlaubt jedem
        // Prozess mit adb-Zugriff das Auslesen der laufenden Session.
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Eigenes WebView-Datenverzeichnis: trennt die Sitzungsdaten sauber ab.
        runCatching { WebView.setDataDirectorySuffix("meshdroid") }
    }
}
