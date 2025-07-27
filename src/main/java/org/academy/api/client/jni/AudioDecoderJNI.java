package org.academy.api.client.jni;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public final class AudioDecoderJNI {
    static {
        loadNativeLibrary();
    }

    private AudioDecoderJNI() {
    }

    private static void loadNativeLibrary() {
        try {
            var libName = "audiodecoder_jni";
            var os = System.getProperty("os.name").toLowerCase();
            String libFileName;

            if (os.contains("win")) {
                libFileName = libName + ".dll";
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                libFileName = "lib" + libName + ".so";
            } else if (os.contains("mac")) {
                libFileName = "lib" + libName + ".dylib";
            } else {
                throw new UnsupportedOperationException("Unsupported operating system: " + os);
            }

            var tempDir = new File(System.getProperty("java.io.tmpdir"), "natives");
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new RuntimeException("Failed to create temp directory: " + tempDir.getAbsolutePath());
            }

            var nativeLibFile = new File(tempDir, libFileName);

            try (var in = AudioDecoderJNI.class.getResourceAsStream("/natives/" + libFileName);
                 var out = new FileOutputStream(nativeLibFile)) {
                if (in == null) {
                    throw new UnsatisfiedLinkError("Native library not found in JAR: /natives/" + libFileName);
                }
                in.transferTo(out);
            }

            System.load(nativeLibFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load native audio decoder library", e);
        }
    }

    public static native long decoder_init_memory(ByteBuffer dataBuffer);

    private static native long decoder_read_pcm_frames(long decoderPtr, ByteBuffer pcmBuffer, int framesToRead);

    public static long decoder_read_into_buffer(long decoderPtr, ByteBuffer pcmBuffer, int framesToRead) {
        if (!pcmBuffer.isDirect()) {
            throw new IllegalArgumentException("pcmBuffer must be a direct buffer");
        }
        return decoder_read_pcm_frames(decoderPtr, pcmBuffer, framesToRead);
    }

    public static native void decoder_uninit(long decoderPtr);

    public static native int decoder_get_channels(long decoderPtr);

    public static native int decoder_get_sample_rate(long decoderPtr);

    public static native long decoder_get_length_in_pcm_frames(long decoderPtr);

    public static native int decoder_seek_to_pcm_frame(long decoderPtr, long frameIndex);
}