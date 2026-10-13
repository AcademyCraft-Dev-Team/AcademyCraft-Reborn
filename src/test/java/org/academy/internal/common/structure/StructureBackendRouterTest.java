package org.academy.internal.common.structure;

import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.internal.common.structure.space.NativeStructureManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StructureBackendRouterTest {
    @TempDir Path directory;

    @Test void installationPreflightRetainsEveryKindOfNativeOwnership() {
        var empty = new NativeStructureManifest.Preflight(0, false, true);
        var selected = StructureBackendRouter.decide(true, empty, "tested");
        assertEquals(StructureBackendStatus.Mode.SABLE, selected.mode());
        assertTrue(selected.sableQueriesAvailable());
        for (var existing : new NativeStructureManifest.Preflight[] {
                new NativeStructureManifest.Preflight(2, true, true),
                new NativeStructureManifest.Preflight(0, true, true),
                new NativeStructureManifest.Preflight(0, false, false)}) {
            var result = StructureBackendRouter.decide(true, existing, "retained");
            assertEquals(StructureBackendStatus.Mode.MIGRATION_REQUIRED, result.mode());
            assertEquals(existing.structures(), result.storedNativeStructures());
            assertTrue(result.sableQueriesAvailable());
        }
        var unavailable = StructureBackendRouter.decide(false, empty, "unknown artifact");
        assertEquals(StructureBackendStatus.Mode.INCOMPATIBLE, unavailable.mode());
        assertFalse(unavailable.sableQueriesAvailable());
    }

    @Test void identicalVersionAndLengthCannotBypassTheArtifactContract() throws Exception {
        var path = directory.resolve("sable.jar");
        byte[] audited = {1, 2, 3, 4};
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(audited));
        Files.write(path, audited);
        assertTrue(StructureBackendRouter.matchesArtifact(path, hash));
        Files.write(path, new byte[] {1, 2, 3, 5});
        assertFalse(StructureBackendRouter.matchesArtifact(path, hash));
        assertFalse(StructureBackendRouter.matchesArtifact(directory.resolve("missing.jar"), hash));
        assertFalse(StructureBackendRouter.matchesArtifact(directory, hash));
    }
}
