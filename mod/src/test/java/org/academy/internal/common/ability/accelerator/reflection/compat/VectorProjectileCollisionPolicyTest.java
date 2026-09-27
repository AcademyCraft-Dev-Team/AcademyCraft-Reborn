package org.academy.internal.common.ability.accelerator.reflection.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorProjectileCollisionPolicyTest {
    @Test
    void blocksOnlyConfiguredOwnerfulHitsAgainstOwnerlessSameTypePeers() {
        assertTrue(VectorProjectileCollisionPolicy.blocksOwnerlessPeerCollision(
                true, true, false, true));

        assertFalse(VectorProjectileCollisionPolicy.blocksOwnerlessPeerCollision(
                false, true, false, true));
        assertFalse(VectorProjectileCollisionPolicy.blocksOwnerlessPeerCollision(
                true, false, false, true));
        assertFalse(VectorProjectileCollisionPolicy.blocksOwnerlessPeerCollision(
                true, true, true, true));
        assertFalse(VectorProjectileCollisionPolicy.blocksOwnerlessPeerCollision(
                true, true, false, false));
    }
}
