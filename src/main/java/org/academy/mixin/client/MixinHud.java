package org.academy.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.academy.api.client.hud.terminal.TerminalHud;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class MixinHud {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void extractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (TerminalHud.Companion.isActive() || MentalIntrusionClientState.isActive()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void academy$extractMentalPerceptionOverlay(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (!MentalIntrusionClientState.isActive() && !MentalIntrusionClientState.hasFilters()) return;
        var width = graphics.guiWidth();
        var height = graphics.guiHeight();
        var pulse = (int) (36 + (Math.sin(System.nanoTime() * 0.000000003) + 1.0) * 22);
        var color = (pulse << 24) | (MentalIntrusionClientState.hasFilters() ? 0xB45CE8 : 0x53C7E8);
        graphics.fill(0, 0, width, 3, color);
        graphics.fill(0, height - 3, width, height, color);
        graphics.fill(0, 3, 3, height - 3, color);
        graphics.fill(width - 3, 3, width, height - 3, color);
    }
}
