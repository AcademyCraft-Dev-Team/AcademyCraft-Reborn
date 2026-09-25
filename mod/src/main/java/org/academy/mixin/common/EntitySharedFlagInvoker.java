package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntitySharedFlagInvoker {
    @Invoker("setSharedFlag")
    void academy$setSharedFlag(int flag, boolean value);
}
