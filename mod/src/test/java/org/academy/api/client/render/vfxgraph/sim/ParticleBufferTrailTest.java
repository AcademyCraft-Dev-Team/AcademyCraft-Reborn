package org.academy.api.client.render.vfxgraph.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBufferTrailTest {

    @Test
    void rotationAndMassStoredAndMoved() {
        var buffer = new ParticleBuffer(2);
        var a = buffer.spawn();
        var b = buffer.spawn();
        buffer.setRotation(a, 1.5f);
        buffer.setMass(a, 3f);
        buffer.setRotation(b, 0.5f);
        buffer.setMass(b, 9f);
        // kill a（swap b 进入 a 槽）
        buffer.kill(a);
        assertEquals(0.5f, buffer.rotation(a));
        assertEquals(9f, buffer.mass(a));
    }

    @Test
    void trailPushStoresMostRecentFirst() {
        var buffer = new ParticleBuffer(2);
        var i = buffer.spawn();
        buffer.pushTrail(i, 1f, 0f, 0f);
        buffer.pushTrail(i, 2f, 0f, 0f);
        buffer.pushTrail(i, 3f, 0f, 0f);
        assertEquals(3, buffer.trailSize(i));
        assertEquals(3f, buffer.trailX(i, 0));
        assertEquals(2f, buffer.trailX(i, 1));
        assertEquals(1f, buffer.trailX(i, 2));
    }

    @Test
    void trailCappedAtLength() {
        var buffer = new ParticleBuffer(2);
        var i = buffer.spawn();
        for (var k = 0; k < ParticleBuffer.TRAIL_LENGTH + 5; k++) {
            buffer.pushTrail(i, k, 0f, 0f);
        }
        assertEquals(ParticleBuffer.TRAIL_LENGTH, buffer.trailSize(i));
    }

    @Test
    void trailMovedOnKill() {
        var buffer = new ParticleBuffer(2);
        var a = buffer.spawn();
        var b = buffer.spawn();
        buffer.pushTrail(a, 1f, 0f, 0f);
        buffer.pushTrail(a, 2f, 0f, 0f);
        buffer.pushTrail(b, 7f, 0f, 0f);
        buffer.kill(a);
        assertEquals(1, buffer.trailSize(a));
        assertEquals(7f, buffer.trailX(a, 0));
    }

    @Test
    void trailSurvivesGrow() {
        var buffer = new ParticleBuffer(2);
        var i = buffer.spawn();
        buffer.pushTrail(i, 5f, 0f, 0f);
        for (var k = 0; k < 8; k++) buffer.spawn(); // 触发扩容
        assertEquals(1, buffer.trailSize(i));
        assertEquals(5f, buffer.trailX(i, 0));
        assertTrue(buffer.capacity() >= 16);
    }
}
