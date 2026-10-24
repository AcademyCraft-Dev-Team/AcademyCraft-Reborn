package org.academy.internal.server.world.level.storage;

import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaNetworkGovernancePermissionTest {
    @AfterEach
    void clear() {
        MisakaNetworkGovernance.testingInstall(null);
    }

    @Test
    void nonAdminCannotSatisfySatelliteManage() {
        var gov = new MisakaNetworkGovernance();
        MisakaNetworkGovernance.testingInstall(gov);
        UUID network = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        gov.onFirstIntegration(network, admin, "admin");
        gov.grant(network, member, "member", MisakaNetworkPermission.ACCESS);

        assertTrue(gov.hasPermission(admin, network, MisakaNetworkPermission.SATELLITE_MANAGE));
        assertFalse(gov.hasPermission(member, network, MisakaNetworkPermission.SATELLITE_MANAGE));
        assertTrue(gov.hasPermission(member, network, MisakaNetworkPermission.ACCESS));
    }

    @Test
    void revokeRemovesAccess() {
        var gov = new MisakaNetworkGovernance();
        MisakaNetworkGovernance.testingInstall(gov);
        UUID network = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        gov.grant(network, player, "p", MisakaNetworkPermission.ACCESS);
        assertTrue(gov.hasPermission(player, network, MisakaNetworkPermission.ACCESS));
        gov.revoke(network, player, MisakaNetworkPermission.ACCESS);
        assertFalse(gov.hasPermission(player, network, MisakaNetworkPermission.ACCESS));
    }
}
