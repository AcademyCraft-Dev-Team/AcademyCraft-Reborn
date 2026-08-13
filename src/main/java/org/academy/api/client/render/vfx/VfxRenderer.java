package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.List;

public interface VfxRenderer<T extends VfxRenderData> {
    void init(GpuDevice device);

    void render(VfxRenderContext ctx, List<? extends T> data);

    default void submitWorldGeometry(VfxRenderContext ctx, PoseStack poseStack,
                                     SubmitNodeCollector output, List<? extends T> data) {
    }

    default void close() {
    }
}
