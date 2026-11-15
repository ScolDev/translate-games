// SPDX-License-Identifier: AGPL-3.0-only
// Based on CouchSubs by Sinan Yorulmaz (https://github.com/yorulmazsinan/couchsubs, AGPL-3.0),
// couchsubs-android/app/src/main/java/com/couchsubs/mobile/core/Reading.kt (TextBox only); modified on 2026-11-15 for Altyazı Çevirici.
package com.ozkan.altyazi.couchsubs.core

import kotlin.math.abs

/** Yazının yeri, içinde bulunduğu alanın kenarlarına oranla (0–1, üstten ve soldan). */
data class TextBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun union(other: TextBox) =
        TextBox(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

    /** Bu kutu [area] içindeki oranlarsa, aynı yerin [area]'yı içeren çerçevedeki oranları. */
    fun placedIn(area: TextBox): TextBox {
        val width = area.right - area.left
        val height = area.bottom - area.top
        return TextBox(area.left + left * width, area.top + top * height, area.left + right * width, area.top + bottom * height)
    }

    /**
     * Ekranın ortasındaki altyazının kutusu ortaya göre simetrik genişletilir: yazı tanıma satırın bir ucunu
     * okuyamazsa çeviri yine ortalanır ve İngilizcenin tamamını örter. Köşedeki yazıya dokunulmaz.
     */
    fun centeredOnScreen(): TextBox {
        val middle = (left + right) / 2
        if (middle <= 0.3 || middle >= 0.7) return this
        return copy(left = maxOf(0.0, minOf(left, 1 - right)), right = minOf(1.0, maxOf(right, 1 - left)))
    }
}

/** Karede okunan alan (oranlar); masaüstüyle aynı varsayılan: alt orta bölge. */
