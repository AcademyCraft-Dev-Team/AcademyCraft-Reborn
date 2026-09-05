package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.world.damagesource.AcademyDamageRules;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommonHooks.class)
public abstract class MixinCommonHooksDamageComposition {
    @WrapMethod(method = "onEntityIncomingDamage")
    private static boolean academy$incoming(LivingEntity entity, DamageContainer container, Operation<Boolean> original) {
        return DamageTypes.isAcademyDamage(container.getSource()) || container.getSource() instanceof SkillDamageSource
                ? AcademyDamageRules.incoming(entity, container) : original.call(entity, container);
    }

    @WrapMethod(method = "onLivingDamagePost")
    private static void academy$completed(LivingEntity entity, DamageContainer container, Operation<Void> original) {
        org.academy.internal.common.world.damagesource.CategoryDamageRuntime.completed(entity, container);
        original.call(entity, container);
    }

    @WrapMethod(method = "onLivingDamagePre")
    private static float academy$pre(LivingEntity entity, DamageContainer container, Operation<Float> original) {
        return DamageTypes.isAcademyDamage(container.getSource()) || container.getSource() instanceof SkillDamageSource
                ? AcademyDamageRules.pre(entity, container) : original.call(entity, container);
    }
}
