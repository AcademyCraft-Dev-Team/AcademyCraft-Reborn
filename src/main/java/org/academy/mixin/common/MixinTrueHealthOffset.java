package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.HealthOffsetAccess;
import org.academy.internal.common.entitycontrol.HealthOffsetState;
import org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class MixinTrueHealthOffset implements HealthOffsetAccess {
    @Unique private @Nullable HealthOffsetState academy$offsetState;

    @Override
    public @Nullable HealthOffsetState academy$getHealthOffset() {
        return academy$offsetState;
    }

    @Override
    public void academy$setHealthOffset(@Nullable HealthOffsetState state) {
        academy$offsetState = state;
    }

    @Override
    public void academy$syncHealthOffset(float ceiling) {
        var entity = (LivingEntity) (Object) this;
        if (ceiling < 0) {
            if (entity.hasData(AttachmentTypes.TRUE_HEALTH_CEILING)) entity.removeData(AttachmentTypes.TRUE_HEALTH_CEILING);
        } else if (!entity.hasData(AttachmentTypes.TRUE_HEALTH_CEILING)
                || entity.getData(AttachmentTypes.TRUE_HEALTH_CEILING) != ceiling) {
            entity.setData(AttachmentTypes.TRUE_HEALTH_CEILING, ceiling);
        }
    }

    // Physically inlined into getHealth by MixinPlugin; the helper is then removed.
    @ModifyReturnValue(method = "getHealth", at = @At("RETURN"))
    private float academy$inlineOffsetRead(float original) {
        if (TrueHealthOffsetRuntime.RAW_READ.get()) return original;
        var entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) {
            if (!entity.hasData(AttachmentTypes.TRUE_HEALTH_CEILING)) return original;
            float ceiling = entity.getData(AttachmentTypes.TRUE_HEALTH_CEILING);
            return ceiling >= 0 && Float.isFinite(ceiling) ? Math.min(original, ceiling) : original;
        }
        var state = academy$offsetState;
        if (state == null) return original;
        double offset = Double.longBitsToDouble(state.encodedOffset ^ state.mask);
        if (!Double.isFinite(offset) || offset < 0) return original;
        return Math.min(original, (float) Math.max(0, state.maximum - offset));
    }

    // The invocation occurs after NeoForge has adjusted/canceled the heal event.
    @WrapOperation(method = "heal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void academy$acceptHealing(LivingEntity entity, float requested, Operation<Void> original) {
        var state = academy$offsetState;
        if (state == null || state.dead || entity.level().isClientSide()
                || org.academy.internal.common.world.damagesource.TrueDamageCompatibility.isHurtNotification(entity)) {
            original.call(entity, requested);
            return;
        }
        float before = entity.getHealth();
        long saved = state.encodedOffset;
        state.heal(Math.max(0, requested - before));
        try {
            original.call(entity, requested);
        } finally {
            float gained = Math.max(0, entity.getHealth() - before);
            state.encodedOffset = saved;
            state.heal(gained);
            TrueHealthOffsetRuntime.persistAndSync(entity);
        }
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "checkTotemDeathProtection")
    private boolean academy$releaseForDeathProtection(net.minecraft.world.damagesource.DamageSource source,
                                                       Operation<Boolean> original) {
        var state = academy$offsetState;
        if (state == null) return original.call(source);
        academy$offsetState = null;
        boolean protectedFromDeath = false;
        try {
            protectedFromDeath = original.call(source);
            return protectedFromDeath;
        } finally {
            if (protectedFromDeath) TrueHealthOffsetRuntime.clear((LivingEntity) (Object) this);
            else academy$offsetState = state;
        }
    }
}
