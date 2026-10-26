package org.academy.mixin.common.compat;

import org.academy.internal.common.structure.sable.SableProvenanceLifecycle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class MixinSableProvenanceSplit {
    @WrapMethod(method = "assembleBlocks")
    @Coerce
    private static Object academy$inheritCreation(ServerLevel host, BlockPos anchor, Iterable<BlockPos> blocks,
                                                  @Coerce Object bounds, Operation<Object> original) {
        return SableProvenanceLifecycle.assemble(host, anchor, () -> original.call(host, anchor, blocks, bounds));
    }
}
