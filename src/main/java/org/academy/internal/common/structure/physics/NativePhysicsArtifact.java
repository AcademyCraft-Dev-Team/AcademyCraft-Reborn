package org.academy.internal.common.structure.physics;

import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Deployment artifact contract; reading it does not load or link a library. The verified artifact
 * is discovered without required JVM properties: explicit system properties first, then explicit
 * environment variables, then a configured home, and finally the fixed game-directory layout
 * {@code academy/native_physics/} beside the installed game.
 */
public record NativePhysicsArtifact(Path library, Path manifest, String sha256, String platform) {
    public static final String ENGINE = "rapier3d-f64-0.36.1";
    public static final int ABI = 2;
    public static final int ENGINE_VERSION = 3601;
    public static final String LIBRARY_PROPERTY = "academy.nativePhysics.library";
    public static final String MANIFEST_PROPERTY = "academy.nativePhysics.manifest";
    public static final String HOME_PROPERTY = "academy.nativePhysics.home";
    public static final String LIBRARY_ENVIRONMENT = "ACADEMY_NATIVE_PHYSICS_LIBRARY";
    public static final String MANIFEST_ENVIRONMENT = "ACADEMY_NATIVE_PHYSICS_MANIFEST";
    public static final String HOME_ENVIRONMENT = "ACADEMY_NATIVE_PHYSICS_HOME";
    public static final String CONVENTIONAL_DIRECTORY = "academy/native_physics";
    public static final String MANIFEST_FILE_NAME = "academy_physics.properties";
    /** Optional in-JAR fallback, laid out per platform so one JAR can carry several builds. */
    public static final String BUNDLED_DIRECTORY = "/academy/native_physics/";
    private static volatile @Nullable NativePhysicsArtifact bundledArtifact;
    private static final String FORMAT = "academy-native-physics-v1";
    private static final int MAX_MANIFEST_BYTES = 16_384;
    private static final long MAX_LIBRARY_BYTES = 64L * 1024 * 1024;
    private static final int BASE64_LIMIT = (int) (MAX_LIBRARY_BYTES / 3 * 4 + 16);
    private static final Set<String> FIELDS = Set.of("format", "abi", "engine", "platform", "sha256", "library",
            "rustVersion", "sourceSha256", "target");

    public static NativePhysicsArtifact configured() throws IOException {
        // An explicit deployment is authoritative: never mask a partial, mismatched or
        // unreadable external artifact by silently replacing it with the bundled copy.
        if (hasExplicitConfiguration()) return resolve(System::getProperty, System::getenv, null);
        var conventional = conventionalDirectory();
        if (conventional != null) {
            var external = fromHome(conventional, currentPlatform());
            if (external != null) return external;
        }
        var bundled = bundled();
        if (bundled != null) return bundled;
        return resolve(System::getProperty, System::getenv, conventional);
    }

    private static boolean hasExplicitConfiguration() {
        return present(System.getProperty(LIBRARY_PROPERTY)) != null
                || present(System.getProperty(MANIFEST_PROPERTY)) != null
                || present(System.getenv(LIBRARY_ENVIRONMENT)) != null
                || present(System.getenv(MANIFEST_ENVIRONMENT)) != null
                || present(System.getProperty(HOME_PROPERTY)) != null
                || present(System.getenv(HOME_ENVIRONMENT)) != null;
    }

    /**
     * Resolves the optional in-JAR artifact for the current platform. The resource layout is
     * {@code academy/native_physics/<platform>/academy_physics.properties} plus the platform
     * library name. Extraction is bounded and verified with the same manifest contract as an
     * external deployment. A missing resource is not an error; a present but invalid pair is.
     */
    public static @Nullable NativePhysicsArtifact bundled() throws IOException {
        var platform = currentPlatform();
        var root = BUNDLED_DIRECTORY + platform + "/";
        var manifestResource = root + MANIFEST_FILE_NAME;
        var libraryResource = root + expectedLibraryName(platform);
        byte[] manifestBytes;
        try (var stream = NativePhysicsArtifact.class.getResourceAsStream(manifestResource)) {
            if (stream == null) return null;
            manifestBytes = stream.readNBytes(MAX_MANIFEST_BYTES + 1);
        }
        if (manifestBytes.length > MAX_MANIFEST_BYTES) throw new IOException("Bundled native physics manifest is too large");
        byte[] libraryBytes;
        // Prefer the text envelope: it is tracked with the source manifest, so a rebuilt
        // binary directory cannot silently pair a new DLL with an older hash.
        try (var encoded = NativePhysicsArtifact.class.getResourceAsStream(libraryResource + ".b64")) {
            if (encoded != null) {
                var base64 = new String(encoded.readNBytes(BASE64_LIMIT), StandardCharsets.US_ASCII);
                try {
                    libraryBytes = java.util.Base64.getMimeDecoder().decode(base64);
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Bundled native physics library is not valid base64", invalid);
                }
            } else {
                try (var stream = NativePhysicsArtifact.class.getResourceAsStream(libraryResource)) {
                    if (stream == null) throw new IOException("Bundled native physics library is missing: " + libraryResource);
                    libraryBytes = stream.readNBytes((int) MAX_LIBRARY_BYTES + 1);
                }
            }
        }
        if (libraryBytes.length == 0 || libraryBytes.length > MAX_LIBRARY_BYTES) {
            throw new IOException("Bundled native physics library has an invalid size");
        }
        return extractBundled(platform, manifestBytes, libraryBytes);
    }

    private static synchronized NativePhysicsArtifact extractBundled(
            String platform, byte[] manifestBytes, byte[] libraryBytes) throws IOException {
        var cached = bundledArtifact;
        if (cached != null) return cached;
        var directory = Files.createTempDirectory("academy-native-physics-bundle-");
        var library = directory.resolve(expectedLibraryName(platform));
        var manifest = directory.resolve(MANIFEST_FILE_NAME);
        Files.write(library, libraryBytes);
        Files.write(manifest, manifestBytes);
        library.toFile().deleteOnExit();
        manifest.toFile().deleteOnExit();
        directory.toFile().deleteOnExit();
        var verified = verify(library, manifest, platform);
        bundledArtifact = verified;
        return verified;
    }

    /**
     * Pure resolution order used by {@link #configured()}: explicit property pair, explicit
     * environment pair, property or environment home, then the conventional game directory.
     * A partially configured pair is an explicit failure, never a silent fallback.
     */
    static NativePhysicsArtifact resolve(Function<String, String> properties,
                                         Function<String, String> environment,
                                         @Nullable Path conventionalDirectory) throws IOException {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(environment, "environment");
        var propertyLibrary = present(properties.apply(LIBRARY_PROPERTY));
        var propertyManifest = present(properties.apply(MANIFEST_PROPERTY));
        if (propertyLibrary != null || propertyManifest != null) {
            if (propertyLibrary == null || propertyManifest == null) {
                throw new IllegalStateException("Native physics requires both -D" + LIBRARY_PROPERTY
                        + " and -D" + MANIFEST_PROPERTY);
            }
            return verify(absolute(propertyLibrary, LIBRARY_PROPERTY),
                    absolute(propertyManifest, MANIFEST_PROPERTY), currentPlatform());
        }
        var environmentLibrary = present(environment.apply(LIBRARY_ENVIRONMENT));
        var environmentManifest = present(environment.apply(MANIFEST_ENVIRONMENT));
        if (environmentLibrary != null || environmentManifest != null) {
            if (environmentLibrary == null || environmentManifest == null) {
                throw new IllegalStateException("Native physics requires both " + LIBRARY_ENVIRONMENT
                        + " and " + MANIFEST_ENVIRONMENT);
            }
            return verify(absolute(environmentLibrary, LIBRARY_ENVIRONMENT),
                    absolute(environmentManifest, MANIFEST_ENVIRONMENT), currentPlatform());
        }
        var home = firstPresent(properties.apply(HOME_PROPERTY), environment.apply(HOME_ENVIRONMENT));
        if (home != null) {
            var homeDirectory = Path.of(home);
            if (!homeDirectory.isAbsolute()) {
                throw new IllegalStateException("Native physics home must be absolute: " + home);
            }
            var artifact = fromHome(homeDirectory, currentPlatform());
            if (artifact == null) {
                throw new IOException("Native physics home does not contain a deployed artifact: " + home);
            }
            return artifact;
        }
        if (conventionalDirectory != null) {
            var artifact = fromHome(conventionalDirectory, currentPlatform());
            if (artifact != null) return artifact;
        }
        throw new IllegalStateException("Native physics is opt-in; set -D" + LIBRARY_PROPERTY + " and -D"
                + MANIFEST_PROPERTY + ", set -D" + HOME_PROPERTY + " or " + HOME_ENVIRONMENT
                + ", or deploy the verified artifact to " + CONVENTIONAL_DIRECTORY
                + "/ in the game directory");
    }

    private static @Nullable Path conventionalDirectory() {
        try {
            var game = FMLPaths.GAMEDIR.get();
            return game == null ? null : game.resolve(CONVENTIONAL_DIRECTORY);
        } catch (RuntimeException | LinkageError unavailable) {
            // Plain unit tests and early startup have no FML paths; the convention is simply absent.
            return null;
        }
    }

    private static @Nullable NativePhysicsArtifact fromHome(Path home, String platform) throws IOException {
        var library = home.resolve(expectedLibraryName(platform));
        var manifest = home.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(library) || !Files.isRegularFile(manifest)) return null;
        return verify(library, manifest, platform);
    }

    private static String expectedLibraryName(String platform) {
        return switch (platform) {
            case "windows-x86_64", "windows-aarch64" -> "academy_physics.dll";
            case "linux-x86_64", "linux-aarch64", "android-x86_64", "android-aarch64" -> "libacademy_physics.so";
            case "macos-x86_64", "macos-aarch64" -> "libacademy_physics.dylib";
            default -> throw new IllegalArgumentException("Unsupported native physics platform: " + platform);
        };
    }

    private static @Nullable String firstPresent(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) return first;
        return second != null && !second.isBlank() ? second : null;
    }

    private static @Nullable String present(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Path absolute(String value, String source) {
        if (value.isBlank()) throw new IllegalStateException("Native physics path is empty: " + source);
        var path = Path.of(value);
        if (!path.isAbsolute()) {
            throw new IllegalStateException("Native physics artifact path must be absolute: " + source);
        }
        return path;
    }

    public static NativePhysicsArtifact verify(Path library, Path manifest, String platform) throws IOException {
        var expectedName = expectedLibraryName(platform);
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

    /**
     * Explicit override first, then JVM OS/arch detection. Android reports Linux on several
     * launchers, so its vendor string is checked before the generic Linux branch. Add a new
     * platform only together with {@link #expectedLibraryName(String)} and a built artifact.
     */
    public static String currentPlatform() {
        var override = present(System.getProperty("academy.nativePhysics.platform"));
        if (override != null) {
            expectedLibraryName(override);
            return override;
        }
        var architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        var bits = switch (architecture) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new IllegalStateException("Unsupported native physics architecture: " + architecture);
        };
        var system = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        if (system.startsWith("windows")) return "windows-" + bits;
        if (system.startsWith("android") || vendor.contains("android")) return "android-" + bits;
        if (system.equals("linux") || system.startsWith("linux")) return "linux-" + bits;
        if (system.startsWith("mac") || system.startsWith("darwin")) return "macos-" + bits;
        throw new IllegalStateException("Unsupported native physics operating system: " + system);
    }
}
