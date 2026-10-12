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
    void missingOptInNeverAttemptsNativeLoading() {
        var previous = System.clearProperty(NativePhysicsArtifact.LIBRARY_PROPERTY);
        try {
            var failure = assertThrows(IllegalStateException.class, NativePhysicsLibrary::openConfigured);
            assertTrue(failure.getMessage().contains("opt-in"));
            assertTrue(failure.getMessage().contains(NativePhysicsArtifact.LIBRARY_PROPERTY));
        } finally {
            if (previous != null) System.setProperty(NativePhysicsArtifact.LIBRARY_PROPERTY, previous);
        }
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
        for (var invalid : new String[]{original.replace("abi=1", "abi=2"),
                original.replace(NativePhysicsArtifact.ENGINE, "rapier3d-0.36.1"),
                original.replace("windows-x86_64", "linux-x86_64"),
                original + "abi=1\n", original + "fallback=true\n"}) {
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

    private Path writeManifest(Path library, String extra) throws IOException {
        return Files.writeString(directory.resolve("artifact.properties"), "format=academy-native-physics-v1\nabi=1\nengine="
                + NativePhysicsArtifact.ENGINE + "\nplatform=windows-x86_64\nlibrary=academy_physics.dll\nsha256="
                + NativePhysicsArtifact.digest(library) + "\n" + extra);
    }
}
