package org.academy.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.vanilla.ChangeScreenEvent;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow public abstract Window getWindow();

    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void run(CallbackInfo ci) {
        AcademyCraft.init();
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

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void setScreenPre(Screen screen, CallbackInfo ci) {
        ChangeScreenEvent.Pre pre = new ChangeScreenEvent.Pre(this.screen, screen);
        AcademyCraft.EVENT_BUS.post(pre);
        if (pre.isCanceled()) ci.cancel();
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void setScreenPost(Screen screen, CallbackInfo ci) {
        ChangeScreenEvent.Post post = new ChangeScreenEvent.Post(this.screen, screen);
        AcademyCraft.EVENT_BUS.post(post);
    }

    @Inject(method = "pauseGame",at = @At("TAIL"))
    private void pauseGame(CallbackInfo ci) {
        AcademyCraftClient.CLIENT_CONFIG.save();
    }
}