package com.ozkan.altyazi.translate

import android.content.Context
import com.ozkan.altyazi.couchsubs.core.GamePackLibrary
import com.ozkan.altyazi.couchsubs.core.PackLookup
import com.ozkan.altyazi.couchsubs.core.PackMatch
import com.ozkan.altyazi.couchsubs.core.TextTranslator
import com.ozkan.altyazi.couchsubs.packs.HttpPackServer
import com.ozkan.altyazi.couchsubs.packs.PackStore
import com.ozkan.altyazi.couchsubs.translation.AiModel
import com.ozkan.altyazi.couchsubs.translation.AiProvider
import com.ozkan.altyazi.couchsubs.translation.AiTranslator
import com.ozkan.altyazi.couchsubs.translation.DeepLTranslator
import com.ozkan.altyazi.couchsubs.translation.FreeLineTranslator
import com.ozkan.altyazi.couchsubs.translation.Glossary
import com.ozkan.altyazi.couchsubs.translation.KeyCheck
import com.ozkan.altyazi.couchsubs.translation.SentenceTranslator
import com.ozkan.altyazi.couchsubs.translation.OnlineKeyState
import com.ozkan.altyazi.couchsubs.translation.TranslationCache
import com.ozkan.altyazi.data.AppSettings
import com.ozkan.altyazi.data.OnlineEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where a shown translation came from. */
enum class LineOrigin { PACK, ONLINE, AI, FAST }

data class GameTranslationResult(val text: String, val origin: LineOrigin, val packId: String? = null)

/** Pack status for the settings screen. */
data class PackInfo(val id: String, val title: String, val lines: Int, val builtIn: Boolean)

/**
 * English → Turkish translation layers taken from CouchSubs (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0):
 *
 * 1. **Game packs**: complete, reviewed Turkish translations of popular games keyed by a hash of the
 *    English line, with OCR-tolerant matching; the game being played is recognized from the lines.
 * 2. **Online engine** with the user's own key (Gemini, DeepL, OpenAI, Claude), optional.
 * 3. **ML Kit** wrapped in CouchSubs' sentence layer: sentence splitting with context for short
 *    sentences, a game glossary, idiom rewriting, informal address fix-up and a persistent cache.
 *
 * The on-device Gemma translation (ours) sits between 2 and 3 in the view model.
 */
class GameTranslation(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val translationsDir = File(appContext.filesDir, "Translations")

    private val packStore = PackStore(File(appContext.filesDir, "packs-remote"), HttpPackServer(PACKS_URL))

    private val library = GamePackLibrary(
        bundled = {
            (appContext.assets.list("packs") ?: emptyArray()).map { name ->
                name to { appContext.assets.open("packs/$name").use { it.readBytes() } }
            }
        },
        userDirectory = packStore.directory,
    )

    private val glossary: Glossary by lazy { Glossary.load(appContext) }
    private val models: Map<String, List<AiModel>> by lazy { AiModel.load(appContext) }

    private val _packs = MutableStateFlow<List<PackInfo>>(emptyList())
    val packs: StateFlow<List<PackInfo>> = _packs.asStateFlow()

    /** The game recognized from pack matches (shown in the UI); null until a line is found in a pack. */
    private val _game = MutableStateFlow<String?>(null)
    val game: StateFlow<String?> = _game.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    /** Last online failure (invalid key, quota…), shown in settings; null while it works. */
    private val _onlineError = MutableStateFlow<OnlineKeyState?>(null)
    val onlineError: StateFlow<OnlineKeyState?> = _onlineError.asStateFlow()

    @Volatile private var chain: Chain? = null
    @Volatile private var free: FreeLineTranslator? = null

    private class Chain(val signature: String, val translator: TextTranslator, val online: Boolean)

    init {
        scope.launch(Dispatchers.IO) {
            // Packs are opened in the background so the first subtitle does not wait; out of memory
            // just means no packs (the engines still work).
            runCatching { library.warmUp() }
            publishPacks()
            refreshPacks(force = false)
        }
    }

    /** Checks the CouchSubs pack server at most once a day (or now when [force]) and loads new packs. */
    fun refreshPacks(force: Boolean) {
        if (_updating.value) return
        scope.launch(Dispatchers.IO) {
            _updating.value = true
            try {
                val result = packStore.refresh(force = force)
                if (result.downloaded.isNotEmpty() || result.removed.isNotEmpty()) {
                    library.reload()
                    publishPacks()
                }
            } catch (e: Exception) {
                // Offline or server unavailable: the packs shipped with the app keep working.
            } finally {
                _updating.value = false
            }
        }
    }

    private fun publishPacks() {
        _packs.value = runCatching { library.infos.map { PackInfo(it.id, it.title, it.entries, it.builtIn) } }.getOrDefault(emptyList())
    }

    /** The pack translation of [text] (whole line, its sentences, or the closest line of the game in play). */
    suspend fun lookup(text: String): PackMatch? = withContext(Dispatchers.Default) {
        val match = runCatching { PackLookup.match(library, text) }.getOrNull()
        if (match != null) library.scopeTitle?.let { _game.value = it }
        match
    }

    /** ML Kit through CouchSubs' sentence layer. Blocks; [mlKit] translates one sentence. */
    fun translateFree(text: String, mlKit: (String) -> String): String = freeLayer(mlKit).translate(text)

    /**
     * Translates one line with the configured online engine; on failure the engine itself falls back
     * to the free layer for that line. Blocks; call on a background thread.
     */
    fun translateOnline(text: String, settings: AppSettings, mlKit: (String) -> String): GameTranslationResult {
        val current = chainFor(settings, mlKit)
        val translated = current.translator.translate(text)
        return GameTranslationResult(translated, if (current.online && _onlineError.value == null) LineOrigin.ONLINE else LineOrigin.FAST)
    }

    private fun freeLayer(mlKit: (String) -> String): FreeLineTranslator =
        free ?: FreeLineTranslator(cache("mlkit"), glossary, engine = mlKit).also { free = it }

    /** Whether an online engine with a key is configured. */
    fun onlineConfigured(settings: AppSettings) = settings.onlineEngine != OnlineEngine.NONE && settings.onlineKey != null

    fun modelsFor(engine: OnlineEngine): List<AiModel> = engine.aiProvider?.let { models[it] }.orEmpty()

    /** Tries [key] with a sample line before it is saved (CouchSubs' KeyCheck). Blocks. */
    suspend fun checkKey(engine: OnlineEngine, key: String, modelId: String?): OnlineKeyState = withContext(Dispatchers.IO) {
        when (engine) {
            OnlineEngine.NONE -> OnlineKeyState.VALID
            OnlineEngine.DEEPL -> KeyCheck.deepL(key.trim())
            else -> {
                val provider = provider(engine)
                val model = modelsFor(engine).firstOrNull { it.id == modelId } ?: modelsFor(engine).firstOrNull()
                    ?: return@withContext OnlineKeyState.MODEL_REJECTED
                KeyCheck.ai(provider, key.trim(), model)
            }
        }
    }

    private fun chainFor(settings: AppSettings, mlKit: (String) -> String): Chain {
        val key = settings.onlineKey
        val engine = if (key != null) settings.onlineEngine else OnlineEngine.NONE
        val signature = listOf(engine.key, settings.onlineModel.orEmpty(), key?.hashCode()?.toString().orEmpty()).joinToString("|")
        chain?.takeIf { it.signature == signature }?.let { return it }

        val free = freeLayer(mlKit)
        val online: SentenceTranslator<*>? = when (engine) {
            OnlineEngine.NONE -> null
            OnlineEngine.DEEPL -> DeepLTranslator(key!!, free, cache = cache(DeepLTranslator.ENGINE_ID), glossary = glossary)
            else -> {
                val provider = provider(engine)
                val model = modelsFor(engine).firstOrNull { it.id == settings.onlineModel } ?: modelsFor(engine).firstOrNull()
                model?.let { AiTranslator(provider, key!!, it, free, cache = cache(AiTranslator.engineId(provider, it))) }
            }
        }
        // Online translators fall back to the free one by themselves (and pause the service for a
        // minute after a failure); the last failure is shown in settings.
        _onlineError.value = null
        online?.onFailureChanged = { state -> _onlineError.value = state }
        return Chain(signature, online ?: free, online != null).also { chain = it }
    }

    private fun provider(engine: OnlineEngine) = when (engine) {
        OnlineEngine.GEMINI -> AiProvider.GEMINI
        OnlineEngine.OPENAI -> AiProvider.OPENAI
        else -> AiProvider.CLAUDE
    }

    private fun cache(engine: String) = TranslationCache.shared(translationsDir, engine)

    companion object {
        /** CouchSubs' public pack server (free, no account; rate-limited per address). */
        const val PACKS_URL = "https://couchsubs-packs.enmera.workers.dev"
    }
}
