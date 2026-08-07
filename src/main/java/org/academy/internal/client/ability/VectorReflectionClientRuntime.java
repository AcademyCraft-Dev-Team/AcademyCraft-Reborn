package org.academy.internal.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.coremod.ClassPointerProtectionManager;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public final class VectorReflectionClientRuntime {
    private static WeakReference<LocalPlayer> currentPlayer = new WeakReference<>(null);
    private static final Map<Integer, Long> PENDING_HURT_CLEARS = new HashMap<>();

    public static void tick(Minecraft minecraft) {
        tickFeedbackTokens(minecraft);
        var player = minecraft.player;
        var previous = currentPlayer.get();
        if (previous != null && previous != player) {
            ClassPointerProtectionManager.restore(previous);
        }
        currentPlayer = new WeakReference<>(player);
        if (player == null) return;
        if (!isProtected(player)) {
            ClassPointerProtectionManager.restore(player);
            return;
        }

        ClassPointerProtectionManager.ensureClientPlayer(player);
        sanitize(player);
        var level = minecraft.level;
        if (level != null && level.getEntity(player.getId()) != player) {
            player.revive();
            level.addEntity(player);
        }
    }

    public static void shutdown() {
        var player = currentPlayer.get();
        if (player != null) ClassPointerProtectionManager.restore(player);
        currentPlayer = new WeakReference<>(null);
        ClassPointerProtectionManager.restoreAllClient();
        PENDING_HURT_CLEARS.clear();
    }

    public static boolean isProtected(LocalPlayer player) {
        return player != null
                && AbilitySystemClient.isSkillLearned(Skills.VECTOR_REFLECTION.get())
                && AbilitySystemClient.getSkillData(Skills.VECTOR_REFLECTION.get())
                .map(data -> data.isEnabled() && AbilitySystemClient.getAvailableCP() > 0.0f)
                .orElse(false);
    }

    public static void sanitize(LocalPlayer player) {
        if (player.isRemoved()) player.revive();
        player.setTicksFrozen(0);
        player.setInvisible(false);
        player.clearFire();
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
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

    private VectorReflectionClientRuntime() {
    }
}
