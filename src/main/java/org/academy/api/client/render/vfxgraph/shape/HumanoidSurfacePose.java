package org.academy.api.client.render.vfxgraph.shape;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Per-frame bone transforms for small surface effects, in the rendered model's local coordinates. */
public final class HumanoidSurfacePose {
    public static final int BODY = 0, HEAD = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    private static final Map<Integer, Matrix4f[]> POSES = new HashMap<>();
    private HumanoidSurfacePose() { }
    public static void beginFrame() { POSES.clear(); }
    public static Matrix4f[] get(int entityId) { return POSES.get(entityId); }

    public static void capture(int entityId, HumanoidModel<?> model) {
        var parts = new ModelPart[]{model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        var matrices = new Matrix4f[parts.length];
        for (int i = 0; i < parts.length; i++) {
            var pose = new PoseStack();
            model.root().translateAndRotate(pose);
            parts[i].translateAndRotate(pose);
            matrices[i] = new Matrix4f(pose.last().pose());
        }
        POSES.put(entityId, matrices);
    }
}
