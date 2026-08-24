package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.ControlRejectionReason;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.skills.lv4.ElectromagneticShield;
import org.academy.internal.common.ability.mentalout.MentalResistanceManager;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
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
        var kind = kind(subject);
        if (kind == null) return null;
        return kind == Kind.IMMUNE_TAG
                ? ControlRejectionReason.IMMUNE_TAG
                : ControlRejectionReason.PROTECTED_PLAYER;
    }

    static @Nullable Kind kind(LivingEntity subject) {
        if (subject == null) return Kind.IMMUNE_TAG;
        if (subject instanceof DarkmatterBeetle) return Kind.DARKMATTER_NETWORK;
        if (subject.getType().builtInRegistryHolder().is(IMMUNE_ENTITY_TYPES)) return Kind.IMMUNE_TAG;
        if (!(subject instanceof ServerPlayer player)) return null;
        if (MentalResistanceManager.isResistant(player)) return Kind.MENTAL_RESISTANCE;
        if (VectorReflection.Server.isActive(player) || VectorDeviation.Server.isActive(player)) {
            return Kind.VECTOR_FILTER;
        }
        if (ElectromagneticShield.Server.isActive(player)) return Kind.ELECTROMAGNETIC_FIELD;
        if (DarkmatterSixWings.Server.isActive(player)) return Kind.DARKMATTER_UNKNOWN;
        return null;
    }

    static void notifyBlocked(ServerPlayer controller, LivingEntity subject) {
        var kind = kind(subject);
        if (controller == null || kind == null) return;
        controller.sendOverlayMessage(Component.translatable(kind.feedbackKey));
        if (!(subject instanceof ServerPlayer player)) return;
        var direction = controller.getBoundingBox().getCenter()
                .subtract(player.getBoundingBox().getCenter());
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-8) {
            direction = player.getLookAngle();
        }
        if (kind == Kind.VECTOR_FILTER) {
            if (!VectorReflection.Server.tryPlayReflectionSound(player)) return;
            var normal = direction.normalize();
            var offset = Math.max(player.getBbWidth() * 0.95, 0.75);
            var center = player.getBoundingBox().getCenter().add(normal.scale(offset));
            VectorReflection.Server.spawnGlowCircle(player, normal, center);
        } else if (kind == Kind.ELECTROMAGNETIC_FIELD
                && player.level() instanceof ServerLevel level) {
            var normal = direction.normalize();
            var offset = Math.max(player.getBbWidth() * 0.95, 0.75);
            var center = player.getBoundingBox().getCenter().add(normal.scale(offset));
            ElectromasterArcEffects.spawnShieldInterceptRing(level, center, normal);
        }
    }

    static boolean isBossCost(LivingEntity subject) {
        return subject.getType().builtInRegistryHolder().is(BOSS_COST_ENTITY_TYPES);
    }

    enum Kind {
        IMMUNE_TAG("message.academy.mentalout.protected_target"),
        VECTOR_FILTER("message.academy.mentalout.protected.vector_filter"),
        ELECTROMAGNETIC_FIELD("message.academy.mentalout.protected.electromagnetic_field"),
        DARKMATTER_NETWORK("message.academy.mentalout.protected.darkmatter_network"),
        DARKMATTER_UNKNOWN("message.academy.mentalout.protected.darkmatter_unknown"),
        MENTAL_RESISTANCE("message.academy.mentalout.control_resistance");

        private final String feedbackKey;

        Kind(String feedbackKey) {
            this.feedbackKey = feedbackKey;
        }
    }
}
