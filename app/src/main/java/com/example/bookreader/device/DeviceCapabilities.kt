package com.example.bookreader.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import android.speech.tts.TextToSpeech

/**
 * Единая точка проверки возможностей устройства.
 * Звуковые функции (TTS, Ambient) будут спрашивать её и скрываться на читалках без динамиков.
 */
class DeviceCapabilities(private val ctx: Context) {
    val hasAudioOutput: Boolean
        get() = runCatching {
            val audio = ctx.getSystemService(AudioManager::class.java)
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT) &&
                audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.isNotEmpty() == true
        }.getOrDefault(false)

    val hasTtsEngine: Boolean
        get() = runCatching {
            ctx.packageManager
                .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .isNotEmpty()
        }.getOrDefault(false)

    val canPlayAudio: Boolean get() = hasAudioOutput
    val canUseTts: Boolean get() = hasAudioOutput && hasTtsEngine
}

/** Абстракция яркости: для Boox и других читалок позже добавятся свои реализации. */
interface BrightnessController {
    fun current(): Float
    fun set(value: Float)
    fun reset()
}

/** Телефон: яркость только окна читалки, без разрешений. */
class PhoneBrightnessController(private val activity: Activity) : BrightnessController {
    override fun current(): Float {
        val b = activity.window.attributes.screenBrightness
        if (b >= 0f) return b
        return runCatching {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    override fun set(value: Float) {
        val a = activity.window.attributes
        a.screenBrightness = value.coerceIn(0.02f, 1f)
        activity.window.attributes = a
    }

    override fun reset() {
        val a = activity.window.attributes
        a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = a
    }
}
