package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.event.SkillExecutionPreEvent;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.server.ability.SkillTuning;
import org.academy.api.server.time.TemporalApi;
import org.academy.internal.common.ability.AbilityCategories;
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
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.time.TemporalRuntime;

import java.util.List;
import java.util.function.Consumer;

/**
 * Academy's own combat rules, in priority order. Mutable external events are deliberately
 * not posted for Academy damage. Vanilla armor/absorption and immutable Post notifications
 * continue to use the original damage path. Keep new category defenses in this list as well
 * as their ordinary-damage event adapters.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AcademyDamageRules {
    private static final List<Consumer<LivingIncomingDamageEvent>> INCOMING = List.of(
            AcademyDamageRules::onIncomingDamage,
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

    private AcademyDamageRules() {
    }

    public static boolean incoming(LivingEntity target, DamageContainer container) {
        var event = new LivingIncomingDamageEvent(target, container);
        for (var rule : INCOMING) {
            if (event.isCanceled() || target.isDeadOrDying()) break;
            rule.accept(event);
        }
        return event.isCanceled() || target.isDeadOrDying();
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (AbilityCategories.ELECTROMASTER.get().blocksOutgoingDamage(event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOrdinaryDamagePre(LivingDamageEvent.Pre event) {
        event.setNewDamage(AbilityCategories.ELECTROMASTER.get().outgoingDamage(
                event.getSource(), event.getNewDamage()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSkillUse(SkillExecutionPreEvent event) {
        var player = event.player();
        var time = (TemporalRuntime) TemporalApi.get(player);
        if (AbilityCategories.ELECTROMASTER.get().electricalInterruptionTicks(player) > 0
                || !event.continuous() && !time.isPlayerActionTick(player)) event.setCanceled(true);
    }

    public static float pre(LivingEntity target, DamageContainer container) {
        DamageCompletionTracker.of(target).track(target, container);
        var event = new LivingDamageEvent.Pre(target, container);
        VectorExternalInterceptionService.protectAppliedBoundary(event);
        DarkmatterSixWings.Events.onDarkmatterDamageApplied(event);
        var beforeOutput = event.getNewDamage();
        OutputControl.Events.onDamagePre(event);
        if (event.getNewDamage() > beforeOutput && container.getOriginalDamage() > 0.0f) {
            var percentage = Math.min(container.getOriginalDamage(),
                    DamageComposition.maximumHealthPart(target, container.getSource()));
            event.setNewDamage(beforeOutput + (event.getNewDamage() - beforeOutput)
                    * (1.0f - percentage / container.getOriginalDamage()));
        }
        event.setNewDamage(AbilityCategories.ELECTROMASTER.get().outgoingDamage(
                container.getSource(), event.getNewDamage()));
        event.setNewDamage(SkillTuning.scaleSkillDamage(
                container.getSource(), event.getNewDamage(),
                DamageComposition.maximumHealthPart(
                        target, container.getSource())));
        VectorExternalInterceptionService.enforceAppliedBoundary(event);
        if (target instanceof ServerPlayer player) {
            AbilitySystemServer.getSystem(player).getPropsManager().onDamagePre(event);
        }
        return event.getNewDamage();
    }

    public static void completed(LivingEntity target, DamageContainer container) {
        var healthDamage = DamageCompletionTracker.of(target).complete(target, container);
        if (!(healthDamage > 0.0f)) return;
        var source = (org.academy.api.common.damage.SkillDamageSource) container.getSource();
        var category = source.getSkill().getCategory();
        if (category == AbilityCategories.ELECTROMASTER.get()) {
            AbilityCategories.ELECTROMASTER.get().onDamageCompleted(target, container, healthDamage);
        } else if (category == AbilityCategories.MELTDOWNER.get()) {
            AbilityCategories.MELTDOWNER.get().onDamageCompleted(target, container, healthDamage);
        } else if (category == AbilityCategories.AEROMANIP.get()) {
            AbilityCategories.AEROMANIP.get().onDamageCompleted(target, container, healthDamage);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickEvent(ServerTickEvent.Pre event) {
        AbilityCategories.ELECTROMASTER.get().tick(event.getServer());
        AbilityCategories.AEROMANIP.get().tick(event.getServer().getTickCount());
    }

    @SubscribeEvent
    public static void leaveEvent(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(event.getLevel() instanceof ServerLevel)) return;
        AbilityCategories.ELECTROMASTER.get().leave(target);
        AbilityCategories.AEROMANIP.get().leave(target);
    }

    @SubscribeEvent
    public static void stopEvent(ServerStoppedEvent event) {
        DamageCompletionTracker.of(event.getServer()).clear();
        AbilityCategories.ELECTROMASTER.get().stop();
        AbilityCategories.AEROMANIP.get().stop();
    }
}
