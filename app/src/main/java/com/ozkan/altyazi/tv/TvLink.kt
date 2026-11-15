package com.ozkan.altyazi.tv

import android.content.Context
import com.ozkan.altyazi.couchsubs.core.SubtitlePlacement
import com.ozkan.altyazi.couchsubs.core.SubtitleServer
import com.ozkan.altyazi.couchsubs.core.SubtitleStyle
import com.ozkan.altyazi.couchsubs.core.SubtitleTextColor
import com.ozkan.altyazi.couchsubs.tv.FoundTv
import com.ozkan.altyazi.couchsubs.tv.LgSessionRenewalPolicy
import com.ozkan.altyazi.couchsubs.tv.TvBrand
import com.ozkan.altyazi.couchsubs.tv.TvDiscovery
import com.ozkan.altyazi.couchsubs.tv.lg.JschShell
import com.ozkan.altyazi.couchsubs.tv.lg.LgInstallError
import com.ozkan.altyazi.couchsubs.tv.lg.LgInstallException
import com.ozkan.altyazi.couchsubs.tv.lg.LgInstaller
import com.ozkan.altyazi.couchsubs.tv.lg.LgKeys
import com.ozkan.altyazi.couchsubs.tv.samsung.InstallLog
import com.ozkan.altyazi.couchsubs.tv.samsung.InstallStep
import com.ozkan.altyazi.data.AppSettings
import com.ozkan.altyazi.data.PanelBackground
import com.ozkan.altyazi.data.TvPlacement
import com.ozkan.altyazi.data.TvTextSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/** An LG TV on the network and whether its Developer Mode SSH answers. */
data class LgTv(val name: String, val host: String, val devMode: Boolean)

sealed interface TvInstallState {
    data object Idle : TvInstallState
    data class Working(val step: InstallStep) : TvInstallState
    data class Done(val version: String, val installed: Boolean) : TvInstallState
    data class Failed(val error: LgInstallError?, val detail: String?) : TvInstallState
}

/**
 * Shows the translations on the TV, over the game, with the CouchSubs webOS app
 * (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0):
 *
 * - the tablet runs CouchSubs' subtitle server (Server-Sent Events on port 8787); the TV app finds
 *   it on the network, or is started with its address, and draws the PS5's HDMI picture with the
 *   subtitles on top;
 * - the TV app is installed once from the tablet through LG Developer Mode (SSH on 9922, key from the
 *   Key Server unlocked with the passphrase shown on the TV), and the Developer Mode session is
 *   renewed at most once a day so the app is not removed.
 */
class TvLink(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val keys = LgKeys(File(appContext.noBackupFilesDir, "lg"))
    private val log = InstallLog(File(appContext.filesDir, "tvinstall.log"))
    private val prefs = appContext.getSharedPreferences("tv", Context.MODE_PRIVATE)

    private var server: SubtitleServer? = null

    private val _connected = MutableStateFlow(0)
    /** Number of TV apps currently connected to the subtitle server. */
    val connected: StateFlow<Int> = _connected.asStateFlow()

    private val _serverError = MutableStateFlow(false)
    val serverError: StateFlow<Boolean> = _serverError.asStateFlow()

    private val _install = MutableStateFlow<TvInstallState>(TvInstallState.Idle)
    val install: StateFlow<TvInstallState> = _install.asStateFlow()

    @Volatile private var lastStyle: SubtitleStyle? = null

    // ---------- Subtitle server ----------

    /** Starts or stops the subtitle server; when started, the installed TV app is opened if possible. */
    fun setEnabled(enabled: Boolean, settings: AppSettings) {
        if (enabled == (server != null)) {
            if (enabled) applyStyle(settings)
            return
        }
        if (!enabled) {
            server?.stop()
            server = null
            _connected.value = 0
            return
        }
        val created = SubtitleServer().apply { onClientsChanged = { count -> _connected.value = count } }
        try {
            created.start()
            server = created
            _serverError.value = false
            lastStyle = null
            applyStyle(settings)
            settings.tvHost?.let { host -> scope.launch(Dispatchers.IO) { openOnTv(host) } }
        } catch (e: Exception) {
            // Port 8787 already in use (e.g. by the CouchSubs phone app).
            _serverError.value = true
        }
    }

    fun applyStyle(settings: AppSettings) {
        val style = styleFor(settings)
        if (style == lastStyle) return
        lastStyle = style
        server?.sendStyle(style)
    }

    fun subtitle(text: String) {
        server?.sendSubtitle(text, durationMs = SHOW_MS)
    }

    fun clear() {
        server?.clear()
    }

    fun close() {
        server?.stop()
        server = null
    }

    // ---------- Installing the TV app ----------

    /** LG TVs answering on the network (SSDP), with their Developer Mode state. */
    suspend fun findTvs(): List<LgTv> = withContext(Dispatchers.IO) {
        val found: List<FoundTv> = runCatching { TvDiscovery.search() }.getOrDefault(emptyList()).filter { it.brand == TvBrand.LG }
        found.map { tv -> async { LgTv(tv.name, tv.host, LgInstaller.isDevModeOn(tv.host)) } }.awaitAll()
    }

    fun hasKey(host: String): Boolean = host.isNotBlank() && keys.load(host.trim()) != null

    /**
     * Installs (or updates) the TV app on the LG TV at [host] and opens it. [passphrase] is the
     * 6-character code shown by the Developer Mode app; it is needed only once, the key is kept.
     * Returns the host on success so it can be remembered.
     */
    fun install(host: String, passphrase: String, onInstalled: (String) -> Unit) {
        if (_install.value is TvInstallState.Working) return
        val target = host.trim()
        _install.value = TvInstallState.Working(InstallStep.CONNECTING)
        scope.launch(Dispatchers.IO) {
            _install.value = try {
                val (ipk, name, version) = packageInfo() ?: throw LgInstallException(LgInstallError.PACKAGE_MISSING)
                val progress: (InstallStep) -> Unit = { step -> _install.value = TvInstallState.Working(step) }
                fun fresh(): ByteArray {
                    if (passphrase.isBlank()) throw LgInstallException(LgInstallError.KEY_REFUSED, "no passphrase")
                    progress(InstallStep.KEY)
                    val key = LgInstaller.decodeKey(LgInstaller.downloadKey(target), passphrase.trim()) { k, p -> JschShell.decryptOtherFormat(k, p) }
                    keys.save(target, key)
                    return key
                }
                fun attempt(key: ByteArray): LgInstaller.Result {
                    val installer = LgInstaller(JschShell(target, key), progress, log)
                    val result = installer.ensureInstalled(ipk, name, version)
                    renewIfDue(target, installer)
                    return result
                }
                val saved = keys.load(target)
                val result = if (saved == null) attempt(fresh()) else try {
                    attempt(saved)
                } catch (e: LgInstallException) {
                    // Turning Developer Mode off and on makes the TV generate a new key.
                    if (e.error != LgInstallError.KEY_REFUSED) throw e
                    keys.remove(target)
                    attempt(fresh())
                }
                onInstalled(target)
                // Point the freshly opened app at this tablet right away.
                openOnTv(target)
                TvInstallState.Done(result.version, result.installed)
            } catch (e: LgInstallException) {
                TvInstallState.Failed(e.error, e.detail)
            } catch (e: Exception) {
                TvInstallState.Failed(null, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun resetInstall() {
        if (_install.value !is TvInstallState.Working) _install.value = TvInstallState.Idle
    }

    /**
     * Opens the TV app with this tablet's address so it connects at once (no network scan), and
     * renews the Developer Mode session when due. Quietly does nothing if the TV is off or not set up.
     */
    fun openOnTv(host: String) {
        val key = keys.load(host) ?: return
        if (!LgInstaller.isDevModeOn(host)) return
        runCatching {
            val shell = JschShell(host, key)
            val payload = JSONObject().put("id", LgInstaller.APP_ID)
            localAddress()?.let { payload.put("params", JSONObject().put("server", "http://$it:${SubtitleServer.DEFAULT_PORT}")) }
            shell.run("/usr/bin/luna-send-pub -n 1 'luna://com.webos.applicationManager/launch' '$payload'", null)
            renewIfDue(host, LgInstaller(shell, log = log))
        }
    }

    /** Developer Mode expires after a while and then removes the app; renew at most once a day. */
    private fun renewIfDue(host: String, installer: LgInstaller) {
        LgSessionRenewalPolicy.renewIfDue(
            now = System.currentTimeMillis(),
            lastRenewedAt = prefs.getLong("renewed_$host", 0L).takeIf { it > 0 },
            saveRenewedAt = { prefs.edit().putLong("renewed_$host", it).apply() },
            renew = { runCatching { installer.renewSession() }.getOrNull() },
        )
    }

    /** The TV package shipped in the APK: bytes, file name (carries the version) and version. */
    private fun packageInfo(): Triple<ByteArray, String, String>? = runCatching {
        val index = appContext.assets.open("tv/tv-packages.properties").use { String(it.readBytes(), Charsets.UTF_8) }
            .lineSequence().map { it.trim() }.filter { '=' in it && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val name = index[LG_IPK] ?: return null
        val version = LgInstaller.versionOfPackage(name) ?: return null
        Triple(appContext.assets.open("tv/$LG_IPK").use { it.readBytes() }, name, version)
    }.getOrNull()

    /** This tablet's Wi-Fi / LAN IPv4 address, which the TV app connects to. */
    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun styleFor(s: AppSettings) = SubtitleStyle(
        fontSize = s.tvTextSize.px,
        bottomPercent = 7,
        placement = when (s.tvPlacement) {
            TvPlacement.COVER -> SubtitlePlacement.COVER
            TvPlacement.ABOVE -> SubtitlePlacement.ABOVE
            TvPlacement.BELOW -> SubtitlePlacement.BELOW
        },
        background = when (s.background) {
            PanelBackground.NONE -> 0
            PanelBackground.SOFT -> 60
            PanelBackground.SOLID -> 90
        },
        bold = s.bold,
        textColor = if (isYellowish(s.textColor)) SubtitleTextColor.YELLOW else SubtitleTextColor.WHITE,
    )

    private fun isYellowish(argb: Long): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r > 200 && g > 170 && b < 170
    }

    private companion object {
        const val LG_IPK = "couchsubs-lg.ipk"
        /** A line stays on the TV at most this long; it is cleared as soon as it leaves the game screen. */
        const val SHOW_MS = 15_000
    }
}
