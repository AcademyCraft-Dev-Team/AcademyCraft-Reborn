package org.academy.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the opt-in integrated probe exercise the same input methods as actual key presses. */
@Mixin(Minecraft.class)
public interface MixinMinecraftNativeMiningAccess {
    @Invoker("startAttack") boolean academy$startNativeMiningAttack();
    @Invoker("continueAttack") void academy$continueNativeMiningAttack(boolean down);
}
