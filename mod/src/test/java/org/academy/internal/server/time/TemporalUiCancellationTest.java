package org.academy.internal.server.time;

import org.academy.api.client.input.UiInputContext;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

class TemporalUiCancellationTest {
    @Test
    void guiHudScopeForwardsCancellationAndRestoresNestedScopeAfterFailure() {
        var original = new CallbackInfo("keyPress", true);
        var proxy = TemporalBoundaryProtection.protectCallback(original, true, UiInputContext::isActive);
        proxy.cancel();
        assertFalse(original.isCancelled());
        assertThrows(IllegalStateException.class, () -> UiInputContext.run(() -> {
            UiInputContext.run(proxy::cancel);
            assertTrue(original.isCancelled());
            throw new IllegalStateException("fixture");
        }));
        assertFalse(UiInputContext.isActive());
    }
}
