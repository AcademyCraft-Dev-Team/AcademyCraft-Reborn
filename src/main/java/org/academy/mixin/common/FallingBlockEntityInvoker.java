package org.academy.mixin.common;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creates a candidate without vanilla fall() removing a world block before entity admission. */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityInvoker {
    @Invoker("<init>")
    static FallingBlockEntity academy$create(Level level, double x, double y, double z, BlockState state) {
        throw new AssertionError();
    }
}
