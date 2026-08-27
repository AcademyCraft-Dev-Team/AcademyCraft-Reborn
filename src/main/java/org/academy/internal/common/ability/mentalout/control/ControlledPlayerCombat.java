package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.ability.aeromanip.skills.lv3.RejectingWind;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.meltdowner.skills.lv1.SingleHighSpeedElectronBeam;

/**
 * Server-owned ability selection for a player acting on an explicit forced target.
 */
final class ControlledPlayerCombat {
    private ControlledPlayerCombat() {
    }

    static double abilityRange(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var category = system.getPlayerAbilityCategory(player.getUUID());
        if (category == AbilityCategories.ACCELERATOR.get()
                && Skills.VECTOR_BLAST.get().isEnabled(player)) return 64.0;
        if (category == AbilityCategories.ELECTROMASTER.get()
                && Skills.ARC_GENERATE.get().isEnabled(player)) return 10.0;
        if (category == AbilityCategories.MELTDOWNER.get()
                && Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get().isEnabled(player)) return 48.0;
        if (category == AbilityCategories.DARKMATTER.get()
                && Skills.DARKMATTER_DISASSEMBLE.get().isEnabled(player)) return 32.0;
        if (category == AbilityCategories.AEROMANIP.get()
                && Skills.REJECTING_WIND.get().isEnabled(player)) return 8.0;
        return 0.0;
    }

    static boolean tryAbilityAttack(ServerPlayer player, LivingEntity target) {
        if (player == null || target == null || !MentalControlRuntime.canForceAttack(player, target)
                || target.level() != player.level() || !player.hasLineOfSight(target)) return false;
        var range = abilityRange(player);
        if (range <= 0.0 || player.distanceToSqr(target) > range * range) return false;
        var category = AbilitySystemServer.getSystem(player)
                .getPlayerAbilityCategory(player.getUUID());
        if (category == AbilityCategories.ACCELERATOR.get()) {
            return VectorBlast.Server.tryAutomatedAttack(player);
        }
        if (category == AbilityCategories.ELECTROMASTER.get()) {
            return ArcGenerate.Server.tryAutomatedAttack(player);
        }
        if (category == AbilityCategories.MELTDOWNER.get()) {
            return SingleHighSpeedElectronBeam.Server.tryAutomatedAttack(player);
        }
        if (category == AbilityCategories.DARKMATTER.get()) {
            return DarkmatterDisassemble.Server.tryAutomatedAttack(player, target);
        }
        if (category == AbilityCategories.AEROMANIP.get()) {
            return RejectingWind.Server.tryAutomatedAttack(player);
        }
        return false;
    }
}
