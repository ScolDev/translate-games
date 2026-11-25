package com.ozkan.altyazi.data

import androidx.annotation.StringRes
import com.ozkan.altyazi.R

/** Enums are persisted by a stable [key], so R8 renaming can never corrupt stored settings. */
interface Keyed { val key: String }

inline fun <reified T> keyedValueOf(key: String?, default: T): T where T : Enum<T>, T : Keyed =
    enumValues<T>().firstOrNull { it.key == key } ?: default

/**
 * Translation languages. [key] is the ML Kit language code.
 * [ocrSupported] marks Latin-script languages the bundled OCR model can read, i.e. valid game languages.
 */
enum class Language(override val key: String, @StringRes val label: Int, val ocrSupported: Boolean) : Keyed {
    ENGLISH("en", R.string.lang_en, true),
    TURKISH("tr", R.string.lang_tr, true),
    GERMAN("de", R.string.lang_de, true),
    FRENCH("fr", R.string.lang_fr, true),
    SPANISH("es", R.string.lang_es, true),
    ITALIAN("it", R.string.lang_it, true),
    PORTUGUESE("pt", R.string.lang_pt, true),
    DUTCH("nl", R.string.lang_nl, true),
    POLISH("pl", R.string.lang_pl, true),
    RUSSIAN("ru", R.string.lang_ru, false),
    ARABIC("ar", R.string.lang_ar, false),
    JAPANESE("ja", R.string.lang_ja, false),
    KOREAN("ko", R.string.lang_ko, false),
    CHINESE("zh", R.string.lang_zh, false);

    companion object {
        val sources: List<Language> get() = entries.filter { it.ocrSupported }
        val targets: List<Language> get() = entries
    }
}

enum class SubtitleFont(override val key: String, @StringRes val label: Int) : Keyed {
    MODERN("modern", R.string.font_modern),
    MEDIUM("medium", R.string.font_medium),
    CONDENSED("condensed", R.string.font_condensed),
    SERIF("serif", R.string.font_serif),
    MONO("mono", R.string.font_mono),
    CASUAL("casual", R.string.font_casual),
}

enum class PanelBackground(override val key: String, @StringRes val label: Int, val alpha: Float) : Keyed {
    NONE("none", R.string.bg_none, 0f),
    SOFT("soft", R.string.bg_soft, 0.55f),
    SOLID("solid", R.string.bg_solid, 0.92f),
}

/**
 * Speed/quality trade-off. Each step reads the subtitle over more frames and votes on the result;
 * every step except [INSTANT] also runs a second, high-contrast OCR pass per frame.
 * [approxDelayMs] is a typical end-to-end delay on a current tablet.
 */
enum class Quality(
    override val key: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
    val requiredReads: Int,
    val dualPass: Boolean,
    val approxDelayMs: Int,
) : Keyed {
    INSTANT("instant", R.string.quality_instant, R.string.quality_instant_desc, 1, false, 150),
    FAST("fast", R.string.quality_fast, R.string.quality_fast_desc, 2, true, 300),
    BALANCED("balanced", R.string.quality_balanced, R.string.quality_balanced_desc, 3, true, 420),
    BEST("best", R.string.quality_best, R.string.quality_best_desc, 4, true, 550),
    MAX("max", R.string.quality_max, R.string.quality_max_desc, 6, true, 800),
}

/**
 * Optional online translation with the user's own key (CouchSubs' engines). Text leaves the tablet
 * only when one of these is chosen; [NONE] keeps everything offline.
 */
enum class OnlineEngine(override val key: String, @StringRes val label: Int, val aiProvider: String?) : Keyed {
    NONE("none", R.string.engine_none, null),
    GEMINI("gemini", R.string.engine_gemini, "gemini"),
    DEEPL("deepl", R.string.engine_deepl, null),
    OPENAI("openai", R.string.engine_openai, "openai"),
    CLAUDE("claude", R.string.engine_claude, "claude"),
}

/** Subtitle size on the TV (CouchSubs TV app: pixels on a 1080p screen). */
enum class TvTextSize(override val key: String, @StringRes val label: Int, val px: Int) : Keyed {
    SMALL("small", R.string.tv_size_small, 36),
    MEDIUM("medium", R.string.tv_size_medium, 46),
    LARGE("large", R.string.tv_size_large, 58),
    HUGE("huge", R.string.tv_size_huge, 72),
}

/** Where the translation sits relative to the game's own English subtitle on the TV. */
enum class TvPlacement(override val key: String, @StringRes val label: Int) : Keyed {
    COVER("cover", R.string.tv_place_cover),
    ABOVE("above", R.string.tv_place_above),
    BELOW("below", R.string.tv_place_below),
}

/** Normalized (0..1) subtitle region relative to the camera frame or the streamed picture. */
data class Roi(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val DEFAULT = Roi(0.08f, 0.70f, 0.92f, 0.96f)
        /** Where games put subtitles in the console's own picture: the lower fifth, full width. */
        val STREAM_DEFAULT = Roi(0.05f, 0.74f, 0.95f, 0.97f)
        const val MIN_SIZE = 0.06f
    }
}

/** Where the subtitles are read from; asked on every launch. */
enum class FrameSource { CAMERA, REMOTE_PLAY }

data class AppSettings(
    val textSizeSp: Float = 40f,
    val font: SubtitleFont = SubtitleFont.MODERN,
    val bold: Boolean = true,
    val textColor: Long = 0xFFFFFFFF,
    val background: PanelBackground = PanelBackground.SOFT,
    val showOriginal: Boolean = true,
    val speak: Boolean = false,
    /** Read-aloud appends lines to the speech queue instead of cutting the current speech. */
    val queueSpeech: Boolean = false,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
    /** Off = passthrough: the source text is shown and spoken as is, with no translation work. */
    val translationEnabled: Boolean = true,
    /** Optional offline AI translation with an on-device LLM (off by default). */
    val aiTranslation: Boolean = false,
    /** Ready-made game translations (CouchSubs packs), used first for English → Turkish. */
    val packsEnabled: Boolean = true,
    val onlineEngine: OnlineEngine = OnlineEngine.NONE,
    /** Model id for Gemini / OpenAI / Claude; null = the provider's first (recommended) model. */
    val onlineModel: String? = null,
    /** API keys by [OnlineEngine.key]; kept in app-private storage only. */
    val apiKeys: Map<String, String> = emptyMap(),
    /** Also show the translations on the TV, over the game (CouchSubs webOS app). */
    val tvOutput: Boolean = false,
    /** Address of the LG TV the TV app was installed on. */
    val tvHost: String? = null,
    val tvTextSize: TvTextSize = TvTextSize.MEDIUM,
    val tvPlacement: TvPlacement = TvPlacement.COVER,
    val sourceLanguage: Language = Language.ENGLISH,
    val targetLanguage: Language = Language.TURKISH,
    val quality: Quality = Quality.BEST,
    val roi: Roi = Roi.DEFAULT,
    /** Subtitle region in the PS5 Remote Play picture (kept apart from the camera's). */
    val streamRoi: Roi = Roi.STREAM_DEFAULT,
    val cameraId: String? = null,
) {
    val onlineKey: String? get() = apiKeys[onlineEngine.key]?.takeIf { it.isNotBlank() }

    /** Pack and CouchSubs translation layers cover English → Turkish, the pair they were built for. */
    val gameTranslationPair: Boolean get() = sourceLanguage == Language.ENGLISH && targetLanguage == Language.TURKISH

    /** Language the read-aloud voice speaks: the translation, or the source text in passthrough. */
    val speechLanguage: Language get() = if (translationEnabled) targetLanguage else sourceLanguage

    val translationRequired: Boolean get() = translationEnabled

    /** True when moving from [previous] to this must stop the current speech and empty the queue. */
    fun interruptsSpeech(previous: AppSettings): Boolean =
        speechLanguage != previous.speechLanguage ||
            sourceLanguage != previous.sourceLanguage || targetLanguage != previous.targetLanguage ||
            translationEnabled != previous.translationEnabled ||
            (previous.speak && !speak) || (previous.queueSpeech && !queueSpeech)

    override fun toString(): String = "AppSettings(engine=${onlineEngine.key}, packs=$packsEnabled, ai=$aiTranslation, " +
        "${sourceLanguage.key}->${targetLanguage.key})" // never print API keys

    fun roiFor(source: FrameSource?): Roi = if (source == FrameSource.REMOTE_PLAY) streamRoi else roi

    fun withRoi(source: FrameSource?, value: Roi): AppSettings =
        if (source == FrameSource.REMOTE_PLAY) copy(streamRoi = value) else copy(roi = value)

    /** Picks a new source; if it collides with the target, the two are swapped. */
    fun withSource(language: Language): AppSettings =
        if (language == targetLanguage) copy(sourceLanguage = language, targetLanguage = sourceLanguage)
        else copy(sourceLanguage = language)

    /** Picks a new target; if it collides with the source, the two are swapped (when the old target can be OCR'd). */
    fun withTarget(language: Language): AppSettings = when {
        language != sourceLanguage -> copy(targetLanguage = language)
        targetLanguage.ocrSupported -> copy(sourceLanguage = targetLanguage, targetLanguage = language)
        else -> this
    }

    fun swapped(): AppSettings =
        if (targetLanguage.ocrSupported) copy(sourceLanguage = targetLanguage, targetLanguage = sourceLanguage) else this

    companion object {
        const val MIN_TEXT_SP = 20f
        const val MAX_TEXT_SP = 80f

        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
        const val DEFAULT_SPEECH_RATE = 1.1f
        /** Slider steps between the bounds, so every position is a multiple of 0.1. */
        const val SPEECH_RATE_STEPS = 14

        /** Clamps to the allowed range and rounds to 0.1; a non-finite value falls back to the default. */
        fun coerceSpeechRate(value: Float): Float =
            if (!value.isFinite()) DEFAULT_SPEECH_RATE
            else Math.round(value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE) * 10f) / 10f

        /** Soft tones chosen for long reading sessions next to a TV. */
        val TEXT_COLORS: List<Long> = listOf(
            0xFFFFFFFF, // white
            0xFFFFE58A, // subtitle yellow
            0xFFFFC94D, // amber
            0xFF8BE9FD, // cyan
            0xFFA6F0B5, // mint
            0xFFFFB38A, // peach
            0xFFFF9EC7, // pink
            0xFFC7B8FF, // lavender
        )
    }
}
