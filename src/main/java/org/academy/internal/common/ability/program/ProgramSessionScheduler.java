package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded scheduler for resumable ability-program VM sessions.
 */
public final class ProgramSessionScheduler<K> {
    public static final int DEFAULT_MAX_SESSIONS = 256;

    private final int maxSessions;
    private final Map<K, RunningSession<K>> sessions = new LinkedHashMap<>();

    public ProgramSessionScheduler() {
        this(DEFAULT_MAX_SESSIONS);
    }

    public ProgramSessionScheduler(int maxSessions) {
        if (maxSessions < 1) throw new IllegalArgumentException("Session limit must be positive");
        this.maxSessions = maxSessions;
    }

    public boolean start(
            K key,
            CompiledProgram program,
            ProgramExecutorLookup executors,
            @Nullable Object attachment,
            int fuelPerTick,
            long startedAt,
            long maxLifetimeTicks,
            SessionListener<K> listener
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(executors, "executors");
        Objects.requireNonNull(listener, "listener");
        if (fuelPerTick < 1) throw new IllegalArgumentException("Session fuel must be positive");
        if (startedAt < 0) throw new IllegalArgumentException("Session start tick cannot be negative");
        if (maxLifetimeTicks < 1) {
            throw new IllegalArgumentException("Session lifetime must be positive");
        }
        if (sessions.containsKey(key) || sessions.size() >= maxSessions) return false;
        sessions.put(key, new RunningSession<>(
                new ProgramVm.Session(program),
                executors,
                attachment,
                fuelPerTick,
                startedAt,
                maxLifetimeTicks,
                listener
        ));
        return true;
    }

    public void tick(long gameTime) {
        if (gameTime < 0) throw new IllegalArgumentException("Scheduler tick cannot be negative");
        for (var entry : List.copyOf(sessions.entrySet())) {
            var key = entry.getKey();
            var running = entry.getValue();
            if (sessions.get(key) != running) continue;
            if (gameTime - running.startedAt >= running.maxLifetimeTicks) {
                terminate(key, running, Termination.expired(running.vm.currentNodeId()));
                continue;
            }
            var result = running.vm.run(
                    gameTime,
                    running.fuelPerTick,
                    running.executors,
                    running.attachment
            );
            switch (result.status()) {
                case COMPLETED -> terminate(key, running, Termination.completed(result.nodeId()));
                case FAILED -> terminate(key, running, Termination.failed(
                        result.nodeId(), result.diagnostic()));
                case FUEL_EXHAUSTED, SUSPENDED -> {
                }
            }
        }
    }

    public boolean cancel(K key) {
        var running = sessions.remove(key);
        if (running == null) return false;
        notifyListener(key, running, Termination.cancelled(running.vm.currentNodeId()));
        return true;
    }

    public void clear() {
        for (var entry : List.copyOf(sessions.entrySet())) cancel(entry.getKey());
    }

    public int size() {
        return sessions.size();
    }

    public boolean contains(K key) {
        return sessions.containsKey(key);
    }

    private void terminate(K key, RunningSession<K> running, Termination termination) {
        if (!sessions.remove(key, running)) return;
        notifyListener(key, running, termination);
    }

    private void notifyListener(K key, RunningSession<K> running, Termination termination) {
        try {
            running.listener.onTerminated(key, termination);
        } catch (RuntimeException ignored) {
        }
    }

    @FunctionalInterface
    public interface SessionListener<K> {
        void onTerminated(K key, Termination termination);
    }

    public enum TerminationKind {
        COMPLETED,
        FAILED,
        EXPIRED,
        CANCELLED
    }

    public record Termination(
            TerminationKind kind,
            int nodeId,
            ProgramVmDiagnostic diagnostic
    ) {
        private static Termination completed(int nodeId) {
            return new Termination(TerminationKind.COMPLETED, nodeId, ProgramVmDiagnostic.NONE);
        }

        private static Termination failed(int nodeId, ProgramVmDiagnostic diagnostic) {
            return new Termination(TerminationKind.FAILED, nodeId, diagnostic);
        }

        private static Termination expired(int nodeId) {
            return new Termination(TerminationKind.EXPIRED, nodeId, ProgramVmDiagnostic.NONE);
        }

        private static Termination cancelled(int nodeId) {
            return new Termination(TerminationKind.CANCELLED, nodeId, ProgramVmDiagnostic.NONE);
        }
    }

    private record RunningSession<K>(
            ProgramVm.Session vm,
            ProgramExecutorLookup executors,
            @Nullable Object attachment,
            int fuelPerTick,
            long startedAt,
            long maxLifetimeTicks,
            SessionListener<K> listener
    ) {
    }
}
