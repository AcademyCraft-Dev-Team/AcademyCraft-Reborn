package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.server.damage.HealthLossGuards;
import org.academy.internal.common.entitycontrol.HealthDataOwner;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Protects the actual data value, including writes that bypass LivingEntity.setHealth. */
@Mixin(SynchedEntityData.DataItem.class)
public abstract class MixinHealthDataItem implements HealthDataOwner {
    @Unique private @Nullable LivingEntity academy$healthOwner;

    @Override public void academy$bindHealthOwner(LivingEntity owner) { academy$healthOwner = owner; }

    @WrapOperation(method = "setValue", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;value:Ljava/lang/Object;"))
    private void academy$submitHealth(SynchedEntityData.DataItem<?> item, Object requested, Operation<Void> original) {
        if (academy$healthOwner == null || !(requested instanceof Float value) || !(item.getValue() instanceof Float current)) {
            original.call(item, requested);
            return;
        }
        HealthLossGuards.commit(academy$healthOwner, current, value, protectedValue -> {
            var settled = (float) protectedValue;
            original.call(item, Float.valueOf(settled));
            return item.getValue() instanceof Float actual && Float.compare(actual, settled) == 0;
        });
    }
}
