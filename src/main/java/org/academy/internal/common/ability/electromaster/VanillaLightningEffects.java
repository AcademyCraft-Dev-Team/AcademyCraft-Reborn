package org.academy.internal.common.ability.electromaster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Applies vanilla server-side lightning effects without adding a second rendered lightning bolt.
 */
public final class VanillaLightningEffects {
    private VanillaLightningEffects() {
    }

    public static void trigger(ServerLevel level, Vec3 impact, @Nullable ServerPlayer cause) {
        if (level == null || impact == null || !isFinite(impact)
                || !level.hasChunkAt(BlockPos.containing(impact))) return;

        var lightning = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
        lightning.setPos(impact.x, impact.y, impact.z);
        lightning.setCause(cause);
        // AcademyCraft applies its own skill-scaled damage after vanilla transformations.
        lightning.setDamage(0.0f);
        try {
            lightning.tick();
        } finally {
            lightning.discard();
        }
    }

    static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
