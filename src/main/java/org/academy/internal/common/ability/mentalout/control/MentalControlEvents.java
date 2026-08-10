package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.*;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationRuntime;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MentalControlEvents {
    private MentalControlEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled()) return;
        var subject = event.getEntity();
        var forcedTarget = MentalControlRuntime.getForcedTarget(subject);
        if (forcedTarget != null) {
            event.setNewAboutToBeSetTarget(forcedTarget);
        } else {
            var guardTarget = MentalControlRuntime.getGuardTarget(subject);
            if (guardTarget != null) {
                event.setNewAboutToBeSetTarget(guardTarget);
                return;
            }
            var proposedTarget = event.getNewAboutToBeSetTarget();
            if (proposedTarget != null
                    && MentalControlRuntime.attackDecision(subject, proposedTarget)
                    == AttackDecision.DENY) {
                event.setNewAboutToBeSetTarget(null);
            }
        }
        if (!event.isCanceled()
                && event.getNewAboutToBeSetTarget() instanceof ServerPlayer controller) {
            MentalControlRuntime.alertImpressionAllies(controller, subject);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingAttacked(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0f
                || !(event.getSource().getEntity() instanceof LivingEntity aggressor)
                || aggressor == event.getEntity()) return;
        if (MentalControlRuntime.attackDecision(aggressor, event.getEntity())
                == AttackDecision.DENY) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerControlSessionManager.onControllerDamaged(player, event.getHealthDamage());
        }
        if (event.getOriginalDamage() <= 0.0f
                || !(event.getSource().getEntity() instanceof LivingEntity aggressor)
                || aggressor == event.getEntity()) return;
        MentalControlRuntime.authorizeRetaliation(event.getEntity(), aggressor);
        if (event.getEntity() instanceof ServerPlayer controller) {
            MentalControlRuntime.alertImpressionAllies(controller, aggressor);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(ServerTickEvent.Pre event) {
        PlayerNavigationRuntime.beginServerTick(event.getServer().overworld().getGameTime());
        MentalControlRuntime.tick(event.getServer());
        MentalIntrusionManager.tick(event.getServer());
        PlayerControlSessionManager.tick(event.getServer());
        PrecisionOperationRuntime.tick(event.getServer());
        MentalControlRecall.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var entityId = event.getEntity().getUUID();
        MentaloutControlContext.releaseMisidentificationTarget(entityId);
        MentaloutControlContext.releaseSubject(entityId);
        MentalControlRuntime.releaseBySubject(level.getServer(), entityId);
        MentalIntrusionManager.releaseEntity(entityId);
        PlayerControlSessionManager.releaseEntity(entityId);
        PrecisionOperationRuntime.releaseEntity(level.getServer(), entityId);
        if (event.getEntity() instanceof ServerPlayer) {
            MentalControlRecall.releaseController(entityId);
            MentaloutControlContext.releaseController(entityId);
            MentalControlRuntime.releaseByController(level.getServer(), entityId);
            MentalIntrusionManager.releaseController(entityId);
            PrecisionOperationManager.releaseController((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        var entityId = event.getEntity().getUUID();
        MentaloutControlContext.releaseMisidentificationTarget(entityId);
        MentaloutControlContext.releaseSubject(entityId);
        MentalControlRuntime.releaseBySubject(level.getServer(), entityId);
        MentalIntrusionManager.releaseEntity(entityId);
        PlayerControlSessionManager.releaseEntity(entityId);
        PrecisionOperationRuntime.releaseEntity(level.getServer(), entityId);
        if (event.getEntity() instanceof ServerPlayer) {
            MentalControlRecall.releaseController(entityId);
            MentaloutControlContext.releaseController(entityId);
            MentalControlRuntime.releaseByController(level.getServer(), entityId);
            MentalIntrusionManager.releaseController(entityId);
            PrecisionOperationManager.releaseController((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MentaloutRequestGuard.release(player.getUUID());
        MentalControlRecall.releaseController(player.getUUID());
        MentaloutControlContext.releaseController(player.getUUID());
        MentalControlRuntime.releaseByController(player.level().getServer(), player.getUUID());
        MentalIntrusionManager.releaseEntity(player.getUUID());
        PlayerControlSessionManager.releaseEntity(player.getUUID());
        MentalIntrusionManager.releaseController(player.getUUID());
        PrecisionOperationRuntime.releaseEntity(player.level().getServer(), player.getUUID());
        PrecisionOperationManager.releaseController(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MentaloutControlContext.releaseMisidentificationTarget(player.getUUID());
        MentalControlRecall.releaseController(player.getUUID());
        MentaloutControlContext.releaseController(player.getUUID());
        MentalControlRuntime.releaseByController(player.level().getServer(), player.getUUID());
        MentalControlRuntime.releaseBySubject(player.level().getServer(), player.getUUID());
        MentalIntrusionManager.releaseEntity(player.getUUID());
        PlayerControlSessionManager.releaseEntity(player.getUUID());
        MentalIntrusionManager.releaseController(player.getUUID());
        PrecisionOperationRuntime.releaseEntity(player.level().getServer(), player.getUUID());
        PrecisionOperationManager.releaseController(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MentaloutRequestGuard.clear();
        MentalControlRecall.clear();
        MentaloutControlContext.clearAll();
        MentalControlRuntime.clear(event.getServer());
        MentalIntrusionManager.clear();
        PlayerControlSessionManager.clear();
        PrecisionOperationManager.clear(event.getServer());
    }
}
