package com.ozkan.altyazi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    private val base = AppSettings(sourceLanguage = Language.ENGLISH, targetLanguage = Language.TURKISH)

    @Test
    fun `choosing the target as source swaps the pair`() {
        val s = base.withSource(Language.TURKISH)
        assertEquals(Language.TURKISH, s.sourceLanguage)
        assertEquals(Language.ENGLISH, s.targetLanguage)
    }

    @Test
    fun `choosing the source as target swaps the pair`() {
        val s = base.withTarget(Language.ENGLISH)
        assertEquals(Language.TURKISH, s.sourceLanguage)
        assertEquals(Language.ENGLISH, s.targetLanguage)
    }

    @Test
    fun `a non-latin target cannot become the source`() {
        val s = base.copy(targetLanguage = Language.JAPANESE)
        assertSame(s, s.swapped())
    }

    @Test
    fun `keyed lookup falls back to the default for unknown keys`() {
        assertEquals(Quality.BEST, keyedValueOf("nope", Quality.BEST))
        assertEquals(Quality.FAST, keyedValueOf("fast", Quality.BEST))
    }

    @Test
    fun `best quality is the default`() {
        assertEquals(Quality.BEST, AppSettings().quality)
    }

    @Test
    fun `only latin script languages are offered as game languages`() {
        assertTrue(Language.sources.all { it.ocrSupported })
        assertFalse(Language.JAPANESE in Language.sources)
    }

    @Test
    fun `reading controls default to the previous behavior`() {
        val d = AppSettings()
        assertFalse(d.queueSpeech)
        assertEquals(1.1f, d.speechRate, 0f)
        assertTrue(d.translationEnabled)
    }

    @Test
    fun `speech language follows the translation toggle`() {
        assertEquals(Language.TURKISH, base.speechLanguage)
        assertEquals(Language.ENGLISH, base.copy(translationEnabled = false).speechLanguage)
    }

    @Test
    fun `translation is not required when it is off`() {
        assertTrue(base.translationRequired)
        assertFalse(base.copy(translationEnabled = false).translationRequired)
    }

    @Test
    fun `speech rate is clamped, rounded and never non-finite`() {
        assertEquals(0.5f, AppSettings.coerceSpeechRate(0.1f), 0f)
        assertEquals(2.0f, AppSettings.coerceSpeechRate(5.0f), 0f)
        assertEquals(1.1f, AppSettings.coerceSpeechRate(Float.NaN), 0f)
        assertEquals(1.1f, AppSettings.coerceSpeechRate(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1.1f, AppSettings.coerceSpeechRate(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(1.5f, AppSettings.coerceSpeechRate(1.5f), 0f)
        assertEquals(1.3f, AppSettings.coerceSpeechRate(1.2999f), 0f)
    }

    @Test
    fun `speech rate slider steps land on tenths`() {
        val span = AppSettings.MAX_SPEECH_RATE - AppSettings.MIN_SPEECH_RATE
        assertEquals(span / 0.1f, (AppSettings.SPEECH_RATE_STEPS + 1).toFloat(), 0.01f)
    }

    @Test
    fun `settings that change what is spoken interrupt speech`() {
        val on = base.copy(speak = true, queueSpeech = true)
        assertTrue(on.copy(targetLanguage = Language.GERMAN).interruptsSpeech(on))
        assertTrue(on.copy(sourceLanguage = Language.FRENCH).interruptsSpeech(on))
        assertTrue(on.copy(translationEnabled = false).interruptsSpeech(on))
        assertTrue(on.copy(speak = false).interruptsSpeech(on))
        assertTrue(on.copy(queueSpeech = false).interruptsSpeech(on))
    }

    @Test
    fun `rate, turning speech on and unrelated settings do not interrupt speech`() {
        val on = base.copy(speak = true, queueSpeech = true)
        assertFalse(on.copy(speechRate = 1.8f).interruptsSpeech(on))
        val off = base.copy(speak = false, queueSpeech = false)
        assertFalse(off.copy(speak = true).interruptsSpeech(off))
        assertFalse(off.copy(queueSpeech = true).interruptsSpeech(off))
        assertFalse(on.copy(bold = false, textSizeSp = 50f).interruptsSpeech(on))
    }

    @Test
    fun `same source and target is fine when translation is off`() {
        val s = base.copy(targetLanguage = Language.ENGLISH, translationEnabled = false)
        assertEquals(Language.ENGLISH, s.speechLanguage)
        assertFalse(s.translationRequired)
    }
}
