package org.academy.api.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Composable gravity ownership; release removes only the named source. */
public final class GravityControl {
    private static final Map<Entity, State> STATES = new WeakHashMap<>();
    private GravityControl() {}

    public static void set(Entity subject, Identifier source, boolean enabled) {
        if (subject.level().isClientSide()) return;
        if (enabled) {
            STATES.computeIfAbsent(subject, _ -> new State(subject.isNoGravity())).sources.add(source);
            subject.setNoGravity(true);
        } else {
            var state = STATES.get(subject);
            if (state == null || !state.sources.remove(source)) return;
            if (state.sources.isEmpty()) {
                STATES.remove(subject);
                subject.setNoGravity(state.previous);
            }
        }
    }

    private static final class State {
        final boolean previous;
        final Set<Identifier> sources = new HashSet<>();
        State(boolean previous) { this.previous = previous; }
    }
}
