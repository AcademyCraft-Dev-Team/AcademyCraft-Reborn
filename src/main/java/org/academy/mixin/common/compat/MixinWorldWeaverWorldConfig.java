package org.academy.mixin.common.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import org.academy.internal.common.compat.WorldWeaverConfigAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Optional: no WorldWeaver classes are linked when the mod is absent. */
@Pseudo
@Mixin(targets = "org.betterx.wover.state.impl.WorldConfigImpl", remap = false)
public abstract class MixinWorldWeaverWorldConfig {
    @WrapMethod(method = "getRootTag")
    private static CompoundTag academy$root(@Coerce Object mod, Operation<CompoundTag> original) {
        return WorldWeaverConfigAccess.locked(() -> original.call(mod));
    }

    @WrapMethod(method = "getCompoundTag")
    private static CompoundTag academy$compound(@Coerce Object mod, String path, Operation<CompoundTag> original) {
        return WorldWeaverConfigAccess.locked(() -> original.call(mod, path));
    }

    @WrapMethod(method = "saveFile")
    private static void academy$save(@Coerce Object mod, Operation<Void> original) {
        WorldWeaverConfigAccess.locked(() -> original.call(mod));
    }
}
