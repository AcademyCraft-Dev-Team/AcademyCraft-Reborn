package org.academy.internal.client.app.music.engine

/**
 * 流式播放缓冲的排空与结束判定策略（与 OpenAL 解耦，便于回归测试）。
 *
 * 修复的缺陷：原先只为"正在播放"的音源回填缓冲。缓冲播放完毕时音源会转为 STOPPED，
 * 于是最后一个缓冲永远留在队列里，queuedBufferCount 不归零、isFinished 永不成立，
 * 表现就是**歌曲播完不会自动切下一首**喵。
 */
internal object StreamDrainPolicy {
    /**
     * 是否可回填已播完的缓冲。只要音源报告有处理完的缓冲就允许排空，不再依赖播放状态，
     * 否则尾缓冲会滞留导致播放无法结束；仅当解码暂时落后且流未读完时保留缓冲避免断音喵。
     */
    fun shouldDrain(queueEmpty: Boolean, streamReadFinished: Boolean): Boolean {
        return streamReadFinished || !queueEmpty
    }

    /**
     * 流读取结束且队列中已无待播缓冲时视为播放完成喵。
     */
    fun isFinished(streamReadFinished: Boolean, queuedBufferCount: Int): Boolean {
        return streamReadFinished && queuedBufferCount == 0
    }
}
