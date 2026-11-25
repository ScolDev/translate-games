package com.ozkan.altyazi.core

import com.ozkan.altyazi.core.SpeechQueuePolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechQueuePolicyTest {

    private val policy = SpeechQueuePolicy()

    private fun queue(text: String) = policy.next(text, queue = true)

    @Test
    fun `flush always returns the full text, even when repeated`() {
        assertEquals(SpeechQueuePolicy.Utterance("Hello", Mode.FLUSH), policy.next("Hello", queue = false))
        assertEquals(SpeechQueuePolicy.Utterance("Hello", Mode.FLUSH), policy.next("Hello", queue = false))
    }

    @Test
    fun `blank text is skipped and leaves the state unchanged`() {
        queue("Hello")
        assertNull(queue("   "))
        assertNull(policy.next("", queue = false))
        assertNull(queue("Hello"))
    }

    @Test
    fun `first line with empty state is enqueued`() {
        assertEquals(SpeechQueuePolicy.Utterance("Hello", Mode.ADD), queue("Hello"))
    }

    @Test
    fun `exact duplicate is skipped`() {
        queue("Hello there")
        assertNull(queue("Hello there"))
    }

    @Test
    fun `case-insensitive duplicate is skipped`() {
        queue("Hello there")
        assertNull(queue("hello THERE"))
    }

    @Test
    fun `whitespace-only difference is skipped`() {
        queue("Hello there")
        assertNull(queue("Hello there "))
        assertNull(queue("Hello there   "))
    }

    @Test
    fun `extension enqueues only the suffix and the state becomes the full text`() {
        queue("Hello")
        assertEquals(SpeechQueuePolicy.Utterance("there friend", Mode.ADD), queue("Hello there friend"))
        assertNull(queue("Hello there friend"))
    }

    @Test
    fun `extension with only whitespace is skipped`() {
        queue("Hello")
        assertNull(queue("Hello   "))
    }

    @Test
    fun `punctuation-only suffix is skipped`() {
        queue("Hello there")
        assertNull(queue("Hello there..."))
    }

    @Test
    fun `mid-word extension backs up to the start of the word`() {
        queue("Hello the")
        assertEquals(SpeechQueuePolicy.Utterance("there friend", Mode.ADD), queue("Hello there friend"))
    }

    @Test
    fun `shorter line is enqueued in full and becomes the state`() {
        queue("Hello there friend")
        assertEquals(SpeechQueuePolicy.Utterance("Hello there", Mode.ADD), queue("Hello there"))
        assertNull(queue("Hello there"))
    }

    @Test
    fun `unrelated line is enqueued in full`() {
        queue("Hello there")
        assertEquals(SpeechQueuePolicy.Utterance("Goodbye", Mode.ADD), queue("Goodbye"))
    }

    @Test
    fun `reset makes the same text enqueue in full again`() {
        queue("Hello")
        policy.reset()
        assertEquals(SpeechQueuePolicy.Utterance("Hello", Mode.ADD), queue("Hello"))
    }
}
