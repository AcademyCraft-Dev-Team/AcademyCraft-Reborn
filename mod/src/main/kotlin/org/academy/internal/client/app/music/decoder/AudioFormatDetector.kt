package org.academy.internal.client.app.music.decoder

import org.academy.internal.common.music.provider.AudioFormat
import java.nio.ByteBuffer

/**
 * 客户端解码器的格式嗅探入口，逻辑委托公共 [AudioFormat] 喵。
 */
object AudioFormatDetector {
    fun detect(data: ByteArray): Format = toClient(AudioFormat.detect(data))

    fun detect(data: ByteBuffer): Format = toClient(AudioFormat.detect(data))

    private fun toClient(format: AudioFormat.Format): Format = Format.valueOf(format.name)

    enum class Format(val supported: Boolean) {
        OGG_VORBIS(true),
        FLAC(true),
        MP3(true),
        OGG_OPUS(false),
        OGG_UNKNOWN(false),
        MP4_AAC(false),
        UNKNOWN(false),
    }
}
