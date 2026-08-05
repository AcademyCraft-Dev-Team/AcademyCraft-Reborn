package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.ControlRejectionReason;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterSixWings;
import org.academy.internal.common.ability.electromaster.skills.lv4.ElectromagneticShield;
import org.jspecify.annotations.Nullable;

final class MentalControlProtection {
    static final TagKey<EntityType<?>> IMMUNE_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "mental_control_immune")
    );
    static final TagKey<EntityType<?>> BOSS_COST_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "mental_control_boss_cost")
    );

    private MentalControlProtection() {
    }

    static @Nullable ControlRejectionReason rejectionReason(LivingEntity subject) {
        if (subject.getType().builtInRegistryHolder().is(IMMUNE_ENTITY_TYPES)) {
            return ControlRejectionReason.IMMUNE_TAG;
        }
        if (subject instanceof ServerPlayer player
                && (VectorReflection.Server.isActive(player)
                || ElectromagneticShield.Server.isActive(player)
                || DarkmatterSixWings.Server.isActive(player))) {
            return ControlRejectionReason.PROTECTED_PLAYER;
        }
        return null;
    }

    static boolean isBossCost(LivingEntity subject) {
        return subject.getType().builtInRegistryHolder().is(BOSS_COST_ENTITY_TYPES);
    }
}
