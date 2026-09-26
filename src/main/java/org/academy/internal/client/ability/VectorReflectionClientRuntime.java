package org.academy.internal.client.ability;

import net.minecraft.world.effect.MobEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.proficiency.ProficiencyPolicy;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.ability.accelerator.reflection.VectorHealthLedger;
import org.academy.mixin.common.LivingEntityDamageInvoker;

import java.lang.ref.WeakReference;
import java.util.*;

public final class VectorReflectionClientRuntime {
    private static final Map<Integer, Long> PENDING_HURT_CLEARS = new HashMap<>();
    private static final Set<UUID> FORCED_DEACTIVATIONS = new HashSet<>();
    private static WeakReference<LocalPlayer> currentPlayer = new WeakReference<>(null);

    private VectorReflectionClientRuntime() {
    }

    public static void tick(Minecraft minecraft) {
        tickFeedbackTokens(minecraft);
        var player = minecraft.player;
        var previous = currentPlayer.get();
        if (previous != null && previous != player) {
            VectorHealthLedger.disarm(previous);
        }
        currentPlayer = new WeakReference<>(player);
        if (player == null) return;

        if (!isProtected(player)) {
            VectorHealthLedger.disarm(player);
            return;
        }

        VectorHealthLedger.arm(player);
        player.getHealth();
        VectorHealthLedger.repair(player);
        sanitize(player);
        var level = minecraft.level;
        if (level != null && level.getEntity(player.getId()) != player) {
            player.revive();
            level.addEntity(player);
        }
    }

    public static void shutdown() {
        var player = currentPlayer.get();
        currentPlayer = new WeakReference<>(null);
        if (player != null) VectorHealthLedger.disarm(player);
        VectorHealthLedger.disarmSide(true);
        PENDING_HURT_CLEARS.clear();
        FORCED_DEACTIVATIONS.clear();
    }

    public static boolean isProtected(LocalPlayer player) {
        if (player == null) return false;
        var reflection = AbilitySystemClient.isSkillLearned(Skills.VECTOR_REFLECTION.get())
                && AbilitySystemClient.getSkillData(Skills.VECTOR_REFLECTION.get())
                .map(data -> data.isEnabled() && AbilitySystemClient.getAvailableCP() > 0.0f)
                .orElse(false);
        var protectedByVectorDefense = reflection || isVectorDeviationFullyProtected(player);
        var uuid = player.getUUID();
        if (!protectedByVectorDefense) {
            FORCED_DEACTIVATIONS.remove(uuid);
            return false;
        }
        return !FORCED_DEACTIVATIONS.contains(uuid);
    }

    public static boolean isReflectionProtected(LocalPlayer player) {
        return isProtected(player)
                && AbilitySystemClient.isSkillLearned(Skills.VECTOR_REFLECTION.get())
                && AbilitySystemClient.getSkillData(Skills.VECTOR_REFLECTION.get())
                .map(data -> data.isEnabled() && AbilitySystemClient.getAvailableCP() > 0.0f)
                .orElse(false);
    }

    public static boolean shouldReflectEffect(LocalPlayer player, MobEffectInstance effect) {
        if (!isReflectionProtected(player) || effect == null) return false;
        var data = AbilitySystemClient
                .getSkillData(Skills.REFLECTION_FILTER.get(), ReflectionFilter.Data.class)
                .filter(ReflectionFilter.Data::isEnabled)
                .orElseGet(ReflectionFilter.Data::new);
        return !ReflectionFilter.shouldAcceptEffect(data, effect);
    }

    public static boolean shouldPreventInvisibility(LocalPlayer player) {
        if (!isProtected(player)) return false;
        var effect = player.getEffect(MobEffects.INVISIBILITY);
        return effect == null || shouldReflectEffect(player, effect);
    }

    private static boolean isVectorDeviationActive(LocalPlayer player) {
        return player != null
                && AbilitySystemClient.isSkillLearned(Skills.VECTOR_DEVIATION.get())
                && AbilitySystemClient.getSkillData(Skills.VECTOR_DEVIATION.get())
                .map(data -> data.isEnabled() && AbilitySystemClient.getAvailableCP() > 0.0f)
                .orElse(false);
    }

    private static boolean isVectorDeviationFullyProtected(LocalPlayer player) {
        return isVectorDeviationActive(player)
                && ProficiencyPolicy.client().enabled()
                && AbilitySystemClient.getSkillProficiencyMilestone(
                Skills.VECTOR_DEVIATION.get()) >= 3;
    }

    public static void imaginebreaker(LocalPlayer player, float amount) {
        if (player == null || !Float.isFinite(amount) || !(amount > 0.0f)) return;
        if (!isProtected(player)) return;

        VectorHealthLedger.arm(player);
        var remaining = Math.max(0.0f, player.getHealth() - amount);
        var accepted = VectorHealthLedger.writeAuthorized(player, remaining,
                () -> setOriginalHealth(player, remaining));
        if (accepted && remaining <= 0.0f) markImagineBreakerDepleted(player);
    }

    public static void markImagineBreakerDepleted(LocalPlayer player) {
        if (player != null) FORCED_DEACTIVATIONS.add(player.getUUID());
    }

    public static void sanitize(LocalPlayer player) {
        if (player.isRemoved()) player.revive();
        ((LivingEntityDamageInvoker) player).academy$setDead(false);
        clearHurtState(player);
        player.deathTime = 0;
        if (player.getPose() == Pose.DYING) player.setPose(Pose.STANDING);
        player.invulnerableTime = 0;
        player.setTicksFrozen(0);
        player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY)
                && !shouldPreventInvisibility(player));
        player.clearFire();
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        for (var effect : Set.copyOf(player.getActiveEffects())) {
            if (shouldReflectEffect(player, effect)) {
                player.removeEffectNoUpdate(effect.getEffect());
            }
        }
    }

    public static void confirmFullDefense(int entityId, long serverTick) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var entity = level.getEntity(entityId);
        var cleared = entity instanceof LivingEntity living && clearHurtState(living);
        if (!cleared) PENDING_HURT_CLEARS.put(entityId, level.getGameTime() + 3L);
    }

    private static void tickFeedbackTokens(Minecraft minecraft) {
        var level = minecraft.level;
        if (level == null) {
            PENDING_HURT_CLEARS.clear();
            return;
        }
        var now = level.getGameTime();
        var iterator = PENDING_HURT_CLEARS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() < now) {
                iterator.remove();
                continue;
            }
            var entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && clearHurtState(living)) iterator.remove();
        }
    }

    private static boolean clearHurtState(LivingEntity living) {
        var dirty = living.hurtTime > 0 || living.hurtDuration > 0 || living.hurtMarked;
        if (!dirty) return false;
        living.hurtTime = 0;
        living.hurtDuration = 0;
        living.hurtMarked = false;
        return true;
    }

    private static boolean setOriginalHealth(LocalPlayer player, float health) {
        var accepted = new boolean[1];
        PlayerAttributeRuntime.runWithoutResistance(
                () -> accepted[0] = EntityControlApi.forceSetTrueHealth(player, health));
        return accepted[0];
    }
}
