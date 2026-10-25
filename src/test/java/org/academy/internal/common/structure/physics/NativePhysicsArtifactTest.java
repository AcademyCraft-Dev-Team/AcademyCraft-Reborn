package org.academy.internal.common.structure.physics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativePhysicsArtifactTest {
    @TempDir Path directory;

    @Test
    @ResourceLock("systemProperties")
    void brokenExplicitConfigurationNeverFallsBackToTheBundledArtifact() {
        var previousLibrary = System.getProperty(NativePhysicsArtifact.LIBRARY_PROPERTY);
        var previousManifest = System.getProperty(NativePhysicsArtifact.MANIFEST_PROPERTY);
        try {
            System.setProperty(NativePhysicsArtifact.LIBRARY_PROPERTY,
                    directory.resolve("missing.dll").toAbsolutePath().toString());
            System.setProperty(NativePhysicsArtifact.MANIFEST_PROPERTY,
                    directory.resolve("missing.properties").toAbsolutePath().toString());
            var failure = assertThrows(IOException.class, NativePhysicsLibrary::openConfigured);
            assertNotNull(failure.getMessage());
        } finally {
            if (previousLibrary == null) System.clearProperty(NativePhysicsArtifact.LIBRARY_PROPERTY);
            else System.setProperty(NativePhysicsArtifact.LIBRARY_PROPERTY, previousLibrary);
            if (previousManifest == null) System.clearProperty(NativePhysicsArtifact.MANIFEST_PROPERTY);
            else System.setProperty(NativePhysicsArtifact.MANIFEST_PROPERTY, previousManifest);
        }
    }

    @Test
    void bundledArtifactIsDecodedAndMatchesItsManifestOnWindows() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        var artifact = NativePhysicsArtifact.bundled();
        assertNotNull(artifact);
        assertEquals("windows-x86_64", artifact.platform());
        assertTrue(Files.isRegularFile(artifact.library()));
        assertEquals(NativePhysicsArtifact.digest(artifact.library()), artifact.sha256());
    }

    @Test
    void validatesExactArtifactWithoutLoadingItsArbitraryBytes() throws IOException {
        var library = Files.write(directory.resolve("academy_physics.dll"), new byte[]{1, 2, 3});
        var manifest = writeManifest(library, "");
        var verified = NativePhysicsArtifact.verify(library, manifest, "windows-x86_64");
        assertEquals(library.toRealPath(), verified.library());
        assertEquals(NativePhysicsArtifact.digest(library), verified.sha256());
        Files.write(library, new byte[]{1, 2, 4});
        assertThrows(IOException.class, () -> NativePhysicsArtifact.verify(library, manifest, "windows-x86_64"));
    }

    @Test
    void rejectsAbiEnginePlatformDuplicateAndUnrecognizedFields() throws IOException {
        var library = Files.write(directory.resolve("academy_physics.dll"), new byte[]{1, 2, 3});
        var manifest = writeManifest(library, "rustVersion=1.99.0\nsourceSha256=source-evidence\ntarget=x86_64-pc-windows-msvc\n");
        var original = Files.readString(manifest);
        for (var invalid : new String[]{original.replace("abi=2", "abi=1"),
                original.replace(NativePhysicsArtifact.ENGINE, "rapier3d-0.36.1"),
                original.replace("windows-x86_64", "linux-x86_64"),
                original + "abi=2\n", original + "fallback=true\n"}) {
            Files.writeString(manifest, invalid);
            assertThrows(IOException.class, () -> NativePhysicsArtifact.verify(library, manifest, "windows-x86_64"));
        }
        Files.writeString(manifest, original);
        assertDoesNotThrow(() -> NativePhysicsArtifact.verify(library, manifest, "windows-x86_64"));
    }

    @Test
    void rejectsOversizedManifestAndUnsupportedArchitectureBeforeLinking() throws IOException {
        var library = Files.write(directory.resolve("academy_physics.dll"), new byte[]{1});
        var manifest = Files.writeString(directory.resolve("artifact.properties"), "#".repeat(16_385));
        assertThrows(IOException.class, () -> NativePhysicsArtifact.verify(library, manifest, "windows-x86_64"));
        assertThrows(IllegalArgumentException.class, () -> NativePhysicsArtifact.verify(library, manifest, "windows-arm64"));
    }

    @Test
    void resolvesExplicitPropertiesEnvironmentHomeAndConventionalDirectory() throws IOException {
        var libraryName = System.mapLibraryName("academy_physics");
        var propertyHome = deployment("property_home");
        var environmentHome = deployment("environment_home");
        var convention = deployment("convention");

        var conventional = NativePhysicsArtifact.resolve(name -> null, name -> null, convention);
        assertEquals(convention.resolve(libraryName).toRealPath(), conventional.library());

        var fromEnvironment = NativePhysicsArtifact.resolve(name -> null, name -> switch (name) {
            case NativePhysicsArtifact.LIBRARY_ENVIRONMENT ->
                    environmentHome.resolve(libraryName).toString();
            case NativePhysicsArtifact.MANIFEST_ENVIRONMENT ->
                    environmentHome.resolve(NativePhysicsArtifact.MANIFEST_FILE_NAME).toString();
            default -> null;
        }, convention);
        assertEquals(environmentHome.resolve(libraryName).toRealPath(), fromEnvironment.library());

        var fromProperties = NativePhysicsArtifact.resolve(name -> switch (name) {
            case NativePhysicsArtifact.LIBRARY_PROPERTY -> propertyHome.resolve(libraryName).toString();
            case NativePhysicsArtifact.MANIFEST_PROPERTY ->
                    propertyHome.resolve(NativePhysicsArtifact.MANIFEST_FILE_NAME).toString();
            default -> null;
        }, name -> switch (name) {
            case NativePhysicsArtifact.LIBRARY_ENVIRONMENT ->
                    environmentHome.resolve(libraryName).toString();
            case NativePhysicsArtifact.MANIFEST_ENVIRONMENT ->
                    environmentHome.resolve(NativePhysicsArtifact.MANIFEST_FILE_NAME).toString();
            default -> null;
        }, convention);
        assertEquals(propertyHome.resolve(libraryName).toRealPath(), fromProperties.library());

        var fromHome = NativePhysicsArtifact.resolve(
                name -> NativePhysicsArtifact.HOME_PROPERTY.equals(name) ? environmentHome.toString() : null,
                name -> null, convention);
        assertEquals(environmentHome.resolve(libraryName).toRealPath(), fromHome.library());

        var fromHomeEnvironment = NativePhysicsArtifact.resolve(name -> null,
                name -> NativePhysicsArtifact.HOME_ENVIRONMENT.equals(name) ? convention.toString() : null, null);
        assertEquals(convention.resolve(libraryName).toRealPath(), fromHomeEnvironment.library());
    }

    @Test
    void partialOrBrokenExplicitConfigurationFailsInsteadOfFallingBack() {
        var partial = assertThrows(IllegalStateException.class, () -> NativePhysicsArtifact.resolve(
                name -> NativePhysicsArtifact.LIBRARY_PROPERTY.equals(name)
                        ? directory.resolve("missing.dll").toString() : null, name -> null, null));
        assertTrue(partial.getMessage().contains(NativePhysicsArtifact.MANIFEST_PROPERTY));
        assertThrows(IllegalStateException.class, () -> NativePhysicsArtifact.resolve(name -> null,
                name -> NativePhysicsArtifact.MANIFEST_ENVIRONMENT.equals(name) ? "manifest" : null, null));
        assertThrows(IllegalStateException.class, () -> NativePhysicsArtifact.resolve(
                name -> NativePhysicsArtifact.LIBRARY_PROPERTY.equals(name) ? "relative.dll" : null,
                name -> NativePhysicsArtifact.MANIFEST_PROPERTY.equals(name) ? "relative.properties" : null, null));
        assertThrows(IOException.class, () -> NativePhysicsArtifact.resolve(
                name -> NativePhysicsArtifact.HOME_PROPERTY.equals(name) ? directory.toString() : null,
                name -> null, null));
        assertThrows(IllegalStateException.class, () -> NativePhysicsArtifact.resolve(
                name -> NativePhysicsArtifact.HOME_PROPERTY.equals(name) ? "relative/home" : null,
                name -> null, null));
    }

    @Test
    void nothingConfiguredStaysOptIn() {
        var failure = assertThrows(IllegalStateException.class,
                () -> NativePhysicsArtifact.resolve(name -> null, name -> null, null));
        assertTrue(failure.getMessage().contains("opt-in"));
        assertTrue(failure.getMessage().contains(NativePhysicsArtifact.CONVENTIONAL_DIRECTORY));
    }

    private Path deployment(String name) throws IOException {
        var home = directory.resolve(name);
        Files.createDirectories(home);
        var library = Files.write(home.resolve(System.mapLibraryName("academy_physics")), new byte[]{1, 2, 3});
        writeManifest(home.resolve(NativePhysicsArtifact.MANIFEST_FILE_NAME), library);
        return home;
    }

    private Path writeManifest(Path library, String extra) throws IOException {
        return writeManifest(directory.resolve("artifact.properties"), library, extra);
    }

    private Path writeManifest(Path target, Path library) throws IOException {
        return Files.writeString(target, "format=academy-native-physics-v1\nabi=2\nengine="
                + NativePhysicsArtifact.ENGINE + "\nplatform=" + NativePhysicsArtifact.currentPlatform()
                + "\nlibrary=" + library.getFileName() + "\nsha256="
                + NativePhysicsArtifact.digest(library) + "\n");
    }

    private Path writeManifest(Path target, Path library, String extra) throws IOException {
        return Files.writeString(target, "format=academy-native-physics-v1\nabi=2\nengine="
                + NativePhysicsArtifact.ENGINE + "\nplatform=windows-x86_64\nlibrary=academy_physics.dll\nsha256="
                + NativePhysicsArtifact.digest(library) + "\n" + extra);
    }
}
