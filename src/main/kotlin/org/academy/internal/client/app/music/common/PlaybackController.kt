package org.academy.internal.client.app.music.common

/**
 * 播放控制权归属：LOCAL 为本地自由播放；SHARED 表示播放轴由共享通道（音乐室/全服点播）驱动，
 * 本地自动连播逻辑被禁用，UI 操作改走共享控制包喵。
 */
enum class PlaybackController {
    LOCAL,
    SHARED
}
