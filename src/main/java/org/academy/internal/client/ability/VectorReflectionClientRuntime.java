package org.academy.internal.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.coremod.ClassPointerProtectionManager;

import java.lang.ref.WeakReference;

public final class VectorReflectionClientRuntime {
    private static WeakReference<LocalPlayer> currentPlayer = new WeakReference<>(null);

    public static void tick(Minecraft minecraft) {
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

    private VectorReflectionClientRuntime() {
    }
}
