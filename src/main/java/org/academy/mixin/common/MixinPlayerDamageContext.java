package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.level0.skills.OutputControl;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.spongepowered.asm.mixin.Mixin;

/** Player overrides actuallyHurt, so LivingEntity's context injections do not cover player health writes. */
@Mixin(Player.class)
public abstract class MixinPlayerDamageContext {
    @WrapMethod(method = "actuallyHurt")
    private void academy$damageContext(ServerLevel level, DamageSource source, float amount, Operation<Void> original) {
        PlayerAttributeRuntime.pushDamageContext(source);
        OutputControl.pushDamageContext(source);
        try {
            original.call(level, source, amount);
        } finally {
            OutputControl.popDamageContext();
            PlayerAttributeRuntime.popDamageContext();
        }
    }
}
