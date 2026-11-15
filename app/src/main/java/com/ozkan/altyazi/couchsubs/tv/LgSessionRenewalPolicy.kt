// SPDX-License-Identifier: AGPL-3.0-only
// Based on CouchSubs by Sinan Yorulmaz (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0),
// couchsubs-android/app/src/main/java/com/couchsubs/mobile/tv/LgSessionRenewalPolicy.kt; modified on 2026-11-15 for Altyazı Çevirici (package name, integration).
package com.ozkan.altyazi.couchsubs.tv

import java.util.Locale

internal object LgSessionRenewalPolicy {
    private const val RENEW_INTERVAL_MS = 20 * 3600 * 1000L

    fun storageKey(host: String): String = "tvApp.lgSessionRenewed.${host.trim().lowercase(Locale.ROOT)}"

    fun renewIfDue(
        now: Long,
        lastRenewedAt: Long?,
        saveRenewedAt: (Long) -> Unit,
        renew: () -> Int?,
    ): Int? {
        if (lastRenewedAt != null && now - lastRenewedAt < RENEW_INTERVAL_MS) return null
        val hours = renew() ?: return null
        saveRenewedAt(now)
        return hours
    }
}
