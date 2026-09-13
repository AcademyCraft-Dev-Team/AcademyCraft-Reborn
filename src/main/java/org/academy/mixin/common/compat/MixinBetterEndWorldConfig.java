package org.academy.mixin.common.compat;

import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import org.spongepowered.asm.mixin.Mixin;

/** Finalizes BetterEnd's merged handler after ordinary mixin injection. */
@Mixin(value = {EndSpikeFeature.class, EndSpikeFeature.EndSpike.class, EndPodiumFeature.class}, priority = 1)
public abstract class MixinBetterEndWorldConfig {}
