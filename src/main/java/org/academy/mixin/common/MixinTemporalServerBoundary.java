package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

/** Finalizes known simulation entry points after ordinary mixin injection. */
@Mixin(value = ServerLevel.class, priority = 1)
public abstract class MixinTemporalServerBoundary {}
