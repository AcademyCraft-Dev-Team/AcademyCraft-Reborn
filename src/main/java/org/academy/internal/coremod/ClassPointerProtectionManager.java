package org.academy.internal.coremod;

import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class ClassPointerProtectionManager {
    private static final Object LOCK = new Object();
    private static final Map<Object, ProtectionState> STATES = new IdentityHashMap<>();

    private ClassPointerProtectionManager() {
    }

    public static boolean ensureServerPlayer(ServerPlayer player) {
        return ensure(player, Side.SERVER);
    }

    public static boolean ensureClientPlayer(Object player) {
        return ensure(player, Side.CLIENT);
    }

    public static ProtectionBackend backend(Object player) {
        if (player == null) return ProtectionBackend.UNSUPPORTED;
        synchronized (LOCK) {
            var state = STATES.get(player);
            if (state != null) return state.backend;
        }
        return HotSpotClassPointerAccess.capability().available()
                ? ProtectionBackend.MIXIN_FALLBACK : ProtectionBackend.UNSUPPORTED;
    }

    public static boolean restore(Object player) {
        if (player == null) return false;
        synchronized (LOCK) {
            var state = STATES.remove(player);
            if (state == null || state.backend != ProtectionBackend.CLASS_POINTER) return false;
            state.ledger.remove(playerId(player));
            var current = HotSpotClassPointerAccess.read(player);
            if (current == state.originalWord) return false;
            var restored = HotSpotClassPointerAccess.writeAndVerify(player, state.originalWord);
            if (!restored) logFailure(player, state, "could not restore original class pointer");
            return restored;
        }
    }

    public static void restoreAllServer() {
        restoreAll(Side.SERVER);
    }

    public static void restoreAllClient() {
        restoreAll(Side.CLIENT);
    }

    private static boolean ensure(Object player, Side side) {
        if (player == null) return false;
        synchronized (LOCK) {
            var existing = STATES.get(player);
            if (existing != null) {
                if (existing.backend != ProtectionBackend.CLASS_POINTER) return false;
                return repair(player, existing);
            }

            var capability = HotSpotClassPointerAccess.capability();
            if (!capability.available()) {
                fallback(player, side, player.getClass(), 0L, capability.reason());
                return false;
            }

            var originalType = player.getClass();
            var generated = DispatchSubclassFactory.forPlayerType(originalType);
            if (!generated.successful()) {
                fallback(player, side, originalType, 0L, generated.failureReason());
                return false;
            }
            try {
                DispatchSubclassFactory.initializeForUse(generated);
            } catch (Throwable error) {
                fallback(player, side, originalType, 0L,
                        "dispatch state initialization failed: "
                                + error.getClass().getSimpleName() + ": " + error.getMessage());
                return false;
            }

            var originalWord = HotSpotClassPointerAccess.read(player);
            var dispatchWord = HotSpotClassPointerAccess.wordFor(generated.dispatchType());
            if (originalWord == 0L || dispatchWord == 0L || originalWord == dispatchWord) {
                fallback(player, side, originalType, originalWord,
                        "invalid original or dispatch class pointer");
                return false;
            }

            generated.ledger().remove(playerId(player));
            var state = new ProtectionState(side, ProtectionBackend.CLASS_POINTER, originalType,
                    generated.dispatchType(), originalWord, dispatchWord, generated.ledger(), null);
            if (!HotSpotClassPointerAccess.writeAndVerify(player, dispatchWord)
                    || player.getClass() != generated.dispatchType()) {
                HotSpotClassPointerAccess.writeAndVerify(player, originalWord);
                fallback(player, side, originalType, originalWord,
                        "initial class pointer verification failed");
                return false;
            }
            STATES.put(player, state);
            return true;
        }
    }

    private static boolean repair(Object player, ProtectionState state) {
        var current = HotSpotClassPointerAccess.read(player);
        if (current == state.dispatchWord) return false;
        if (HotSpotClassPointerAccess.writeAndVerify(player, state.dispatchWord)) {
            var now = System.nanoTime();
            if (now - state.lastRepairLogNanos > 5_000_000_000L) {
                state.lastRepairLogNanos = now;
                AcademyCraft.getLogger().warn(
                        "Vector Reflection repaired a displaced class pointer for {} ({})",
                        describe(player, state), state.originalType.getName());
            }
            return true;
        }

        HotSpotClassPointerAccess.writeAndVerify(player, state.originalWord);
        state.backend = ProtectionBackend.MIXIN_FALLBACK;
        state.failureReason = "foreign class pointer repair failed";
        logFailure(player, state, state.failureReason);
        return false;
    }

    private static void fallback(Object player, Side side, Class<?> originalType,
                                 long originalWord, String reason) {
        var state = new ProtectionState(side, ProtectionBackend.MIXIN_FALLBACK, originalType,
                null, originalWord, 0L, Map.of(), reason);
        STATES.put(player, state);
        logFailure(player, state, "using Mixin fallback: " + reason);
    }

    private static void restoreAll(Side side) {
        ArrayList<Object> players;
        synchronized (LOCK) {
            players = new ArrayList<>(STATES.keySet());
        }
        for (var player : players) {
            synchronized (LOCK) {
                var state = STATES.get(player);
                if (state == null || state.side != side) continue;
            }
            restore(player);
        }
    }

    private static void logFailure(Object player, ProtectionState state, String message) {
        if (state.failureLogged) return;
        state.failureLogged = true;
        AcademyCraft.getLogger().warn("Vector Reflection {} for {}; original type={}, backend={}",
                message, describe(player, state), state.originalType.getName(), state.backend);
    }

    private static String describe(Object player, ProtectionState state) {
        if (state.side == Side.SERVER && player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getGameProfile().name() + "/" + serverPlayer.getUUID();
        }
        return "local player";
    }

    private static UUID playerId(Object player) {
        if (player instanceof net.minecraft.world.entity.player.Player actual) {
            return actual.getUUID();
        }
        return new UUID(0L, System.identityHashCode(player));
    }

    private enum Side {
        SERVER,
        CLIENT
    }

    private static final class ProtectionState {
        private final Side side;
        private final Class<?> originalType;
        private final Class<?> dispatchType;
        private final long originalWord;
        private final long dispatchWord;
        private final Map<UUID, Long> ledger;
        private volatile ProtectionBackend backend;
        private volatile String failureReason;
        private volatile boolean failureLogged;
        private volatile long lastRepairLogNanos;

        private ProtectionState(Side side, ProtectionBackend backend, Class<?> originalType,
                                Class<?> dispatchType, long originalWord, long dispatchWord,
                                Map<UUID, Long> ledger, String failureReason) {
            this.side = side;
            this.backend = backend;
            this.originalType = originalType;
            this.dispatchType = dispatchType;
            this.originalWord = originalWord;
            this.dispatchWord = dispatchWord;
            this.ledger = ledger;
            this.failureReason = failureReason;
        }
    }
}
