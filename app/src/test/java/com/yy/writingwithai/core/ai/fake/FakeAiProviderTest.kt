package com.yy.writingwithai.core.ai.fake

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeAiProviderTest {
    private val provider = FakeAiProvider()

    @AfterEach
    fun tearDown() {
        FakeConfigHolder.reset()
    }

    @Test
    fun normal_flow_emits_started_delta_usage_done() =
        runTest {
            FakeConfigHolder.set(
                text = "hello world",
                tokenCounts = AiStreamEvent.Usage(5, 2, 7),
            )
            val events =
                provider.stream(
                    AiRequest(WritingOp.EXPAND, "test", "fake"),
                    AiCredentials("fake"),
                ).toList()

            assertEquals(AiStreamEvent.Started, events.first())
            assertTrue(events.any { it is AiStreamEvent.Delta })
            val usage = events.filterIsInstance<AiStreamEvent.Usage>().firstOrNull()
            assertNotNull(usage)
            assertEquals(5, usage!!.inputTokens)
            assertEquals(AiStreamEvent.Done, events.last())
        }

    @Test
    fun error_injection_emits_failed() =
        runTest {
            FakeConfigHolder.set(
                text = "a b c d e",
                errorAfterTokens = 1,
            )
            val events =
                provider.stream(
                    AiRequest(WritingOp.POLISH, "test", "fake"),
                    AiCredentials("fake"),
                ).toList()

            assertEquals(AiStreamEvent.Started, events.first())
            assertTrue(events.any { it is AiStreamEvent.Delta })
            assertTrue(events.any { it is AiStreamEvent.Failed })
        }

    @Test
    fun empty_text_emits_failed() =
        runTest {
            FakeConfigHolder.set(text = "")
            val events =
                provider.stream(
                    AiRequest(WritingOp.ORGANIZE, "test", "fake"),
                    AiCredentials("fake"),
                ).toList()

            assertEquals(AiStreamEvent.Started, events.first())
            assertTrue(events.any { it is AiStreamEvent.Failed })
        }
}
