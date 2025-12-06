package org.academy.mixin.common;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.academy.internal.common.ability.accelerator.skills.VectorReflection;
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
    public boolean redirectHurtServer(Player instance, ServerLevel level, DamageSource source, float amount) {
        var pair = VectorReflection.Server.hurtServer(instance, level, source, amount);
        if (pair.getLeft()) return false;
        return super.hurtServer(level, source, pair.getRight());
    }
}