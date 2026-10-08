package org.academy.api.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceModelPolicyTest {
    private final DistanceModelPolicy policy = new DistanceModelPolicy(16, 24, 2, 4);

    @Test
    void boundariesKeepNearerDetailAndNeverAlterCulling() {
        assertEquals(DistanceModelPolicy.Detail.FULL, policy.detail(16 * 16));
        assertEquals(DistanceModelPolicy.Detail.REDUCED, policy.detail(16.01 * 16.01));
        assertEquals(DistanceModelPolicy.Detail.REDUCED, policy.detail(24 * 24));
        assertEquals(DistanceModelPolicy.Detail.COARSE, policy.detail(24.01 * 24.01));
        assertEquals(DistanceModelPolicy.Detail.COARSE, policy.detail(10000));
        assertEquals(DistanceModelPolicy.Detail.FULL, policy.detail(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DistanceModelPolicy(24, 16, 2, 4));
    }

    @Test
    void staggeredSamplingIsPurePerEntityAndFullDetailRemainsExact() {
        assertEquals(31.5f, policy.sampleAge(31.5f, 9, DistanceModelPolicy.Detail.FULL));
        var first = policy.sampleAge(31.5f, 0, DistanceModelPolicy.Detail.REDUCED);
        assertEquals(first, policy.sampleAge(31.9f, 0, DistanceModelPolicy.Detail.REDUCED));
        assertNotEquals(first, policy.sampleAge(31.5f, 1, DistanceModelPolicy.Detail.REDUCED));
        policy.sampleAge(77.1f, -4, DistanceModelPolicy.Detail.COARSE);
        assertEquals(first, policy.sampleAge(31.5f, 0, DistanceModelPolicy.Detail.REDUCED));
    }
}
