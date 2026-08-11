package org.academy.internal.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.ReflectionHealthRecordCodec;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.coremod.ClassPointerProtectionManager;
import org.academy.internal.coremod.ProtectionBackend;

import java.lang.ref.WeakReference;
import java.util.*;

public final class VectorReflectionClientRuntime {
    private static final Map<Integer, Long> PENDING_HURT_CLEARS = new HashMap<>();
    private static final Map<UUID, String> HEALTH_RECORDS = new HashMap<>();
    private static final Set<UUID> IMAGINE_BREAKER_MUTATIONS = new HashSet<>();
    private static final Set<UUID> FORCED_DEACTIVATIONS = new HashSet<>();
    private static WeakReference<LocalPlayer> currentPlayer = new WeakReference<>(null);

    private VectorReflectionClientRuntime() {
    }

    public static void tick(Minecraft minecraft) {
        tickFeedbackTokens(minecraft);
        var player = minecraft.player;
        var previous = currentPlayer.get();
        if (previous != null && previous != player) {
            deactivate(previous);
            ClassPointerProtectionManager.restore(previous);
        }
        currentPlayer = new WeakReference<>(player);
        if (player == null) return;

        // Arm the dispatch class as soon as either vector defense is learned and retain it for the
        // lifetime of this LocalPlayer. The generated overrides delegate to vanilla while
        // reflection is disabled, so toggling a skill no longer changes the runtime class between
        // two rendered frames.
        if (shouldKeepClassPointerArmed(player)) {
            ClassPointerProtectionManager.ensureClientPlayer(player);
        }
        if (!isProtected(player)) {
            deactivate(player);
            return;
        }

        player.getHealth();
        sanitize(player);
        var level = minecraft.level;
        if (level != null && level.getEntity(player.getId()) != player) {
            player.revive();
            level.addEntity(player);
        }
    }

    public static void shutdown() {
        var player = currentPlayer.get();
        if (player != null) deactivate(player);
        currentPlayer = new WeakReference<>(null);
        ClassPointerProtectionManager.restoreAllClient();
        PENDING_HURT_CLEARS.clear();
        HEALTH_RECORDS.clear();
        IMAGINE_BREAKER_MUTATIONS.clear();
        FORCED_DEACTIVATIONS.clear();
    }

    public static boolean isProtected(LocalPlayer player) {
        if (player == null) return false;
        var enabled = AbilitySystemClient.isSkillLearned(Skills.VECTOR_REFLECTION.get())
                && AbilitySystemClient.getSkillData(Skills.VECTOR_REFLECTION.get())
                .map(data -> data.isEnabled() && AbilitySystemClient.getAvailableCP() > 0.0f)
                .orElse(false);
        if (!enabled) {
            FORCED_DEACTIVATIONS.remove(player.getUUID());
            return false;
        }
        return !FORCED_DEACTIVATIONS.contains(player.getUUID());
    }

    public static boolean shouldReflectEffect(LocalPlayer player, MobEffectInstance effect) {
        if (!isProtected(player) || effect == null) return false;
        var data = AbilitySystemClient
                .getSkillData(Skills.REFLECTION_FILTER.get(), ReflectionFilter.Data.class)
                .filter(ReflectionFilter.Data::isEnabled)
                .orElseGet(ReflectionFilter.Data::new);
        return !ReflectionFilter.shouldAcceptEffect(data, effect);
    }

    private static boolean isVectorReductionActive(LocalPlayer player) {
        return player != null
                && AbilitySystemClient.isSkillLearned(Skills.VECTOR_REDUCTION.get())
                && AbilitySystemClient.getSkillData(Skills.VECTOR_REDUCTION.get())
                .map(data -> data.isEnabled() && AbilitySystemClient.getAvailableCP() > 0.0f)
                .orElse(false);
    }

    private static boolean shouldKeepClassPointerArmed(LocalPlayer player) {
        if (player == null) return false;
        if (ClassPointerProtectionManager.backend(player)
                == ProtectionBackend.CLASS_POINTER) {
            return true;
        }
        return AbilitySystemClient.isSkillLearned(Skills.VECTOR_REFLECTION.get())
                || AbilitySystemClient.isSkillLearned(Skills.VECTOR_REDUCTION.get());
    }

    public static float protectHealthRead(LocalPlayer player, float original) {
        if (player == null) return Math.max(0.0f, original);
        var uuid = player.getUUID();
        if (IMAGINE_BREAKER_MUTATIONS.contains(uuid)) {
            return ReflectionHealthRecordCodec.lockedHealth(0.0f, original);
        }
        var encoded = HEALTH_RECORDS.computeIfAbsent(
                uuid,
                ignored -> ReflectionHealthRecordCodec.encode(uuid, original)
        );
        var recorded = ReflectionHealthRecordCodec.decode(uuid, encoded, original);
        return ReflectionHealthRecordCodec.lockedHealth(recorded, original);
    }

    public static void imaginebreaker(LocalPlayer player, float amount) {
        if (player == null || !Float.isFinite(amount) || !(amount > 0.0f)) return;
        var reflectionActive = isProtected(player);
        if (!reflectionActive && !isVectorReductionActive(player)) return;

        var uuid = player.getUUID();
        if (reflectionActive) player.getHealth();
        IMAGINE_BREAKER_MUTATIONS.add(uuid);
        var depleted = false;
        try {
            var original = player.getHealth();
            var nextOriginal = ReflectionHealthRecordCodec.loweredHealth(original, amount);
            if (reflectionActive) {
                var encoded = HEALTH_RECORDS.computeIfAbsent(
                        uuid,
                        ignored -> ReflectionHealthRecordCodec.encode(uuid, original)
                );
                var recorded = ReflectionHealthRecordCodec.decode(uuid, encoded, original);
                var nextRecorded = ReflectionHealthRecordCodec.loweredHealth(recorded, amount);
                HEALTH_RECORDS.put(uuid, ReflectionHealthRecordCodec.encode(uuid, nextRecorded));
                depleted = ReflectionHealthRecordCodec
                        .lockedHealth(nextRecorded, nextOriginal) <= 0.0f;
            } else {
                depleted = nextOriginal <= 0.0f;
            }
            setOriginalHealth(player, nextOriginal);
        } finally {
            IMAGINE_BREAKER_MUTATIONS.remove(uuid);
        }

        if (depleted && reflectionActive) {
            FORCED_DEACTIVATIONS.add(uuid);
            deactivate(player);
        }
    }

    public static void sanitize(LocalPlayer player) {
        if (player.isRemoved()) player.revive();
        player.setTicksFrozen(0);
        player.setInvisible(false);
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

    private static void deactivate(LocalPlayer player) {
        var uuid = player.getUUID();
        var encoded = HEALTH_RECORDS.get(uuid);
        if (encoded == null) return;
        var reported = player.getHealth();
        var restored = ReflectionHealthRecordCodec.lockedHealth(
                ReflectionHealthRecordCodec.decode(uuid, encoded, reported), reported);
        HEALTH_RECORDS.remove(uuid);
        if (Float.isFinite(restored)) setOriginalHealth(player, Math.max(0.0f, restored));
    }

    private static void setOriginalHealth(LocalPlayer player, float health) {
        PlayerAttributeRuntime.runWithoutResistance(
                () -> EntityControlApi.forceSetTrueHealth(player, health)
        );
    }
}
