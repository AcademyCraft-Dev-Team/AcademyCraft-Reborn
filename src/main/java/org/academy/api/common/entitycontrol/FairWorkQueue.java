package org.academy.api.common.entitycontrol;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Persistent round robin by owner, then order, then worker. Add/remove only on lifecycle changes. */
public final class FairWorkQueue<T> {
    private record Assignment(Object owner, Object group) {}
    private final LinkedHashMap<Object, LinkedHashMap<Object, LinkedHashSet<T>>> owners = new LinkedHashMap<>();
    private final Map<T, Assignment> assignments = new java.util.HashMap<>();

    public void add(Object owner, Object group, T task) {
        remove(task);
        assignments.put(task, new Assignment(owner, group));
        owners.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(group, ignored -> new LinkedHashSet<>()).add(task);
    }

    public void remove(T task) {
        var assignment = assignments.remove(task);
        if (assignment == null) return;
        var groups = owners.get(assignment.owner);
        var tasks = groups.get(assignment.group);
        tasks.remove(task);
        if (tasks.isEmpty()) groups.remove(assignment.group);
        if (groups.isEmpty()) owners.remove(assignment.owner);
    }

    public @Nullable T next() {
        if (owners.isEmpty()) return null;
        var owner = owners.pollFirstEntry();
        var group = owner.getValue().pollFirstEntry();
        var task = group.getValue().removeFirst();
        group.getValue().add(task);
        owner.getValue().put(group.getKey(), group.getValue());
        owners.put(owner.getKey(), owner.getValue());
        return task;
    }

    public int size() { return assignments.size(); }
    /** Shift the first claimant of scarce sub-operation budgets even when a full round fits in a tick. */
    public void rotateStart() { next(); }
    public void clear() { owners.clear(); assignments.clear(); }
}
