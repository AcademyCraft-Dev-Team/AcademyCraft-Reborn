package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.entitycontrol.*;

import java.util.*;

public final class PlayerNavigationRuntime {
    static final int MAX_GLOBAL_EXPANSIONS_PER_TICK = 1024;
    private static final Object LOCK = new Object();
    private static volatile List<Registration> registrations = List.of();
    private static long budgetTick = Long.MIN_VALUE;
    private static int remainingBudget;

    private PlayerNavigationRuntime() {
    }

    public static void registerAdapter(Identifier id, int priority, PlayerNavigationAdapter adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        synchronized (LOCK) {
            var next = new ArrayList<>(registrations);
            next.removeIf(registration -> registration.id.equals(id));
            next.add(new Registration(id, priority, adapter));
            next.sort(Comparator.comparingInt(Registration::priority).reversed()
                    .thenComparing(registration -> registration.id.toString()));
            registrations = List.copyOf(next);
        }
    }

    public static Optional<PlayerNavigationAdapter> findAdapter(
            ServerPlayer subject,
            PlayerMovementMode mode
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(mode, "mode");
        return registrations.stream()
                .filter(registration -> registration.adapter.matches(subject))
                .filter(registration -> registration.adapter.modes(subject).contains(mode))
                .map(Registration::adapter)
                .findFirst();
    }

    static ControlBinding activate(
            ControlContext context,
            ServerPlayer subject,
            ControlDirective.MoveTo directive
    ) {
        for (var registration : registrations) {
            if (!registration.adapter.matches(subject)
                    || registration.adapter.modes(subject).isEmpty()) continue;
            return registration.adapter.activate(context, subject, directive);
        }
        throw new IllegalStateException("No player navigation adapter supports " + subject.getUUID());
    }

    static synchronized void beginServerTick(long gameTick) {
        if (budgetTick == gameTick) return;
        budgetTick = gameTick;
        remainingBudget = MAX_GLOBAL_EXPANSIONS_PER_TICK;
    }

    static synchronized int claimExpansionBudget(long gameTick, int requested) {
        beginServerTick(gameTick);
        var granted = Math.min(Math.max(0, requested), remainingBudget);
        remainingBudget -= granted;
        return granted;
    }

    private record Registration(Identifier id, int priority, PlayerNavigationAdapter adapter) {
    }
}
