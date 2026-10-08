package org.academy.api.client.render.vfxgraph.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stable bounded presentation selection. Deferred candidates retain metadata, never pooled effects. */
public final class StableEffectAdmission<K> {
    private final int capacity, minimumHoldTicks;
    private final double replacementSquared;
    private final Map<K, Candidate> candidates = new LinkedHashMap<>();
    private final Set<K> selected = new LinkedHashSet<>();
    private final Set<K> view = Collections.unmodifiableSet(selected);
    private final ArrayList<Candidate> order = new ArrayList<>();
    private long sequence;
    private final class Candidate {
        final K key;
        final long sequence = StableEffectAdmission.this.sequence++;
        int priority;
        double distanceSquared;
        long admitted;
        Candidate(K key) { this.key = key; }
    }
    public StableEffectAdmission(int capacity, int minimumHoldTicks, double replacementRatio) {
        if (capacity < 1 || minimumHoldTicks < 0 || !(replacementRatio > 0 && replacementRatio <= 1)) throw new IllegalArgumentException("Invalid admission budget");
        this.capacity = capacity; this.minimumHoldTicks = minimumHoldTicks;
        replacementSquared = replacementRatio * replacementRatio;
    }
    public void offer(K key, int priority, double distanceSquared) {
        var candidate = candidates.computeIfAbsent(key, Candidate::new);
        candidate.priority = priority;
        candidate.distanceSquared = Double.isFinite(distanceSquared) ? Math.max(0, distanceSquared) : Double.MAX_VALUE;
    }
    public void remove(K key) { candidates.remove(key); selected.remove(key); }
    public void clear() { candidates.clear(); selected.clear(); order.clear(); }
    public Set<K> selected() { return view; }
    private int compare(Candidate a, Candidate b) {
        int priority = Integer.compare(b.priority, a.priority);
        if (priority != 0) return priority;
        int distance = Double.compare(a.distanceSquared, b.distanceSquared);
        return distance != 0 ? distance : Long.compare(a.sequence, b.sequence);
    }
    public Set<K> reconcile(long tick) {
        order.clear(); order.addAll(candidates.values());
        order.sort(this::compare);
        Candidate worst = null, worstMature = null;
        boolean residentsDirty = true;
        for (var candidate : order) {
            if (selected.contains(candidate.key)) continue;
            if (selected.size() < capacity) { selected.add(candidate.key); candidate.admitted = tick; residentsDirty = true; continue; }
            // A stationary over-budget scene scans its resident set once, rather than once per deferred candidate.
            if (residentsDirty) {
                worst = null; worstMature = null;
                for (var key : selected) {
                    var resident = candidates.get(key);
                    if (worst == null || compare(resident, worst) > 0) worst = resident;
                    if (tick - resident.admitted >= minimumHoldTicks && (worstMature == null || compare(resident, worstMature) > 0)) worstMature = resident;
                }
                residentsDirty = false;
            }
            boolean higherPriority = candidate.priority > worst.priority;
            boolean substantiallyCloser = worstMature != null && candidate.priority == worstMature.priority
                    && candidate.distanceSquared < worstMature.distanceSquared * replacementSquared;
            if (higherPriority || substantiallyCloser) {
                selected.remove((higherPriority ? worst : worstMature).key); selected.add(candidate.key); candidate.admitted = tick;
                residentsDirty = true;
            } else break; // Remaining deferred candidates are no better in priority or distance.
        }
        return view;
    }
}
