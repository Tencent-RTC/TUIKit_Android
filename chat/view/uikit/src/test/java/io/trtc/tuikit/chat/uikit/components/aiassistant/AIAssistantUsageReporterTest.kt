package io.trtc.tuikit.chat.uikit.components.aiassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AIAssistantUsageReporterTest {

    @Test
    fun `message usage reports 1022 only once per reporter`() {
        val values = mutableListOf<Int>()
        val reporter = AIAssistantUsageReporter(values::add)

        reporter.onMessageSent()
        reporter.onMessageSent()

        assertEquals(listOf(1022), values)
    }

    @Test
    fun `voice entry reports 1023 every time`() {
        val values = mutableListOf<Int>()
        val reporter = AIAssistantUsageReporter(values::add)

        reporter.onVoiceEntered()
        reporter.onVoiceEntered()

        assertEquals(listOf(1023, 1023), values)
    }
}
