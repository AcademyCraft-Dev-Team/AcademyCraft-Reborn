package org.academy.api.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

/** Shared admission for filter-backed vector defense. */
public final class VectorDefenseProtection {
    private VectorDefenseProtection() {
    }

    /**
     * True when the player's vector defense should behave like full reflection for effect
     * protection: reflection is active, or deviation reached full proficiency while the
     * reflection filter is enabled.
     */
    public static boolean usesFilterBackedProtection(ServerPlayer player) {
        if (player == null) return false;
        return VectorReflection.Server.isActive(player)
                || VectorDeviation.Server.usesClassPointerProtection(player)
                && Skills.REFLECTION_FILTER.get().isEnabled(player);
    }
}
