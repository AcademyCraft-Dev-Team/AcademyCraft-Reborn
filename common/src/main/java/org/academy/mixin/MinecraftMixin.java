package org.academy.mixin;

import net.minecraft.client.Minecraft;
import org.academy.AbilitySystemClient;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void run(CallbackInfo ci) {
        AbilitySystemClient.initClient();
    }
}