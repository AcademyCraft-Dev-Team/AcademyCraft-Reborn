package org.academy.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.academy.api.client.hud.terminal.TerminalHud;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.ability.mentalout.ControlledPlayerHudRenderer;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class MixinHud {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void extractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (TerminalHud.Companion.isActive()
                || MentalIntrusionClientState.isActive() && !PlayerControlClientState.isController()
                || PlayerControlClientState.isSubject()) ci.cancel();
    }

    @Inject(method = "extractHotbar", at = @At("HEAD"), cancellable = true)
    private void academy$replaceControlledHotbar(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (PlayerControlClientState.isController()
                && PlayerControlClientState.targetViewState() != null) ci.cancel();
    }

    @Inject(method = {
            "extractContextualInfoBarBackground",
            "extractExperienceLevel",
            "extractContextualInfoBar",
            "maybeExtractSelectedItemName"
    }, at = @At("HEAD"), cancellable = true)
    private void academy$replaceControlledStatus(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (PlayerControlClientState.isController()
                && PlayerControlClientState.targetViewState() != null) ci.cancel();
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void academy$replaceControlledPlayerHealth(
            GuiGraphicsExtractor graphics,
            CallbackInfo ci
    ) {
        if (PlayerControlClientState.isController()
                && PlayerControlClientState.targetViewState() != null) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void academy$extractMentalPerceptionOverlay(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (PlayerControlClientState.isController()) {
            ControlledPlayerHudRenderer.extract(graphics, deltaTracker);
        }
        if (!MentalIntrusionClientState.isActive() && !MentalIntrusionClientState.hasFilters()
                && !PlayerControlClientState.isActive()) return;
        var width = graphics.guiWidth();
        var height = graphics.guiHeight();
        var pulse = (int) (36 + (Math.sin(System.nanoTime() * 0.000000003) + 1.0) * 22);
        var color = (pulse << 24) | (MentalIntrusionClientState.hasFilters() ? 0xB45CE8 : 0x53C7E8);
        graphics.fill(0, 0, width, 3, color);
        graphics.fill(0, height - 3, width, height, color);
        graphics.fill(0, 3, 3, height - 3, color);
        graphics.fill(width - 3, 3, width, height - 3, color);
        if (PlayerControlClientState.isActive()) {
            var barWidth = Math.min(160, Math.max(80, width / 5));
            var left = (width - barWidth) / 2;
            var top = PlayerControlClientState.isController() ? height - 65 : height - 18;
            graphics.fill(left - 1, top - 1, left + barWidth + 1, top + 7, 0xB0000000);
            graphics.fill(left, top,
                    left + Math.round(barWidth * PlayerControlClientState.struggle() / 100.0f),
                    top + 6, 0xD8D95B6A);
            var maxCp = PlayerControlClientState.controllerMaxCp();
            if (maxCp > 0.0f) {
                graphics.fill(left, top + 9,
                        left + Math.round(barWidth * Math.clamp(
                                PlayerControlClientState.controllerCp() / maxCp, 0.0f, 1.0f)),
                        top + 11, 0xD85CB9D9);
            }
        }
    }
}
