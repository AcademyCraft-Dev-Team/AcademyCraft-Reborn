package org.academy.internal.common.structure.sable;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SableJointIndexTest {
    @Test
    void bothEndpointsDeferUntilTheActualHandleBecomesInvalid() {
        var index = new SableJointIndex<Object, AtomicBoolean>(AtomicBoolean::get);
        var first = new Object();
        var second = new Object();
        var handle = new AtomicBoolean(true);
        index.record(first, handle);
        index.record(second, handle);
        assertTrue(index.hasAny(first));
        assertTrue(index.hasAny(second));
        handle.set(false);
        assertFalse(index.hasAny(first));
        assertFalse(index.hasAny(second));
    }

    @Test
    void bodyIdentityAndPipelineLifetimeDoNotLeakAcrossActivations() {
        var index = new SableJointIndex<String, AtomicBoolean>(AtomicBoolean::get);
        var first = new String("same");
        var nextActivation = new String("same");
        index.record(first, new AtomicBoolean(true));
        assertFalse(index.hasAny(nextActivation));
        index.remove(first);
        assertFalse(index.hasAny(first));
        index.record(nextActivation, new AtomicBoolean(true));
        index.clear();
        assertFalse(index.hasAny(nextActivation));
    }

    @Test
    void unavailableNativeHandleStaysConservativelyConstrained() {
        var index = new SableJointIndex<Object, Object>(ignored -> { throw new IllegalStateException("unavailable"); });
        var body = new Object();
        index.record(body, new Object());
        assertTrue(index.hasAny(body));
    }

    @Test
    void nullWorldEndpointAndRejectedCreationDoNotCreateConstraints() {
        var index = new SableJointIndex<Object, AtomicBoolean>(AtomicBoolean::get);
        var body = new Object();
        index.record(null, new AtomicBoolean(true));
        index.record(body, null);
        assertFalse(index.hasAny(body));
        index.record(body, new AtomicBoolean(true));
        index.record(body, new AtomicBoolean(false));
        assertTrue(index.hasAny(body));
    }
}
