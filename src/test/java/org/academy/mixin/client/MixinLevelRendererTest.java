package org.academy.mixin.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinLevelRendererTest {
    @Test
    void retainsTheIrisCompatibleEndOfLevelHook() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/mixin/client/MixinLevelRenderer.java"));

        assertTrue(source.contains("order = Integer.MAX_VALUE"));
        assertTrue(source.contains("method = \"render\""));
        assertTrue(source.contains(
                "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"));
        assertTrue(source.contains("VfxManager.INSTANCE.renderFrame();"));
        assertTrue(source.contains("GlowEffect.getInstance().process();"));
        assertTrue(source.contains("PostEffect.pre();"));
        assertTrue(source.contains("PostEffect.post();"));
    }

    @Test
    void keepsSpatialPostProcessingInTheLevelRendererHookOnly() throws IOException {
        var levelRenderer = Files.readString(Path.of(
                "src/main/java/org/academy/mixin/client/MixinLevelRenderer.java"));
        var gameRenderer = Files.readString(Path.of(
                "src/main/java/org/academy/mixin/client/MixinGameRenderer.java"));

        assertTrue(levelRenderer.contains("SpacialExcisionVfxClient.prepareSourceValidation();"));
        assertTrue(levelRenderer.contains("SpacialExcisionVfxClient.renderPost();"));
        assertFalse(gameRenderer.contains("VfxManager.INSTANCE.renderFrame();"));
        assertFalse(gameRenderer.contains("SpacialExcisionVfxClient.prepareSourceValidation();"));
        assertFalse(gameRenderer.contains("SpacialExcisionVfxClient.renderPost();"));
        assertFalse(gameRenderer.contains("PostEffect.pre();"));
        assertFalse(gameRenderer.contains("PostEffect.post();"));
        assertFalse(gameRenderer.contains("GlowEffect.getInstance().process();"));
    }
}
