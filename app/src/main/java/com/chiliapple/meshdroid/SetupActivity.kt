package com.chiliapple.meshdroid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.chiliapple.meshdroid.databinding.ActivitySetupBinding

/**
 * Ersteinrichtung: Abfrage der Server-URL.
 *
 * Die URL wird bewusst nicht im Quellcode hinterlegt, damit das oeffentliche
 * Repository keine konkrete Serveradresse enthaelt.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        val prefs = Prefs(this)
        binding.serverInput.setText(prefs.serverUrl)

        binding.saveButton.setOnClickListener {
            val normalized = WebUrl.normalizeServerUrl(binding.serverInput.text?.toString().orEmpty())
            if (normalized == null) {
                binding.serverInputLayout.error = getString(R.string.setup_invalid_url)
                return@setOnClickListener
            }
            binding.serverInputLayout.error = null
            prefs.serverUrl = normalized

            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }
}
