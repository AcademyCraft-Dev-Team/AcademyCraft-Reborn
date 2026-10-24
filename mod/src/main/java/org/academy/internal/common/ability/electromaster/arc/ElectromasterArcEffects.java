package org.academy.internal.common.ability.electromaster.arc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.ability.ElectromasterGraphEffects;
import org.academy.internal.common.ability.electromaster.skystrike.SkyStrikeProfile;
import org.academy.internal.common.ability.electromaster.skystrike.SkyStrikeVisualPacket;

public final class ElectromasterArcEffects {
    private ElectromasterArcEffects() {
    }

    public static void spawnChainArc(ServerLevel level, Vec3 start, Vec3 end) {
        ElectromasterGraphEffects.spawnChainArc(level, start, end);
    }

    public static void spawnShieldInterceptRing(ServerLevel level, Vec3 center, Vec3 direction) {
        ElectromasterGraphEffects.spawnShieldInterceptRing(level, center, direction);
    }

    public static void spawnSkyStrike(ServerLevel level, Vec3 impact) {
        spawnSkyStrike(level, impact, SkyStrikeProfile.LIGHTNING_STORM);
    }

    public static void spawnSkyStrike(ServerLevel level, Vec3 impact, SkyStrikeProfile profile) {
        var random = level.getRandom();
        SkyStrikeVisualPacket.broadcast(level, impact, profile);
        level.playSound(
                null,
                impact.x,
                impact.y,
                impact.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER,
                profile.thunderVolume(),
                0.8f + random.nextFloat() * 0.2f
        );
        level.playSound(
                null,
                impact.x,
                impact.y,
                impact.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.WEATHER,
                profile.impactVolume(),
                0.9f + random.nextFloat() * 0.2f
        );
    }
}
