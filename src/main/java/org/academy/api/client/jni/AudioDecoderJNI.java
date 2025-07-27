package org.academy.api.client.jni;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class AudioDecoderJNI {
    static {
        loadNativeLibrary();
    }

    private AudioDecoderJNI() {
    }

    private static void loadNativeLibrary() {
        try {
            String libName = "audiodecoder_jni";
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();

            String platform;
            String arch;
            String libFileName;

            if (osName.contains("win")) {
                platform = "windows";
                libFileName = libName + ".dll";
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix") || osName.contains("linux")) {
                platform = "linux";
                libFileName = "lib" + libName + ".so";
            } else {
                throw new UnsupportedOperationException("Unsupported operating system: " + osName);
            }

            if (osArch.equals("amd64") || osArch.equals("x86_64")) {
                arch = "x86_64";
            } else if (osArch.equals("aarch64") || osArch.equals("arm64")) {
                arch = "aarch64";
            } else {
                throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
            }

            String resourcePath = String.format("/natives/%s-%s/%s", platform, arch, libFileName);

            File tempDir = new File(System.getProperty("java.io.tmpdir"), "audiodecoder_natives_" + System.nanoTime());
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    throw new IOException("Failed to create temp directory: " + tempDir.getAbsolutePath());
                }
            }
            tempDir.deleteOnExit();

            File nativeLibFile = new File(tempDir, libFileName);
            nativeLibFile.deleteOnExit();

            try (InputStream in = AudioDecoderJNI.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    String errorMessage = String.format("Native library not found in JAR for %s-%s: %s", platform, arch, resourcePath);
                    throw new UnsatisfiedLinkError(errorMessage);
                }
                try (FileOutputStream out = new FileOutputStream(nativeLibFile)) {
                    in.transferTo(out);
                }
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