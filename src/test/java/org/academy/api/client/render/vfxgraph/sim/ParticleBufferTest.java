package org.academy.api.client.render.vfxgraph.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBufferTest {
    @Test
    void spawnGrowsCapacity() {
        var buffer = new ParticleBuffer(4);
        for (int i = 0; i < 100; i++) {
            buffer.spawn();
        }
        assertEquals(100, buffer.count());
        assertTrue(buffer.capacity() >= 100);
    }

    @Test
    void killSwapRemovesPreservingSurvivors() {
        var buffer = new ParticleBuffer();
        int a = buffer.spawn();
        int b = buffer.spawn();
        int c = buffer.spawn();

        buffer.setPosition(a, 1f, 0f, 0f);
        buffer.setPosition(b, 2f, 0f, 0f);
        buffer.setPosition(c, 3f, 0f, 0f);

        buffer.kill(a);

        assertEquals(2, buffer.count());
        // 末位 (c) 移入 a 槽，b 保留
        assertEquals(3f, buffer.positionX(0));
        assertEquals(2f, buffer.positionX(1));
    }

    @Test
    void accessorsRoundTrip() {
        var buffer = new ParticleBuffer();
        int i = buffer.spawn();
        buffer.setPosition(i, 1f, 2f, 3f);
        buffer.setVelocity(i, 4f, 5f, 6f);
        buffer.setColor(i, 0.1f, 0.2f, 0.3f, 0.4f);
        buffer.setSize(i, 0.5f);
        buffer.setLifetime(i, 2f);
        buffer.setAge(i, 1f);

        assertEquals(1f, buffer.positionX(i));
        assertEquals(6f, buffer.velocityZ(i));
        assertEquals(0.4f, buffer.alpha(i));
        assertEquals(0.5f, buffer.startSize(i));
        assertEquals(2f, buffer.lifetime(i));
    }

    @Test
    void layerDefaultsToFireAndSurvivesSwapRemove() {
        var buffer = new ParticleBuffer();
        int a = buffer.spawn();
        int b = buffer.spawn();
        assertEquals((byte) 0, buffer.layer(a));
        buffer.setLayer(b, (byte) 1);
        assertEquals((byte) 1, buffer.layer(b));

        // swap-remove 后 layer 跟随粒子
        buffer.kill(a);
        assertEquals((byte) 1, buffer.layer(0));
    }
}
