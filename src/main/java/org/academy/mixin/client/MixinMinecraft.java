package org.academy.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(CallbackInfo ci) {
        AcademyCraft.init();
        AcademyCraftClient.init();
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void resizeDisplay(CallbackInfo ci) {
        var event = new ResizeDisplayEvent(getWindow());
        NeoForge.EVENT_BUS.post(event);
    }
}