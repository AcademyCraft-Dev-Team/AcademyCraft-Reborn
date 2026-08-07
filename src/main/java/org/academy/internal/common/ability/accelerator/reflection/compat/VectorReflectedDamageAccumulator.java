package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSources;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class VectorReflectedDamageAccumulator {
    static final long MERGE_WINDOW_TICKS = 10L;
    private static final Map<Key, Pending> PENDING = new HashMap<>();

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
        var pending = PENDING.get(key);
        if (pending == null || pending.flushAtTick <= now) {
            if (pending != null && pending.accumulated > 0.0f) applyPending(pending);
            var landed = applyNow(redirector, target, originalSource, originalAttacker, kind, amount);
            PENDING.put(key, new Pending(
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
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next().getValue();
            var redirector = pending.redirector.get();
            var target = pending.target.get();
            if (redirector == null || target == null || redirector.isRemoved() || target.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (!shouldFlush(redirector.level().getGameTime(), pending.flushAtTick)) continue;
            if (pending.accumulated > 0.0f) applyPending(pending);
            iterator.remove();
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
        return target.hurtServer(level, source, amount);
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
        private final long flushAtTick;
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
