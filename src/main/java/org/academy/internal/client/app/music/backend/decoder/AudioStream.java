package org.academy.internal.client.app.music.backend.decoder;

import java.io.IOException;
import java.nio.ShortBuffer;

public interface AudioStream extends AutoCloseable {
    int getChannels();

    int getSampleRate();

    long getTotalSamples();

    int read(ShortBuffer pcmBuffer) throws IOException;

    void seek(long sampleFrame) throws IOException;

    @Override
    void close();
}