package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlowCircleVfxReplicationTest {
    @Test
    void serverProducersEmitSnapshotsInsteadOfTrackedEntities() throws IOException {
        var reflection = source(
                "internal/common/ability/accelerator/skills/lv4/VectorReflection.java");
        var deviation = source(
                "internal/common/ability/accelerator/skills/lv3/VectorDeviation.java");
        var kinetic = source(
                "internal/common/ability/accelerator/skills/lv2/KineticEnergyApplied.java");

        assertTrue(reflection.contains("SkillVfxService.distortionRing("));
        assertTrue(deviation.contains("VectorReflection.Server.spawnGlowCircle("));
        assertTrue(kinetic.contains("SkillVfxService.distortionRing("));
        assertFalse(reflection.contains("new GlowCircle("));
        assertFalse(kinetic.contains("new GlowCircle("));
        assertFalse(reflection.contains("addFreshEntity(glowCircle)"));
        assertFalse(kinetic.contains("addFreshEntity(glowCircle)"));
    }

    @Test
    void clientPlaybackOwnsTheDetachedMirrorLifecycle() throws IOException {
        var playback = source("internal/client/render/vfx/GlowCircleVfx.java");
        var mirror = source("internal/common/world/entity/skill/GlowCircle.java");

        assertTrue(playback.contains("new GlowCircle("));
        assertFalse(playback.contains("addFreshEntity("));
        assertFalse(playback.contains("discard()"));
        assertTrue(mirror.contains("public boolean broadcastToPlayer(ServerPlayer player) { return false; }"));
    }

    @Test
    void onlyDetachedClientPlaybackConstructsGlowCircle() throws IOException {
        var sourceRoot = Path.of("src/main/java");
        try (var files = Files.walk(sourceRoot)) {
            var constructors = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(GlowCircleVfxReplicationTest::constructsGlowCircle)
                    .map(sourceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .toList();
            assertEquals(List.of(
                    "org/academy/internal/client/render/vfx/GlowCircleVfx.java"), constructors);
        }
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/java/org/academy", relativePath));
    }

    private static boolean constructsGlowCircle(Path path) {
        try {
            return Files.readString(path).contains("new GlowCircle(");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
