package com.chiliapple.meshdroid

import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Biometrische App-Sperre (opt-in).
 *
 * Gesperrt wird beim Kaltstart sowie nach [GRACE_MILLIS] im Hintergrund. Als
 * Fallback ist die Geraete-PIN/-Muster zugelassen, damit man sich nicht
 * aussperrt, wenn die Biometrie einmal nicht greift.
 */
object AppLock {

    private const val GRACE_MILLIS = 60_000L

    private var lastUnlockElapsed: Long = 0L
    private var unlocked: Boolean = false

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isAvailable(activity: AppCompatActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun needsUnlock(prefs: Prefs): Boolean {
        if (!prefs.appLock) return false
        if (!unlocked) return true
        return SystemClock.elapsedRealtime() - lastUnlockElapsed > GRACE_MILLIS
    }

    /** Muss aufgerufen werden, wenn die App in den Hintergrund geht. */
    fun onBackground() {
        lastUnlockElapsed = SystemClock.elapsedRealtime()
    }

    fun reset() {
        unlocked = false
        lastUnlockElapsed = 0L
    }

    fun prompt(
        activity: AppCompatActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    lastUnlockElapsed = SystemClock.elapsedRealtime()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_title))
            .setSubtitle(activity.getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }
}
