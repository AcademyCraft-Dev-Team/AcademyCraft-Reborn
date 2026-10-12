package org.academy.internal.common.structure.physics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** Explicit development artifact contract; reading it does not load or link a library. */
public record NativePhysicsArtifact(Path library, Path manifest, String sha256, String platform) {
    public static final String ENGINE = "rapier3d-f64-0.36.1";
    public static final int ABI = 1;
    public static final int ENGINE_VERSION = 3601;
    public static final String LIBRARY_PROPERTY = "academy.nativePhysics.library";
    public static final String MANIFEST_PROPERTY = "academy.nativePhysics.manifest";
    private static final String FORMAT = "academy-native-physics-v1";
    private static final int MAX_MANIFEST_BYTES = 16_384;
    private static final long MAX_LIBRARY_BYTES = 64L * 1024 * 1024;
    private static final Set<String> FIELDS = Set.of("format", "abi", "engine", "platform", "sha256", "library",
            "rustVersion", "sourceSha256", "target");

    public static NativePhysicsArtifact configured() throws IOException {
        var library = requiredProperty(LIBRARY_PROPERTY);
        var manifest = requiredProperty(MANIFEST_PROPERTY);
        return verify(Path.of(library), Path.of(manifest), currentPlatform());
    }

    public static NativePhysicsArtifact verify(Path library, Path manifest, String platform) throws IOException {
        var expectedName = switch (platform) {
            case "windows-x86_64" -> "academy_physics.dll";
            case "linux-x86_64" -> "libacademy_physics.so";
            default -> throw new IllegalArgumentException("Unsupported native physics platform: " + platform);
        };
        var libraryPath = library.toRealPath();
        var manifestPath = manifest.toRealPath();
        if (!Files.isRegularFile(libraryPath) || !Files.isRegularFile(manifestPath)) {
            throw new IOException("Native physics library and manifest must be regular files");
        }
        if (Files.size(manifestPath) > MAX_MANIFEST_BYTES) throw new IOException("Native physics manifest is too large");
        byte[] content;
        try (var stream = Files.newInputStream(manifestPath)) {
            content = stream.readNBytes(MAX_MANIFEST_BYTES + 1);
        }
        if (content.length > MAX_MANIFEST_BYTES) throw new IOException("Native physics manifest is too large");
        var values = new HashMap<String, String>();
        for (var line : new String(content, StandardCharsets.UTF_8).split("\\R")) {
            var text = line.trim();
            if (text.isEmpty() || text.startsWith("#")) continue;
            int separator = text.indexOf('=');
            if (separator <= 0) throw new IOException("Invalid native physics manifest line");
            var key = text.substring(0, separator).trim();
            var value = text.substring(separator + 1).trim();
            if (!FIELDS.contains(key) || value.isEmpty() || values.putIfAbsent(key, value) != null) {
                throw new IOException("Unknown, empty or duplicate native physics manifest field: " + key);
            }
        }
        if (!FORMAT.equals(values.get("format")) || !Integer.toString(ABI).equals(values.get("abi"))
                || !ENGINE.equals(values.get("engine")) || !platform.equals(values.get("platform"))
                || !expectedName.equals(values.get("library")) || !expectedName.equals(libraryPath.getFileName().toString())) {
            throw new IOException("Native physics manifest format/ABI/engine/platform/library mismatch");
        }
        var expectedDigest = values.get("sha256");
        if (expectedDigest == null || !expectedDigest.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Native physics manifest requires a SHA-256 digest");
        }
        var actualDigest = digest(libraryPath);
        if (!actualDigest.equalsIgnoreCase(expectedDigest)) throw new IOException("Native physics artifact SHA-256 mismatch");
        return new NativePhysicsArtifact(libraryPath, manifestPath, actualDigest, platform);
    }

    static String digest(Path path) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
        long bytes = 0;
        try (var input = Files.newInputStream(path)) {
            var buffer = new byte[16_384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes += read;
                if (bytes > MAX_LIBRARY_BYTES) throw new IOException("Native physics library exceeds artifact size limit");
                digest.update(buffer, 0, read);
            }
        }
        if (bytes == 0) throw new IOException("Native physics library is empty");
        return HexFormat.of().formatHex(digest.digest());
    }

    static void copyBounded(Path source, Path destination) throws IOException {
        long bytes = 0;
        try (var input = Files.newInputStream(source);
             var output = Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW,
                     java.nio.file.StandardOpenOption.WRITE)) {
            var buffer = new byte[16_384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes += read;
                if (bytes > MAX_LIBRARY_BYTES) throw new IOException("Native physics private copy exceeds artifact size limit");
                output.write(buffer, 0, read);
            }
        }
    }

    public static String currentPlatform() {
        var architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!architecture.equals("amd64") && !architecture.equals("x86_64")) {
            throw new IllegalStateException("Native physics currently requires an x86_64 JVM");
        }
        var system = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (system.startsWith("windows")) return "windows-x86_64";
        if (system.equals("linux")) return "linux-x86_64";
        throw new IllegalStateException("Unsupported native physics operating system: " + system);
    }

    private static String requiredProperty(String name) {
        var value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Native physics is opt-in; explicitly set -D" + name + "=<absolute artifact path>");
        }
        var path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalStateException("Native physics artifact path must be absolute: " + name);
        return value;
    }
}
