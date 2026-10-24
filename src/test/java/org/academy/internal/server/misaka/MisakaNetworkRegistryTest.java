package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import org.academy.internal.server.world.level.storage.MisakaNetworkRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MisakaNetworkRegistryTest {
    @AfterEach
    void clear() {
        MisakaNetworkRegistry.testingInstall(null);
    }

    @Test
    void mergeInheritsUuidOfLargerSide() {
        var registry = new MisakaNetworkRegistry();
        MisakaNetworkRegistry.testingInstall(registry);

        var a = new BlockPos(0, 64, 0);
        var b = new BlockPos(1, 64, 0);
        var c = new BlockPos(2, 64, 0);

        UUID large = registry.assignComponent(Set.of(a, b));
        UUID small = UUID.randomUUID();
        registry.testingBind(c, small);

        UUID merged = registry.assignComponent(Set.of(a, b, c));
        assertEquals(large, merged);
        assertEquals(large, registry.get(a).orElseThrow());
        assertEquals(large, registry.get(b).orElseThrow());
        assertEquals(large, registry.get(c).orElseThrow());
    }

    @Test
    void splitLargerFragmentKeepsUuidSmallerGetsNew() {
        var registry = new MisakaNetworkRegistry();
        MisakaNetworkRegistry.testingInstall(registry);

        var a = new BlockPos(0, 0, 0);
        var b = new BlockPos(1, 0, 0);
        var c = new BlockPos(10, 0, 0);
        UUID shared = registry.assignComponent(Set.of(a, b, c));

        UUID left = registry.assignComponent(Set.of(a, b));
        UUID right = registry.assignComponent(Set.of(c));

        assertEquals(shared, left);
        assertFalse(shared.equals(right));
        assertEquals(shared, registry.get(a).orElseThrow());
        assertEquals(right, registry.get(c).orElseThrow());
    }

    @Test
    void freshComponentCreatesNewUuid() {
        var registry = new MisakaNetworkRegistry();
        UUID first = registry.assignComponent(Set.of(new BlockPos(5, 5, 5)));
        UUID second = registry.assignComponent(Set.of(new BlockPos(9, 9, 9)));
        assertFalse(first.equals(second));
    }
}
