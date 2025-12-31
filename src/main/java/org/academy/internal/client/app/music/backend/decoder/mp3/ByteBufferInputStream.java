package org.academy.internal.client.app.music.backend.decoder.mp3;

import java.io.InputStream;
import java.nio.ByteBuffer;

public final class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() {
        if (!buffer.hasRemaining()) return -1;
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int off, int len) {
        if (!buffer.hasRemaining()) return -1;
        var length = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, length);
        return length;
    }

    @Override
    public int available() {
        return buffer.remaining();
    }
}