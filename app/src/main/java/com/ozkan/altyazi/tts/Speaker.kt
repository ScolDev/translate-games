package com.ozkan.altyazi.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ozkan.altyazi.core.SpeechQueuePolicy
import com.ozkan.altyazi.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Optional read-aloud using the system TTS engine (offline when the voice is installed).
 * The engine is only created once the user turns the feature on.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var engineReady = false
    private var locale: Locale = Locale.forLanguageTag("tr-TR")
    private var voiceReady = false
    private val policy = SpeechQueuePolicy()
    private var queueMode = false
    private var rate = AppSettings.DEFAULT_SPEECH_RATE
    private var utteranceSeq = 0L

    /** null = unknown yet, false = no voice for the selected language */
    private val _voiceAvailable = MutableStateFlow<Boolean?>(null)
    val voiceAvailable: StateFlow<Boolean?> = _voiceAvailable.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (!enabled) { stop(); return }
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (engineReady) applyLocale() else _voiceAvailable.value = false
        }
    }

    fun setLanguage(languageTag: String) {
        locale = Locale.forLanguageTag(languageTag)
        if (engineReady) applyLocale()
    }

    /** Queue mode appends lines instead of cutting the current speech. Main thread only. */
    fun setQueueMode(enabled: Boolean) {
        queueMode = enabled
    }

    /** Applies to utterances spoken from now on; already queued ones keep their rate. */
    fun setRate(value: Float) {
        rate = value
        tts?.setSpeechRate(rate)
    }

    fun speak(text: String) {
        if (!voiceReady) return
        val utterance = policy.next(text, queueMode) ?: return
        val queue = if (utterance.mode == SpeechQueuePolicy.Mode.ADD) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        tts?.speak(utterance.text, queue, null, "subtitle-${utteranceSeq++}")
    }

    /** Stops the current speech, drops everything queued and forgets the last spoken text. */
    fun stop() {
        tts?.stop()
        policy.reset()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        engineReady = false
        voiceReady = false
    }

    private fun applyLocale() {
        val engine = tts ?: return
        voiceReady = engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        engine.setSpeechRate(rate)
        _voiceAvailable.value = voiceReady
    }
}
