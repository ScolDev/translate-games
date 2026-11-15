// SPDX-License-Identifier: AGPL-3.0-only
// Based on CouchSubs by Sinan Yorulmaz (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0),
// couchsubs-android/app/src/main/java/com/couchsubs/mobile/tv/samsung/SamsungInstaller.kt and SamsungDiagnostics.kt
// (only the parts the LG installer uses); modified on 2026-11-15 for Altyazı Çevirici.
package com.ozkan.altyazi.couchsubs.tv.samsung

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kurulum günlüğü (dosyaya; dosya yoksa yazılmaz). */
class InstallLog(val file: File?) {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    @Synchronized
    fun write(line: String) {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            // Günlük büyümesin: 512 KB'ı geçince baştan başlar.
            if (target.length() > 512 * 1024) target.delete()
            target.appendText("${format.format(Date())} $line\n")
        }
    }
}

/** Kurulumun ilerleyişi. */
enum class InstallStep { CONNECTING, READING, SIGN_IN, CERTIFICATE, SIGNING, INSTALLING, UNINSTALLING, LAUNCHING, PAIRING, APPROVE_ON_TV, KEY, OPEN_PLAY }

object SamsungDiagnostics {
    /** [a] sürümü [b]'den yeni mi (1.10 > 1.9)? */
    fun isNewer(a: String, b: String): Boolean {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val left = x.getOrElse(i) { 0 }
            val right = y.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parts(version: String) = version.split('.', '-').map { it.toIntOrNull() ?: 0 }
}
