package org.academy.api.common.ability.darkmatter;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** One synchronous target scan's identity-based assignments; callers choose the precise source bounds. */
public final class TargetAssignmentSnapshot<T> {
    private final Map<T, Integer> counts;

    private TargetAssignmentSnapshot(Map<T, Integer> counts) {
        this.counts = counts;
    }

    public static <E, T> TargetAssignmentSnapshot<T> capture(Iterable<E> sources,
                                                            Predicate<E> accepted,
                                                            Function<E, T> target) {
        var counts = new IdentityHashMap<T, Integer>();
        for (var source : sources) if (accepted.test(source)) {
            var assignment = target.apply(source);
            if (assignment != null) counts.merge(assignment, 1, Integer::sum);
        }
        return new TargetAssignmentSnapshot<>(counts);
    }

    public boolean contains(T target) { return counts.containsKey(target); }

    public int count(T target) { return counts.getOrDefault(target, 0); }
}
