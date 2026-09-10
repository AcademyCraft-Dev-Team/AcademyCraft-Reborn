package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorRuleRegistry;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

/**
 * Pure co-presence soak: both in potent-sulfur spring water for {@link MisakaHotSpring#SOAK_TICKS}.
 */
public final class MisakaHotSpringSoak {
    private int soakTicks;
    private @Nullable String soakPlayerName;

    public void tick(MisakaSisterEntity sister) {
        if (!(sister.level() instanceof ServerLevel level) || !sister.isAwakened() || sister.isPassenger()) {
            reset();
            return;
        }
        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            reset();
            return;
        }
        int day = MisakaDayTime.dayIndex(level);
        if (record.lastHotSpringFavorDay >= 0 && day - record.lastHotSpringFavorDay < MisakaHotSpring.COOLDOWN_DAYS) {
            reset();
            return;
        }
        // Idle sisters: cheap probe every 10 ticks before starting a soak session.
        if (soakTicks == 0 && sister.tickCount % 10 != 0) {
            return;
        }

        ServerPlayer partner = findPartner(sister);
        if (partner == null) {
            reset();
            return;
        }
        String name = partner.getGameProfile().name();
        if (!name.equals(soakPlayerName)) {
            soakPlayerName = name;
            soakTicks = 0;
        }
        soakTicks++;
        if (soakTicks % 40 == 0) {
            MisakaInteractionFeedback.hotSpringSteam(sister, partner);
        }
        if (soakTicks >= MisakaHotSpring.SOAK_TICKS) {
            FavorRuleRegistry.trigger(FavorContext.hotSpring(record, sister.getMisakaUuid(), partner));
            MisakaSisterRoster.get(level.getServer()).setDirty();
            MisakaInteractionFeedback.hotSpringComplete(sister, partner);
            reset();
        }
    }

    private static @Nullable ServerPlayer findPartner(MisakaSisterEntity sister) {
        ServerPlayer best = null;
        double bestDist = MisakaHotSpring.TOGETHER_RANGE * MisakaHotSpring.TOGETHER_RANGE;
        var box = sister.getBoundingBox().inflate(MisakaHotSpring.TOGETHER_RANGE);
        for (ServerPlayer serverPlayer : sister.level().getEntitiesOfClass(
                ServerPlayer.class,
                box,
                player -> !player.isSpectator()
        )) {
            double dist = sister.distanceToSqr(serverPlayer);
            if (dist > bestDist) {
                continue;
            }
            if (!MisakaHotSpring.areSoakingTogether(sister, serverPlayer)) {
                continue;
            }
            bestDist = dist;
            best = serverPlayer;
        }
        return best;
    }

    private void reset() {
        soakTicks = 0;
        soakPlayerName = null;
    }
}
