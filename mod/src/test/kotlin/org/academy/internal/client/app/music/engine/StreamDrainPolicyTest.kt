package org.academy.internal.client.app.music.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamDrainPolicyTest {
    @Test
    fun `drains processed buffers after the source stopped`() {
        // 尾缓冲播完后音源已 STOPPED、解码队列为空但流已读完: 必须继续排空，
        // 否则 queuedBufferCount 不归零，播放永不结束、无法自动切歌喵.
        assertTrue(StreamDrainPolicy.shouldDrain(queueEmpty = true, streamReadFinished = true))
        assertTrue(StreamDrainPolicy.shouldDrain(queueEmpty = false, streamReadFinished = true))
    }

    @Test
    fun `keeps queued buffer while decoder lags behind`() {
        // 解码暂时落后且流未读完: 保留已入队缓冲继续播放，避免断音喵.
        assertFalse(StreamDrainPolicy.shouldDrain(queueEmpty = true, streamReadFinished = false))
        assertTrue(StreamDrainPolicy.shouldDrain(queueEmpty = false, streamReadFinished = false))
    }

    @Test
    fun `finished only after stream end and queue drained`() {
        assertFalse(StreamDrainPolicy.isFinished(streamReadFinished = false, queuedBufferCount = 0))
        assertFalse(StreamDrainPolicy.isFinished(streamReadFinished = true, queuedBufferCount = 2))
        assertTrue(StreamDrainPolicy.isFinished(streamReadFinished = true, queuedBufferCount = 0))
    }
}
