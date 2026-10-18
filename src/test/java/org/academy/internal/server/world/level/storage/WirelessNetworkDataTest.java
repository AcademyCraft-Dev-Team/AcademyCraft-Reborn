package org.academy.internal.server.world.level.storage;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WirelessNetworkDataTest {
    @Test
    void ensureNodeRegisteredCreatesDefaultConfigAndIsIdempotent() {
        var data = new WirelessNetworkData();
        var pos = new BlockPos(5, 64, -7);

        var config = data.ensureNodeRegistered(pos);

        assertNotNull(config);
        assertEquals(WirelessNetworkData.defaultNodeName(pos), config.name);
        assertEquals(WirelessNetworkData.DEFAULT_PASSWORD, config.password);
        assertEquals(WirelessNetworkData.DEFAULT_RADIUS, config.radius);
        assertEquals(WirelessNetworkData.DEFAULT_MAX_CONNECTIONS, config.maxConnections);
        assertTrue(config.connectedUsers.isEmpty());
        assertSame(config, data.ensureNodeRegistered(pos));
        assertEquals(1, data.getAllNodes().size());
    }

    @Test
    void ensureNodeRegisteredSuffixesNameWhenDefaultNameIsTaken() {
        var data = new WirelessNetworkData();
        var pos = new BlockPos(5, 64, -7);
        assertTrue(data.registerNode(new BlockPos(1, 2, 3), WirelessNetworkData.defaultNodeName(pos),
                WirelessNetworkData.DEFAULT_PASSWORD, WirelessNetworkData.DEFAULT_RADIUS,
                WirelessNetworkData.DEFAULT_MAX_CONNECTIONS));

        var config = data.ensureNodeRegistered(pos);

        assertNotNull(config);
        assertEquals(WirelessNetworkData.defaultNodeName(pos) + "_1", config.name);
        assertSame(config, data.getNodeConfig(pos));
        assertEquals(2, data.getAllNodes().size());
    }

    @Test
    void ensureNodeRegisteredKeepsAnExistingConfiguration() {
        var data = new WirelessNetworkData();
        var pos = new BlockPos(5, 64, -7);
        assertTrue(data.registerNode(pos, "Custom", "secret", 16, 4));

        var config = data.ensureNodeRegistered(pos);

        assertSame(data.getNodeConfig(pos), config);
        assertEquals("Custom", config.name);
        assertEquals("secret", config.password);
        assertEquals(16, config.radius);
        assertEquals(4, config.maxConnections);
        assertEquals(1, data.getAllNodes().size());
    }
}
