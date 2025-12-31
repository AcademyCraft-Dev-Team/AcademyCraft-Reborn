package org.academy.internal.client.app.music.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public record MusicSource(Object path) {
    public static MusicSource fromIdentifier(Identifier location) {
        return new MusicSource(location);
    }

    public static MusicSource fromAbsolutePath(String absolutePath) {
        return new MusicSource(absolutePath);
    }

    public ByteBuffer getData() throws IOException {
        byte[] bytes;
        if (path instanceof Identifier location) {
            try (var stream = Minecraft.getInstance().getResourceManager().open(location)) {
                bytes = stream.readAllBytes();
            }
        } else if (path instanceof String strPath) {
            var filePath = Path.of(strPath);
            bytes = Files.readAllBytes(filePath);
        } else throw new IOException("Unsupported media source type: " + path.getClass().getName());

        var buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }
}