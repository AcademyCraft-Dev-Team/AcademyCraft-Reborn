package org.academy.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow public abstract Window getWindow();

    @Inject(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void run(CallbackInfo ci) {
        AcademyCraftClient.init();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        AcademyCraft.EVENT_BUS.post(new ClientTickEvent());
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void resizeDisplay(CallbackInfo ci) {
        ResizeDisplayEvent event = new ResizeDisplayEvent(getWindow().getGuiScaledWidth(), getWindow().getGuiScaledHeight());
        AcademyCraft.EVENT_BUS.post(event);
    }
}