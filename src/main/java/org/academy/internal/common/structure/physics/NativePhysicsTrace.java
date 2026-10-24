package org.academy.internal.common.structure.physics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit diagnostic only: records successful native requests for deterministic solver replay. */
final class NativePhysicsTrace implements AutoCloseable {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final int MAX_LINES = 500_000;
    private final BufferedWriter writer;
    private int lines;

    static NativePhysicsTrace open() {
        String prefix = System.getProperty("academy.nativePhysics.trace", "");
        if (prefix.isBlank()) return null;
        try {
            return new NativePhysicsTrace(Files.newBufferedWriter(Path.of(prefix + "-" + SEQUENCE.incrementAndGet() + ".txt")));
        } catch (IOException failure) { throw new IllegalStateException("Cannot open requested physics trace", failure); }
    }

    private NativePhysicsTrace(BufferedWriter writer) { this.writer = writer; }

    void write(String operation, long body, double[] values) {
        if (++lines > MAX_LINES) throw new IllegalStateException("Physics trace line budget exhausted");
        try {
            writer.write(operation);
            writer.write(" " + body);
            for (double value : values) writer.write(" " + value);
            writer.newLine();
            if (operation.equals("STEP")) writer.flush();
        } catch (IOException failure) { throw new IllegalStateException("Cannot write requested physics trace", failure); }
    }

    @Override public void close() {
        try { writer.close(); }
        catch (IOException failure) { throw new IllegalStateException("Cannot close requested physics trace", failure); }
    }
}
