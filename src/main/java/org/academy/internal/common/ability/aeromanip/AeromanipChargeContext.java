package org.academy.internal.common.ability.aeromanip;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.internal.server.ability.AeromanipResourceManager;

/**
 * Server-authoritative base context for maintained charge gestures.
 * Clients only start and release the context; elapsed time is never accepted from a packet.
 */
public abstract class AeromanipChargeContext extends ServerContext {
    private final Skill skill;
    private final ServerLevel initialLevel;
    private final long startGameTime;
    private final AeromanipResourceManager.UsageLease usageLease;
    private final ChargeTickListener chargeTickListener = new ChargeTickListener();
    private AeromanipChargeTier lastTier = AeromanipChargeTier.INSTANT;
    private boolean ended;
    private boolean released;

    protected AeromanipChargeContext(ServerPlayer player, Skill skill) {
        this(player, skill, player.level().getGameTime());
    }

    protected AeromanipChargeContext(ServerPlayer player, Skill skill, long startGameTime) {
        super(player);
        this.skill = skill;
        initialLevel = player.level();
        this.startGameTime = Math.min(startGameTime, initialLevel.getGameTime());
        usageLease = AbilitySystemServer.getSystem(player)
                .getAeromanipResourceManager()
                .beginUse(player);
    }

    public final long chargeTicks() {
        return elapsedTicks(startGameTime, player.level().getGameTime());
    }

    public final AeromanipChargeTier chargeTier() {
        return AeromanipChargeTier.fromTicks(chargeTicks());
    }

    public final void release() {
        if (ended) return;
        ended = true;
        released = true;
        try {
            onReleased(chargeTier(), chargeTicks());
        } finally {
            unregister();
        }
    }

    public final void cancel() {
        if (ended) return;
        ended = true;
        unregister();
    }

    private void onChargeTick() {
        if (ended || player.hasDisconnected() || !player.isAlive()
                || player.level() != initialLevel || !skill.isEnabled(player)) {
            cancel();
            return;
        }
        var tier = chargeTier();
        if (tier != lastTier) {
            lastTier = tier;
            onTierReached(tier);
        }
    }

    protected abstract void onReleased(AeromanipChargeTier tier, long chargeTicks);

    protected void onTierReached(AeromanipChargeTier tier) {
    }

    protected void onChargeEnded(boolean released) {
    }

    @Override
    protected final void onUnregistered() {
        ended = true;
        usageLease.close();
        onChargeEnded(released);
    }

    @Override
    protected final Object eventListener() {
        return chargeTickListener;
    }

    private final class ChargeTickListener {
        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            onChargeTick();
        }
    }

    public static long elapsedTicks(long startGameTime, long currentGameTime) {
        if (currentGameTime <= startGameTime) return 0L;
        var elapsed = currentGameTime - startGameTime;
        return elapsed < 0L ? Long.MAX_VALUE : elapsed;
    }
}
