package org.academy.mixin.common;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Player {
    private MixinServerPlayer(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @SuppressWarnings("UnnecessarySuperQualifier")
    @Redirect(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    public boolean redirectHurtServer(Player instance, ServerLevel level, DamageSource source, float damage) {
        var pair = VectorReflection.Server.hurtServer(instance, level, source, damage);
        if (!pair.getLeft()) return super.hurtServer(level, source, pair.getRight());
        var remainingDamage = pair.getRight();
        if (!(remainingDamage > 0.0f) || !Float.isFinite(remainingDamage)) return false;
        var player = (ServerPlayer) (Object) this;
        VectorReflection.Server.beginLegitimateHealthMutation(player);
        try {
            return super.hurtServer(level, source, remainingDamage);
        } finally {
            VectorReflection.Server.endLegitimateHealthMutation(player, true);
        }
    }
}
