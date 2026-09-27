package org.academy.internal.client.time;

import org.academy.api.client.input.TemporalInputAccess;
import org.academy.internal.server.time.TemporalControlProvenance;

import java.util.Map;

/**
 * Binds the public {@link TemporalInputAccess} facade to the internal temporal input state.
 */
public final class TemporalInputAccessBridge implements TemporalInputAccess.Bridge {
    private final TemporalBindingRestrictions restrictions = new TemporalBindingRestrictions();

    @Override
    public boolean isLocalExternallyImmune() {
        return TemporalClientRuntime.isLocalExternallyImmune();
    }

    @Override
    public boolean isExternalControl() {
        return TemporalControlProvenance.isExternalControl();
    }

    @Override
    public void ownedWrite(String keyName) {
        restrictions.ownedWrite(keyName);
    }

    @Override
    public boolean externalWrite(String keyName, boolean enabled, boolean current, boolean immune) {
        return restrictions.externalWrite(keyName, enabled, current, immune);
    }

    @Override
    public Map<String, Boolean> effectiveStates(boolean immune) {
        return restrictions.effectiveStates(immune);
    }

    @Override
    public Map<String, Boolean> clear() {
        return restrictions.clear();
    }
}
