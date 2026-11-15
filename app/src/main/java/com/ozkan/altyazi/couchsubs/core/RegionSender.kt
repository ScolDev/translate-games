// SPDX-License-Identifier: AGPL-3.0-only
// Based on CouchSubs by Sinan Yorulmaz (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0),
// couchsubs-android/app/src/main/java/com/couchsubs/mobile/core/Areas.kt; modified on 2026-11-15 for Altyazı Çevirici.
package com.ozkan.altyazi.couchsubs.core

/** Ek alanların (görev, telefon, bildirim) çevirisini TV'ye ileten taraf. */
interface RegionSender {
    /** [text] boşsa alanın kutusu kalkar. */
    fun sendRegion(id: String, text: String, box: TextBox?, lineHeight: Double?)
    fun clearRegions()
}
