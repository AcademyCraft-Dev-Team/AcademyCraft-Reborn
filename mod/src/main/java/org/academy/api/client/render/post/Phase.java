package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.academy.api.client.render.Render;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class Phase {
    private final StagedVertexBuffer stagedVertexBuffer;
    private final List<PhaseDraw> draws = new ArrayList<>();
    private @Nullable RenderType lastRenderType;
    private @Nullable PhaseDraw lastPhaseDraw;

    public Phase(String name) {
        stagedVertexBuffer = new StagedVertexBuffer(() -> "Phase - " + name, 524288);
    }

    public VertexConsumer getBuffer(RenderType renderType) {
        if (lastPhaseDraw == null || lastRenderType != renderType) {
            var draw = stagedVertexBuffer.appendDraw(
                    renderType.format(), renderType.primitiveTopology()
            );
            lastPhaseDraw = new PhaseDraw(draw, renderType);
            draws.add(lastPhaseDraw);
            lastRenderType = renderType;
        }
        return stagedVertexBuffer.getVertexBuilder(lastPhaseDraw.draw);
    }

    public void draw() {
        try {
            stagedVertexBuffer.upload();
            if (draws.isEmpty()) return;

            var grouped = new LinkedHashMap<RenderTarget, List<PhaseDraw>>();
            for (var phaseDraw : draws) {
                var target = Render.getOutputTarget(phaseDraw.renderType);
                grouped.computeIfAbsent(target, k -> new ArrayList<>()).add(phaseDraw);
            }

            for (var entry : grouped.entrySet()) {
                var target = entry.getKey();
                var colorTexture = target.getColorTextureView();
                if (colorTexture == null) continue;
                var depthTexture = target.getDepthTextureView();

                try (var renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> "Phase draw", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
                    for (var phaseDraw : entry.getValue()) {
                        var info = stagedVertexBuffer.getExecuteInfo(phaseDraw.draw);
                        if (info == null) continue;

                        var prepared = phaseDraw.renderType.prepare();

                        prepared.drawFromBuffer(info, renderPass);
                    }
                }
            }
        } finally {
            draws.clear();
            lastRenderType = null;
            lastPhaseDraw = null;
            stagedVertexBuffer.endFrame();
        }
    }

    public void close() {
        stagedVertexBuffer.close();
    }

    private record PhaseDraw(StagedVertexBuffer.Draw draw, RenderType renderType) {
    }
}
