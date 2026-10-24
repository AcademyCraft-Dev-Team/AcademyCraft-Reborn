package org.academy.api.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public record AbilityCastRequest(Identifier skill, @Nullable LivingEntity target, @Nullable ServerPlayer controller) {
    public AbilityCastRequest {
        if (skill == null) throw new IllegalArgumentException("skill");
    }
    public static AbilityCastRequest autonomous(Identifier skill, @Nullable LivingEntity target) {
        return new AbilityCastRequest(skill, target, null);
    }
}
