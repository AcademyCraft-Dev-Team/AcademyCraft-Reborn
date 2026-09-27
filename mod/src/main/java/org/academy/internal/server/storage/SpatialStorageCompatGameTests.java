package org.academy.internal.server.storage;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 26.3 NOTE: Curios 16.0.0 (26.2) still extends the removed NeoForge
 * {@code net.neoforged.neoforge.items.IItemHandlerModifiable}, so this optional integration
 * test cannot compile until a 26.3-compatible Curios build is available. The checks are kept
 * as a no-op so the base {@code SpatialStorageGameTests} flow still runs.
 */
final class SpatialStorageCompatGameTests {
    private SpatialStorageCompatGameTests() {
    }

    static void verify(GameTestHelper helper, ServerPlayer player, ItemStack unit, UUID id) {
        // Disabled pending a 26.3-compatible Curios release.
    }
}
