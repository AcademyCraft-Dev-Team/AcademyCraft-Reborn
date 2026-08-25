package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSources;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VectorReflectedDamageAccumulator {
    static final long MERGE_WINDOW_TICKS = 10L;
    static final long REENTRANT_DELAY_TICKS = 1L;
    private static final Map<Key, Pending> PENDING = new HashMap<>();
    private static final VectorDamageReentryGuard REENTRY_GUARD = new VectorDamageReentryGuard();

    private VectorReflectedDamageAccumulator() {
    }

    public static synchronized boolean submit(
            ServerPlayer redirector,
            LivingEntity target,
            DamageSource originalSource,
            VectorRedirectKind kind,
            float amount
    ) {
        return submit(redirector, target, originalSource, originalSource == null
                ? null : originalSource.getEntity(), kind, amount);
    }

    public static synchronized boolean submit(
            ServerPlayer redirector,
            LivingEntity target,
            DamageSource originalSource,
            @Nullable Entity originalAttacker,
            VectorRedirectKind kind,
            float amount
    ) {
        if (!canDamage(redirector, target, amount) || originalSource == null) return false;
        var key = new Key(
                redirector.getUUID(),
                target.getUUID(),
                VectorCompatProfile.damageTypeId(originalSource),
                kind
        );
        var now = redirector.level().getGameTime();
        if (REENTRY_GUARD.isActive(target.getUUID())) {
            defer(key, redirector, target, originalSource, originalAttacker, kind, amount, now);
            return true;
        }

        var pending = PENDING.get(key);
        if (pending == null || pending.flushAtTick <= now) {
            if (pending != null) {
                PENDING.remove(key);
                if (pending.accumulated > 0.0f) applyPending(pending);
            }
            var landed = applyNow(redirector, target, originalSource, originalAttacker, kind, amount);
            PENDING.putIfAbsent(key, new Pending(
                    redirector,
                    target,
                    originalSource,
                    originalAttacker,
                    kind,
                    now + MERGE_WINDOW_TICKS
            ));
            return landed;
        }
        pending.accumulated += amount;
        return true;
    }

    public static synchronized void tick() {
        // Damage callbacks may synchronously submit more reflected damage. Iterate over a stable
        // key snapshot so those deferred submissions cannot invalidate a live map iterator.
        for (var key : List.copyOf(PENDING.keySet())) {
            var pending = PENDING.get(key);
            if (pending == null) continue;
            var redirector = pending.redirector.get();
            var target = pending.target.get();
            if (redirector == null || target == null || redirector.isRemoved() || target.isRemoved()) {
                PENDING.remove(key, pending);
                continue;
            }
            if (!shouldFlush(redirector.level().getGameTime(), pending.flushAtTick)) continue;
            PENDING.remove(key, pending);
            if (pending.accumulated > 0.0f) applyPending(pending);
        }
    }

    public static synchronized void clear(ServerPlayer player) {
        if (player == null) return;
        var uuid = player.getUUID();
        PENDING.keySet().removeIf(key -> key.redirectorId.equals(uuid) || key.targetId.equals(uuid));
    }

    static boolean shouldFlush(long now, long flushAtTick) {
        return now >= flushAtTick;
    }

    static long deferredFlushAt(long currentFlushAt, long now) {
        var nextTick = now + REENTRANT_DELAY_TICKS;
        return currentFlushAt <= now ? nextTick : Math.min(currentFlushAt, nextTick);
    }

    private static void defer(
            Key key,
            ServerPlayer redirector,
            LivingEntity target,
            DamageSource originalSource,
            @Nullable Entity originalAttacker,
            VectorRedirectKind kind,
            float amount,
            long now
    ) {
        var pending = PENDING.get(key);
        if (pending == null) {
            pending = new Pending(
                    redirector,
                    target,
                    originalSource,
                    originalAttacker,
                    kind,
                    now + REENTRANT_DELAY_TICKS
            );
            PENDING.put(key, pending);
        } else {
            pending.flushAtTick = deferredFlushAt(pending.flushAtTick, now);
        }
        pending.accumulated += amount;
    }

    private static boolean applyPending(Pending pending) {
        var redirector = pending.redirector.get();
        var target = pending.target.get();
        if (redirector == null || target == null) return false;
        var amount = pending.accumulated;
        pending.accumulated = 0.0f;
        return applyNow(
                redirector,
                target,
                pending.originalSource,
                pending.originalAttacker,
                pending.kind,
                amount
        );
    }

    private static boolean applyNow(
            ServerPlayer redirector,
            LivingEntity target,
            DamageSource originalSource,
            @Nullable Entity originalAttacker,
            VectorRedirectKind kind,
            float amount
    ) {
        if (!canDamage(redirector, target, amount)
                || !(redirector.level() instanceof ServerLevel level)) return false;
        var source = VectorRedirectedDamageSources.from(
                originalSource,
                redirector,
                originalAttacker,
                kind
        );
        return REENTRY_GUARD.run(
                target.getUUID(),
                () -> target.hurtServer(level, source, amount)
        );
    }

    private static boolean canDamage(ServerPlayer redirector, LivingEntity target, float amount) {
        return redirector != null
                && target != null
                && target != redirector
                && target.isAlive()
                && target.level() == redirector.level()
                && amount > 0.0f
                && Float.isFinite(amount)
                && !redirector.isAlliedTo(target)
                && !CtaFriendlyFireWhitelist.shouldProtect(redirector, target);
    }

    private record Key(UUID redirectorId, UUID targetId, String damageTypeId, VectorRedirectKind kind) {
    }

    private static final class Pending {
        private final WeakReference<ServerPlayer> redirector;
        private final WeakReference<LivingEntity> target;
        private final DamageSource originalSource;
        @Nullable
        private final Entity originalAttacker;
        private final VectorRedirectKind kind;
        private long flushAtTick;
        private float accumulated;

        private Pending(
                ServerPlayer redirector,
                LivingEntity target,
                DamageSource originalSource,
                @Nullable Entity originalAttacker,
                VectorRedirectKind kind,
                long flushAtTick
        ) {
            this.redirector = new WeakReference<>(redirector);
            this.target = new WeakReference<>(target);
            this.originalSource = originalSource;
            this.originalAttacker = originalAttacker;
            this.kind = kind;
            this.flushAtTick = flushAtTick;
        }
    }
}
