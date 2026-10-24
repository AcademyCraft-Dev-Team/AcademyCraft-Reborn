package org.academy.internal.common.misaka;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaNetworkPermissionTest {
    @Test
    void ownerImpliesAll() {
        for (var perm : MisakaNetworkPermission.values()) {
            assertTrue(MisakaNetworkPermission.OWNER.implies(perm));
        }
    }

    @Test
    void adminImpliesAllExceptOwner() {
        for (var perm : MisakaNetworkPermission.values()) {
            if (perm == MisakaNetworkPermission.OWNER) {
                assertFalse(MisakaNetworkPermission.ADMIN.implies(perm));
            } else {
                assertTrue(MisakaNetworkPermission.ADMIN.implies(perm));
            }
        }
    }

    @Test
    void accessDoesNotImplyConfigure() {
        assertFalse(MisakaNetworkPermission.ACCESS.implies(MisakaNetworkPermission.CONFIGURE));
        assertTrue(MisakaNetworkPermission.ACCESS.implies(MisakaNetworkPermission.ACCESS));
    }

    @Test
    void anyImpliesScansHeldSet() {
        assertTrue(MisakaNetworkPermission.anyImplies(
                Set.of(MisakaNetworkPermission.ACCESS, MisakaNetworkPermission.ADMIN),
                MisakaNetworkPermission.ENTITY_CONFIG
        ));
        assertFalse(MisakaNetworkPermission.anyImplies(
                Set.of(MisakaNetworkPermission.ACCESS),
                MisakaNetworkPermission.DESTROY
        ));
    }
}
