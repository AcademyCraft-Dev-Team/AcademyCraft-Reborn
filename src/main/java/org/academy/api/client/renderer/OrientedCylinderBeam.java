package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.util.VertexUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared oriented cylinder beam layers (Y-up local space scaled to length/radius).
 */
public final class OrientedCylinderBeam {
    private static final float[][] CYLINDER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 12, true);

    private OrientedCylinderBeam() {
    }

    /**
     * Push pose at {@code start}, rotate local +Y toward {@code direction}. Caller must {@link PoseStack#popPose()}.
     *
     * @return false if length/direction invalid (pose not pushed)
     */
    public static boolean preparePose(PoseStack poseStack, Vec3 start, Vec3 direction, float length) {
        var directionLengthSqr = direction == null ? 0.0 : direction.lengthSqr();
        if (!(length > 0.0f)
                || !Float.isFinite(length)
                || !Double.isFinite(directionLengthSqr)
                || directionLengthSqr <= 1.0e-12) {
            return false;
        }
        var normalized = direction.normalize();
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f((float) normalized.x, (float) normalized.y, (float) normalized.z)
        ));
        return true;
    }

    public static void submitLayer(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float length,
            float radius,
            RenderType renderType,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        poseStack.pushPose();
        poseStack.scale(radius, length, radius);
        nodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> CylinderRenderer.renderCylinder(
                        pose.pose(), consumer, CYLINDER, red, green, blue, alpha
                )
        );
        poseStack.popPose();
    }
}
