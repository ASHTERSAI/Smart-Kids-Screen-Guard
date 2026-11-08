package id.smartkids.screenguard.notification

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

class SoundAndTtsHelper(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var lastSpokenMillis = 0L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val indoLocale = Locale("id", "ID")
            val result = tts?.setLanguage(indoLocale)
            isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setPitch(1.15f) // Suara ramah anak
            tts?.setSpeechRate(0.95f)
        }
    }

    fun playGentleAlertAndSpeak(message: String, volume: Float = 0.8f) {
        val now = System.currentTimeMillis()
        if (now - lastSpokenMillis < 6000L) return
        lastSpokenMillis = now

        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, (volume * 80).toInt().coerceIn(20, 100))
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        } catch (_: Exception) {
        }

        if (isReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "KID_GUARD_UTTERANCE")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
