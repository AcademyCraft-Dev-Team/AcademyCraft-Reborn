package org.academy.api.client.hud.ability;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.widget.FrameLayoutWidget;

import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(Dist.CLIENT)
public final class AbilityInfoHUD {
    private static final FrameLayoutWidget ROOT = new FrameLayoutWidget();

    private static void init() {
        ROOT.setName("root");
        ROOT.clearChildren();
    }

    public static void resize() {
    }

    public static void render(
            int width, int height,
            GpuTextureView color,
            GpuTextureView depth,
            AtomicBoolean drew
    ) {
        if (AbilitySystemClient.isActiveHUD()) {

        }
    }

    private AbilityInfoHUD() {
    }
}