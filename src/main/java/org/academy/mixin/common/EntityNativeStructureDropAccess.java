package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Used only for unregistered native mining outputs, never for an existing entity or player. */
@Mixin(Entity.class)
public interface EntityNativeStructureDropAccess {
    @Invoker("setLevel")
    void academy$setNativeDropLevel(Level level);
}
