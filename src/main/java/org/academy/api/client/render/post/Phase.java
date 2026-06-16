package org.academy.api.client.render.post;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        stagedVertexBuffer.upload();
        for (var phaseDraw : draws) {
            var info = stagedVertexBuffer.getExecuteInfo(phaseDraw.draw);
            if (info == null) continue;

            var prepared = phaseDraw.renderType.prepare();

            prepared.drawFromBuffer(info);
        }
        draws.clear();
        lastRenderType = null;
        lastPhaseDraw = null;
        stagedVertexBuffer.endDraw();
    }

    public void close() {
        stagedVertexBuffer.close();
    }

    private record PhaseDraw(StagedVertexBuffer.Draw draw, RenderType renderType) {
    }
}