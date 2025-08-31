package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.Render;
import org.academy.api.client.util.VertexUtil;

public final class BoxRenderer {
    public static void renderFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                       float r, float g, float b, float a) {
        final var vertexConsumer = bufferSource.getBuffer(Render.RenderTypes.BOX);
        final var matrix4f = poseStack.last().pose();

        var faces = VertexUtil.Box.getBoxVertices(box);

        for (var face : faces) {
            vertexConsumer.addVertex(matrix4f, face[0][0], face[0][1], face[0][2]).setColor(r, g, b, a);
            vertexConsumer.addVertex(matrix4f, face[1][0], face[1][1], face[1][2]).setColor(r, g, b, a);
            vertexConsumer.addVertex(matrix4f, face[2][0], face[2][1], face[2][2]).setColor(r, g, b, a);
            vertexConsumer.addVertex(matrix4f, face[3][0], face[3][1], face[3][2]).setColor(r, g, b, a);
        }
    }
}
