package org.academy.api.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ClientEventSubscriberDistTest {
    @Test
    void renderPipelineSubscribersAreClientOnly() {
        assertClientOnly(Render.RenderPipelines.class);
        assertClientOnly(VfxPipelines.class);
    }

    private static void assertClientOnly(Class<?> subscriberClass) {
        var annotation = subscriberClass.getAnnotation(EventBusSubscriber.class);
        assertArrayEquals(new Dist[]{Dist.CLIENT}, annotation.value());
    }
}
