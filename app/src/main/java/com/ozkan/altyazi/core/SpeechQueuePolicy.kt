package com.ozkan.altyazi.core

/**
 * Decides what the speaker should say for each presented line.
 *
 * Flush mode always speaks the full text. Queue mode remembers the last enqueued text so a subtitle that
 * grows word by word (typewriter effect) only speaks what is new instead of repeating earlier words.
 * Pure Kotlin so the rules are unit-testable; the Android speaker only executes the result.
 */
class SpeechQueuePolicy {

    enum class Mode { FLUSH, ADD }

    data class Utterance(val text: String, val mode: Mode)

    private var last = ""

    /** Returns what to speak for [text], or null when there is nothing new to say. */
    fun next(text: String, queue: Boolean): Utterance? {
        val current = text.trim().replace(WHITESPACE, " ")
        if (current.isEmpty()) return null
        if (!queue) {
            last = current
            return Utterance(current, Mode.FLUSH)
        }
        if (last.isNotEmpty()) {
            if (current.equals(last, ignoreCase = true)) return null
            if (current.startsWith(last, ignoreCase = true)) {
                val suffix = newPart(current)
                last = current
                return if (suffix.any { it.isLetterOrDigit() }) Utterance(suffix, Mode.ADD) else null
            }
        }
        last = current
        return Utterance(current, Mode.ADD)
    }

    /** Forgets the last enqueued text, so the same text is spoken in full again. */
    fun reset() {
        last = ""
    }

    /** The part of [current] after [last], backed up to the start of a word that [last] cut in half. */
    private fun newPart(current: String): String {
        val cut = last.length
        val midWord = current.getOrNull(cut)?.isLetterOrDigit() == true && last.last().isLetterOrDigit()
        val start = if (midWord) current.lastIndexOf(' ', cut - 1) + 1 else cut
        return current.substring(start).trim()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
