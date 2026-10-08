package org.academy.api.client.model;

/** Entity-neutral visual detail and staggered animation sampling; never changes simulation or culling. */
public record DistanceModelPolicy(float fullDistance, float coarseDistance,
                                  int reducedSampleTicks, int coarseSampleTicks) {
    public enum Detail { FULL, REDUCED, COARSE }

    public DistanceModelPolicy {
        if (!Float.isFinite(fullDistance) || !Float.isFinite(coarseDistance)
                || fullDistance <= 0 || coarseDistance <= fullDistance
                || reducedSampleTicks < 1 || coarseSampleTicks < reducedSampleTicks) {
            throw new IllegalArgumentException("Invalid model detail policy");
        }
    }

    /** Exact boundaries belong to the nearer tier; invalid distance preserves full detail. */
    public Detail detail(double distanceSquared) {
        if (Double.isNaN(distanceSquared) || distanceSquared <= fullDistance * fullDistance) return Detail.FULL;
        return distanceSquared <= coarseDistance * coarseDistance ? Detail.REDUCED : Detail.COARSE;
    }

    /** Sampling is a pure value calculation, without a shared model or last-entity cache. */
    public float sampleAge(float age, int phaseSeed, Detail detail) {
        if (detail == Detail.FULL || !Float.isFinite(age)) return age;
        var step = detail == Detail.REDUCED ? reducedSampleTicks : coarseSampleTicks;
        var phase = Math.floorMod(phaseSeed, step);
        return (float) (Math.floor((age + phase) / step) * step - phase);
    }
}
