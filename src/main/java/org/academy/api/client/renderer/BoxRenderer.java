package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.util.VertexUtil;

/**
 * Quads 喵
 */
public final class BoxRenderer {
    public static void renderFilledBox(PoseStack poseStack, VertexConsumer vertexConsumer, AABB box,
                                       float r, float g, float b, float a) {
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