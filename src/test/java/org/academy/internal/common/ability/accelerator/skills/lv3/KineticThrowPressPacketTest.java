package org.academy.internal.common.ability.accelerator.skills.lv3;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KineticThrowPressPacketTest {
    @Test
    void queuedPressesOnlyActOncePerPlayerAndServerTick() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        assertTrue(KineticThrow.Server.acceptPacketPress(first, 42157));
        for (int i = 0; i < 3; i++) assertFalse(KineticThrow.Server.acceptPacketPress(first, 42157));
        assertTrue(KineticThrow.Server.acceptPacketPress(second, 42157));
        assertTrue(KineticThrow.Server.acceptPacketPress(first, 42158));
    }
}
