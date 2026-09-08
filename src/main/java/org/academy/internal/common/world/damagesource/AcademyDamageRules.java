package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorExternalInterceptionService;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv5.CrossingTheAbyss;
import org.academy.internal.common.ability.aeromanip.skills.lv3.AtmosphereShield;
import org.academy.internal.common.ability.darkmatter.DarkmatterModifierRuntime;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.ability.electromaster.skills.lv1.ElectricalContact;
import org.academy.internal.common.ability.electromaster.skills.lv4.ElectromagneticShield;
import org.academy.internal.common.ability.level0.skills.OutputControl;
import org.academy.internal.common.ability.mentalout.control.MentalControlEvents;
import org.academy.internal.common.ability.program.AbilityProgramTriggerRuntime;
import org.academy.internal.common.ability.teleport.skills.lv5.Flashing;
import org.academy.internal.common.event.DarkmatterEquipmentEvents;

import java.util.List;
import java.util.function.Consumer;

/**
 * Academy's own combat rules, in priority order. Mutable external events are deliberately
 * not posted for Academy damage. Vanilla armor/absorption and immutable Post notifications
 * continue to use the original damage path. Keep new category defenses in this list as well
 * as their ordinary-damage event adapters.
 */
public final class AcademyDamageRules {
    private static final List<Consumer<LivingIncomingDamageEvent>> INCOMING = List.of(
            CategoryDamageRuntime::onIncomingDamage,
            SkillDamageEvents::onIncomingDamage,
            Flashing.Events::onIncomingDamage,
            MentalControlEvents::onLivingAttacked,
            VectorExternalInterceptionService::protectIncomingBoundary,
            DarkmatterEquipmentEvents.Events::onMatterDamageConversion,
            DarkmatterSixWings.Events::onIncomingDamage,
            AtmosphereShield.Events::onIncomingDamage,
            AbilityProgramTriggerRuntime::onIncomingDamage,
            DarkmatterEquipmentEvents.Events::onIncomingDamage,
            KineticEnergyApplied.Events::onIncomingDamage,
            CrossingTheAbyss.Events::onIncomingDamage,
            DarkmatterModifierRuntime.Events::onIncomingDamage,
            ElectromagneticShield.Events::onIncomingDamage,
            ElectricalContact.Events::onIncomingDamage,
            ElectricalContact.Events::onConductiveDamage,
            VectorExternalInterceptionService::onIncomingDamage
    );

    private AcademyDamageRules() {}

    public static boolean incoming(LivingEntity target, DamageContainer container) {
        var event = new LivingIncomingDamageEvent(target, container);
        for (var rule : INCOMING) {
            if (event.isCanceled() || target.isDeadOrDying()) break;
            rule.accept(event);
        }
        return event.isCanceled() || target.isDeadOrDying();
    }

    public static float pre(LivingEntity target, DamageContainer container) {
        CategoryDamageRuntime.track(target, container);
        var event = new LivingDamageEvent.Pre(target, container);
        VectorExternalInterceptionService.protectAppliedBoundary(event);
        DarkmatterSixWings.Events.onDarkmatterDamageApplied(event);
        var beforeOutput = event.getNewDamage();
        OutputControl.Events.onDamagePre(event);
        if (event.getNewDamage() > beforeOutput && container.getOriginalDamage() > 0.0f) {
            var percentage = Math.min(container.getOriginalDamage(),
                    org.academy.api.common.damage.DamageComposition.maximumHealthPart(target, container.getSource()));
            event.setNewDamage(beforeOutput + (event.getNewDamage() - beforeOutput)
                    * (1.0f - percentage / container.getOriginalDamage()));
        }
        event.setNewDamage(CategoryDamageRuntime.outgoingDamage(container.getSource(), event.getNewDamage()));
        VectorExternalInterceptionService.enforceAppliedBoundary(event);
        if (target instanceof ServerPlayer player) {
            AbilitySystemServer.getSystem(player).getPropsManager().onDamagePre(event);
        }
        return event.getNewDamage();
    }
}
