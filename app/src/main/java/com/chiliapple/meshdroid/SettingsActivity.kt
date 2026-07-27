package com.chiliapple.meshdroid

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.chiliapple.meshdroid.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        bindServerUrl()
        bindAccess()
        bindViewMode()
        bindFullscreen()
        bindDesktopWidth()
        bindThemeMode()
        bindSwitches()

        binding.clearSessionButton.setOnClickListener { confirmClearSession() }
        binding.versionText.text = getString(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
    }

    private fun bindServerUrl() {
        binding.serverInput.setText(prefs.serverUrl)
        binding.serverSaveButton.setOnClickListener {
            val normalized = WebUrl.normalizeServerUrl(binding.serverInput.text?.toString().orEmpty())
            if (normalized == null) {
                binding.serverInputLayout.error = getString(R.string.setup_invalid_url)
                return@setOnClickListener
            }
            binding.serverInputLayout.error = null
            if (normalized == prefs.serverUrl) {
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.serverUrl = normalized
            // Serverwechsel: alte Sitzungsdaten duerfen nicht bestehen bleiben.
            wipeSession()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }
    }

    private fun bindAccess() {
        binding.accessInput.setText(prefs.accessAuthHost)
        updateAccessCookieVisibility()

        binding.accessSaveButton.setOnClickListener {
            val raw = binding.accessInput.text?.toString().orEmpty().trim()
            if (raw.isEmpty()) {
                prefs.accessAuthHost = ""
                binding.accessInputLayout.error = null
                updateAccessCookieVisibility()
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val host = WebUrl.normalizeHost(raw)
            if (host == null) {
                binding.accessInputLayout.error = getString(R.string.settings_access_invalid)
                return@setOnClickListener
            }
            binding.accessInputLayout.error = null
            prefs.accessAuthHost = host
            binding.accessInput.setText(host)
            updateAccessCookieVisibility()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        binding.accessCookiesSwitch.isChecked = prefs.allowAccessCookies
        binding.accessCookiesSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.allowAccessCookies = checked
            Toast.makeText(this, R.string.settings_restart_hint, Toast.LENGTH_SHORT).show()
        }
    }

    /** Cookie-Schalter nur zeigen, wenn ein Access-Host gesetzt ist. */
    private fun updateAccessCookieVisibility() {
        val visible = prefs.hasAccessHost
        binding.accessCookiesSwitch.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        binding.accessCookiesSummary.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun bindViewMode() {
        binding.viewModeGroup.check(
            when (prefs.viewMode) {
                ViewMode.AUTO -> R.id.view_auto
                ViewMode.DESKTOP -> R.id.view_desktop
                ViewMode.MOBILE -> R.id.view_mobile
            }
        )
        binding.viewModeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.viewMode = when (checkedId) {
                R.id.view_desktop -> ViewMode.DESKTOP
                R.id.view_mobile -> ViewMode.MOBILE
                else -> ViewMode.AUTO
            }
        }
    }

    private fun bindFullscreen() {
        binding.fullscreenGroup.check(
            when (prefs.fullscreenMode) {
                FullscreenMode.OFF -> R.id.fs_off
                FullscreenMode.ALWAYS -> R.id.fs_always
                FullscreenMode.LAST -> R.id.fs_last
            }
        )
        binding.fullscreenGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.fullscreenMode = when (checkedId) {
                R.id.fs_off -> FullscreenMode.OFF
                R.id.fs_always -> FullscreenMode.ALWAYS
                else -> FullscreenMode.LAST
            }
        }
    }

    private fun bindDesktopWidth() {
        binding.desktopWidthGroup.check(
            when (prefs.desktopWidth) {
                1024 -> R.id.width_1024
                1440 -> R.id.width_1440
                else -> R.id.width_1280
            }
        )
        binding.desktopWidthGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.desktopWidth = when (checkedId) {
                R.id.width_1024 -> 1024
                R.id.width_1440 -> 1440
                else -> Prefs.DEFAULT_DESKTOP_WIDTH
            }
        }
    }

    private fun bindThemeMode() {
        binding.themeGroup.check(
            when (prefs.themeMode) {
                ThemeMode.SYSTEM -> R.id.theme_system
                ThemeMode.LIGHT -> R.id.theme_light
                ThemeMode.DARK -> R.id.theme_dark
            }
        )
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.theme_light -> ThemeMode.LIGHT
                R.id.theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            if (mode != prefs.themeMode) {
                prefs.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode.nightMode)
            }
        }
    }

    private fun bindSwitches() {
        binding.screenProtectionSwitch.isChecked = prefs.screenProtection
        binding.screenProtectionSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.screenProtection = checked
            Toast.makeText(this, R.string.settings_restart_hint, Toast.LENGTH_SHORT).show()
        }

        val lockAvailable = AppLock.isAvailable(this)
        binding.appLockSwitch.isEnabled = lockAvailable
        binding.appLockSwitch.isChecked = prefs.appLock && lockAvailable
        binding.appLockSummary.setText(
            if (lockAvailable) R.string.settings_app_lock_summary
            else R.string.settings_app_lock_unavailable
        )
        binding.appLockSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.appLock = checked
            if (!checked) AppLock.reset()
        }
    }

    private fun confirmClearSession() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_session_title)
            .setMessage(R.string.clear_session_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_clear) { _, _ ->
                wipeSession()
                Toast.makeText(this, R.string.session_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun wipeSession() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        AppLock.reset()
    }
}
