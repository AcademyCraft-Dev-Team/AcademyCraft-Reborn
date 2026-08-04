package org.academy.internal.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.coremod.VectorReflectionClassPtrTransformer;

public final class VectorReflectionClientRuntime {
    public static void tick(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null) return;
        if (!isProtected(player)) {
            VectorReflectionClassPtrTransformer.restoreOriginal(player);
            return;
        }

        VectorReflectionClassPtrTransformer.repairLocalPlayer(player);
        sanitize(player);
        var level = minecraft.level;
        if (level != null && level.getEntity(player.getId()) != player) {
            player.revive();
            level.addEntity(player);
        }
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
