package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only mutual exclusion with vanilla host-world mining. */
@Mixin(ServerPlayerGameMode.class)
public interface MixinServerPlayerGameModeNativeStructureAccess {
    @Accessor("isDestroyingBlock")
    boolean academy$isDestroyingBlock();

    @Accessor("hasDelayedDestroy")
    boolean academy$hasDelayedDestroy();
}
