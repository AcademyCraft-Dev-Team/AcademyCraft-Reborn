package org.academy.internal.common.ability.accelerator.reflection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class VectorHealthLedgerAuthorizationTest {
    @Test
    void unrelatedCallerCannotSubmitAnAuthorizedReduction() {
        var invoked = new boolean[1];
        assertFalse(VectorHealthLedger.writeAuthorized(null, 1.0f, () -> {
            invoked[0] = true;
            return true;
        }));
        assertFalse(invoked[0]);
    }
}
