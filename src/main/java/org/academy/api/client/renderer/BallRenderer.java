package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class BallRenderer {
    public static void renderBall(PoseStack.Pose pose, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                  float red, float green, float blue, float alpha, int packedLight, int packedOverlay) {
        for (var vertexData : vertexBuffer) {
            var x = vertexData[0];
            var y = vertexData[1];
            var z = vertexData[2];
            var nx = vertexData[3];
            var ny = vertexData[4];
            var nz = vertexData[5];

            vertexConsumer.addVertex(pose.pose(), x, y, z)
                    .setColor(red, green, blue, alpha)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private BallRenderer() {
    }
}
