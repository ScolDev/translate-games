// SPDX-License-Identifier: AGPL-3.0-only
// Based on CouchSubs by Sinan Yorulmaz (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0),
// couchsubs-android/app/src/main/java/com/couchsubs/mobile/core/SubtitlePipeline.kt; modified on 2026-11-15 for Altyazı Çevirici.
package com.ozkan.altyazi.couchsubs.core

/** Çeviri motoru. Bloklayarak çevirir (hattın kendi iş parçacığında çağrılır); başarısızsa istisna atar. */
fun interface TextTranslator {
    fun translate(text: String): String
}
