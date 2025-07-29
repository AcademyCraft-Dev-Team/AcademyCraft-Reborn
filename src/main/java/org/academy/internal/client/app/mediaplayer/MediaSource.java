package org.academy.internal.client.app.mediaplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public record MediaSource(Object path) {
    public static MediaSource fromResourceLocation(ResourceLocation location) {
        return new MediaSource(location);
    }

    public static MediaSource fromAbsolutePath(String absolutePath) {
        return new MediaSource(absolutePath);
    }

    public ByteBuffer getData() throws IOException {
        byte[] bytes;
        if (path instanceof ResourceLocation location) {
            try (var stream = Minecraft.getInstance().getResourceManager().open(location)) {
                bytes = stream.readAllBytes();
            }
        } else if (path instanceof String strPath) {
            var filePath = Path.of(strPath);
            bytes = Files.readAllBytes(filePath);
        } else {
            throw new IOException("Unsupported media source type: " + path.getClass().getName());
        }

        var buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }
}
