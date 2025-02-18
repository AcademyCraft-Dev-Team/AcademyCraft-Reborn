package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import static net.minecraft.client.renderer.RenderStateShard.*;

public class RenderUtil {
    public static final RenderType.CompositeRenderType GLOWING_CYLINDER = RenderType.create("glowing_cylinder", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, 10240, false, true, RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).createCompositeState(false));

    public static void translateToForward(final PoseStack poseStack, final LivingEntity livingEntity, final float distance) {
        final Vec3 lookVec = livingEntity.getLookAngle();

        poseStack.translate(
                lookVec.x * distance,
                lookVec.y * distance,
                lookVec.z * distance
        );
    }

    public static void addVertex(Matrix4f matrix4f, Matrix3f matrix3f, final VertexConsumer vertexConsumer, float r, float g, float b, float a, float x, float y, float z, float nx, float ny, float nz) {
        vertexConsumer.vertex(matrix4f, x, y, z).color(r, g, b, a).overlayCoords(OverlayTexture.NO_OVERLAY).normal(matrix3f, nx, ny, nz).endVertex();
    }

    public static class RayRenderer {
        /**
         * 按照指定面数生成光束侧面四边形
         *
         * @param poseStack      渲染矩阵栈
         * @param vertexConsumer 用于绘制顶点的对象
         * @param red            红色分量
         * @param green          绿色分量
         * @param blue           蓝色分量
         * @param alpha          透明度
         * @param yBottom        光束底部 Y 坐标
         * @param yTop           光束顶部 Y 坐标
         * @param radius         用于计算顶点位置的半径
         * @param faces          光束侧面的面数（例如4、8、16等）
         */
        public static void renderRay(PoseStack poseStack, VertexConsumer vertexConsumer, float red, float green, float blue, float alpha, float yBottom, float yTop, float radius, int faces) {
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix4f = pose.pose();
            Matrix3f matrix3f = pose.normal();

            // 计算每个面的角度步长（单位：弧度）
            double angleStep = 2 * Math.PI / faces;

            // 遍历所有面，使用 TRIANGLE_STRIP 方式优化顶点排列
            for (int i = 0; i <= faces; i++) { // i <= faces 保证首尾相连
                double angle = i * angleStep;

                float x = (float) (radius * Math.cos(angle));
                float z = (float) (radius * Math.sin(angle));

                addVertex(matrix4f, matrix3f, vertexConsumer, red, green, blue, alpha, x, yTop, z, 0, 1, 0);
                addVertex(matrix4f, matrix3f, vertexConsumer, red, green, blue, alpha, x, yBottom, z, 0, 1, 0);
            }
        }
    }

    public static class DummyRenderer {
        public static void renderDummy(float x, float y, float z, VertexConsumer vertexConsumer) {

        }
    }
}