package org.academy.internal.common.ability.accelerator.skills.lv5;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatinumExecutionCleanupTest {
    @Test
    void detachesBossEventAndRemovesAllViewers() {
        var event = new ServerBossEvent(
                UUID.randomUUID(),
                Component.literal("Test"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        event.setVisible(true);

        assertTrue(PlatinumExecutionCleanup.detachBossEvent(event));
        assertFalse(event.isVisible());
        assertTrue(event.getPlayers().isEmpty());
    }
}
