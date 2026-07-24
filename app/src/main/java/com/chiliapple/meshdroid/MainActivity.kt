package com.chiliapple.meshdroid

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.chiliapple.meshdroid.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private var defaultUserAgent: String = ""
    private var currentUrl: String = ""
    private var appliedDesktopMode: Boolean = false
    private var isFullscreen: Boolean = false
    private var pageLoadFailed: Boolean = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    // ---------------------------------------------------------------- Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (!prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        applyScreenProtection()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = Uri.parse(prefs.serverUrl).host ?: getString(R.string.app_name)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupInsets()
        setupWebView()
        setupBackHandling()

        binding.errorRetry.setOnClickListener { reload() }
        binding.showToolbarButton.setOnClickListener { setFullscreen(false) }
        binding.lockUnlock.setOnClickListener { requestUnlock() }

        appliedDesktopMode = resolveDesktopMode()
        applyUserAgent(appliedDesktopMode)

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
            currentUrl = savedInstanceState.getString(STATE_URL).orEmpty()
        }
        if (binding.webView.url == null) {
            loadHome()
        }
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.needsUnlock(prefs)) {
            showLock(true)
            requestUnlock()
        } else {
            showLock(false)
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onBackground()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
        outState.putString(STATE_URL, currentUrl)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (::binding.isInitialized) {
            binding.webView.stopLoading()
            binding.webView.destroy()
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Wird beim Auf- und Zuklappen ausgeloest. Die Activity wird dank
        // configChanges NICHT neu erzeugt, die Sitzung bleibt bestehen.
        if (prefs.viewMode != ViewMode.AUTO) return

        val suggested = newConfig.smallestScreenWidthDp >= LARGE_SCREEN_DP
        if (suggested == appliedDesktopMode) return

        val label = if (suggested) R.string.snack_switch_to_desktop else R.string.snack_switch_to_mobile
        Snackbar.make(binding.root, label, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_switch) { applyViewMode(suggested) }
            .show()
    }

    // ---------------------------------------------------------------- WebView

    private fun setupWebView() = with(binding.webView) {
        defaultUserAgent = WebSettings.getDefaultUserAgent(this@MainActivity)

        settings.apply {
            // MeshCentral ist eine JavaScript-Anwendung; ohne JS gibt es keine
            // Oberflaeche. Das Risiko wird ueber die Navigationssperre
            // (nur der konfigurierte Host), fehlende JS-Bruecke und
            // HTTPS-Zwang begrenzt.
            @Suppress("SetJavaScriptEnabled")
            javaScriptEnabled = true
            domStorageEnabled = true

            // Zoom per Geste erlauben, aber ohne die veralteten Bildschirm-Buttons.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true

            // --- Haertung -------------------------------------------------
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Kein addJavascriptInterface: die App stellt der Seite bewusst keinerlei
        // native Bruecke bereit.

        // MeshCentral bringt ein eigenes Dark-Theme mit und wertet
        // prefers-color-scheme aus. Algorithmisches Nachdunkeln wuerde das
        // doppelt invertieren und wird daher explizit unterbunden.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@with, false)
        }

        webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (WebUrl.isSameServer(url, prefs.serverUrl)) {
                    false // im WebView laden
                } else {
                    openExternally(request.url)
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                pageLoadFailed = false
                currentUrl = url
                binding.progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentUrl = url
                binding.progress.visibility = View.GONE
                if (!pageLoadFailed) {
                    binding.errorView.visibility = View.GONE
                    binding.webView.visibility = View.VISIBLE
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                currentUrl = url
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                pageLoadFailed = true
                binding.errorMessage.text = getString(R.string.error_body, error.description)
                binding.errorView.visibility = View.VISIBLE
                binding.webView.visibility = View.INVISIBLE
                binding.progress.visibility = View.GONE
            }

            // onReceivedSslError wird bewusst NICHT ueberschrieben. Damit bricht der
            // WebView bei jedem Zertifikatsfehler ab, statt ihn wegklickbar zu machen.
        }

        webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.progress = newProgress
                binding.progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // Die Konsole braucht weder Kamera noch Mikrofon; alles ablehnen.
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: android.webkit.GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, false, false)
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    filePathCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (url.startsWith("blob:", ignoreCase = true)) {
            // WebView kann blob-URLs nicht an den DownloadManager uebergeben.
            Snackbar.make(binding.root, R.string.download_blob_hint, Snackbar.LENGTH_LONG).show()
            return
        }
        if (!WebUrl.isSameServer(url, prefs.serverUrl)) {
            Snackbar.make(binding.root, R.string.download_foreign_blocked, Snackbar.LENGTH_LONG).show()
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                val name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                setTitle(name)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Snackbar.make(binding.root, R.string.download_started, Snackbar.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Snackbar.make(binding.root, R.string.download_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- Ansicht

    /** Ermittelt, ob aktuell die Desktop-Ansicht gelten soll. */
    private fun resolveDesktopMode(): Boolean = when (prefs.viewMode) {
        ViewMode.DESKTOP -> true
        ViewMode.MOBILE -> false
        ViewMode.AUTO -> resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP
    }

    private fun applyUserAgent(desktop: Boolean) {
        binding.webView.settings.userAgentString =
            if (desktop) WebUrl.desktopUserAgent(defaultUserAgent) else defaultUserAgent
    }

    /** Wendet den Ansichtsmodus an und laedt die aktuelle Seite neu. */
    private fun applyViewMode(desktop: Boolean) {
        appliedDesktopMode = desktop
        applyUserAgent(desktop)
        val base = currentUrl.ifBlank { prefs.serverUrl }
        binding.webView.loadUrl(WebUrl.withViewModeParam(base, desktop))
        invalidateOptionsMenu()
    }

    private fun toggleViewMode() {
        val next = !appliedDesktopMode
        // Explizite Auswahl gewinnt ab jetzt gegenueber der Automatik.
        prefs.viewMode = if (next) ViewMode.DESKTOP else ViewMode.MOBILE
        applyViewMode(next)
    }

    private fun cycleTheme() {
        val next = when (prefs.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        prefs.themeMode = next
        Toast.makeText(this, themeLabel(next), Toast.LENGTH_SHORT).show()
        // Loest ein Recreate aus; der WebView-Zustand kommt aus onSaveInstanceState
        // zurueck, die Anmeldung bleibt ueber die Cookies erhalten.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(next.nightMode)
    }

    private fun themeLabel(mode: ThemeMode): Int = when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

    private fun loadHome() {
        val url = WebUrl.withViewModeParam(prefs.serverUrl, appliedDesktopMode)
        binding.webView.loadUrl(url)
    }

    private fun reload() {
        pageLoadFailed = false
        binding.errorView.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        if (binding.webView.url == null) loadHome() else binding.webView.reload()
    }

    // ---------------------------------------------------------------- Vollbild

    private fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (enabled) {
            binding.appBar.visibility = View.GONE
            binding.showToolbarButton.visibility = View.VISIBLE
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            binding.appBar.visibility = View.VISIBLE
            binding.showToolbarButton.visibility = View.GONE
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------------------------------------------------------------- Sperre

    private fun showLock(locked: Boolean) {
        binding.lockView.visibility = if (locked) View.VISIBLE else View.GONE
        binding.webView.visibility = if (locked) View.INVISIBLE else View.VISIBLE
    }

    private fun requestUnlock() {
        if (!AppLock.isAvailable(this)) {
            showLock(false)
            return
        }
        AppLock.prompt(
            activity = this,
            onSuccess = { showLock(false) },
            onFailure = { showLock(true) }
        )
    }

    // ---------------------------------------------------------------- Menue

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_view_mode)?.apply {
            setIcon(if (appliedDesktopMode) R.drawable.ic_smartphone else R.drawable.ic_desktop)
            setTitle(if (appliedDesktopMode) R.string.action_mobile_view else R.string.action_desktop_view)
        }
        menu.findItem(R.id.action_fullscreen)?.setTitle(
            if (isFullscreen) R.string.action_exit_fullscreen else R.string.action_fullscreen
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_view_mode -> { toggleViewMode(); true }
        R.id.action_theme -> { cycleTheme(); true }
        R.id.action_reload -> { reload(); true }
        R.id.action_home -> { loadHome(); true }
        R.id.action_fullscreen -> { setFullscreen(!isFullscreen); invalidateOptionsMenu(); true }
        R.id.action_open_browser -> { openExternally(Uri.parse(currentUrl.ifBlank { prefs.serverUrl })); true }
        R.id.action_clear_session -> { confirmClearSession(); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmClearSession() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_session_title)
            .setMessage(R.string.clear_session_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_clear) { _, _ -> clearSession() }
            .show()
    }

    private fun clearSession() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        binding.webView.clearHistory()
        binding.webView.clearCache(true)
        binding.webView.clearFormData()
        AppLock.reset()
        loadHome()
        Snackbar.make(binding.root, R.string.session_cleared, Snackbar.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- Hilfen

    private fun openExternally(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme in BLOCKED_SCHEMES) {
            Snackbar.make(binding.root, R.string.link_blocked, Snackbar.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.no_app_for_link, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun applyScreenProtection() {
        if (prefs.screenProtection) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars: Insets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isFullscreen -> setFullscreen(false)
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    companion object {
        private const val STATE_URL = "state_current_url"
        private const val LARGE_SCREEN_DP = 600
        private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "intent")
    }
}
