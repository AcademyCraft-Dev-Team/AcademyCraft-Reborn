package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillVfxClientLifecycleTest {
    @Test
    void detachedMirrorsUseVfxCleanupInsteadOfEntityRemoval() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/client/render/vfx/SkillVfxClient.java"));

        assertFalse(source.contains(".discard()"));
        assertTrue(source.contains("replica.release();"));
        assertTrue(source.contains("ACTIVE.values().forEach(Replica::release);"));
        assertTrue(source.contains("PlasmaVfxClient.release((Plasma) entity);"));
    }
}
