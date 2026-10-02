package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public final class WingAvatarRegistry {
    private static final Map<Integer, Matrix4f> ROOTS = new HashMap<>();
    private static final Map<Integer, Matrix4f[]> HANDS = new HashMap<>();

    private WingAvatarRegistry() {
    }

    public static void beginFrame() {
        ROOTS.clear();
        HANDS.clear();
        org.academy.api.client.render.vfxgraph.shape.HumanoidSurfacePose.beginFrame();
    }

    public static void capture(int entityId, Matrix4f root) {
        ROOTS.put(entityId, root);
    }

    public static Map<Integer, Matrix4f> entries() {
        return Map.copyOf(ROOTS);
    }

    public static void captureHands(AvatarRenderState state, PlayerModel model) {
        var hands = new Matrix4f[2];
        for (var arm : HumanoidArm.values()) {
            var pose = new PoseStack();
            model.translateToHand(state, arm, pose);
            // Palm position in arm-local model units (16 pixels per block).
            pose.translate(arm == HumanoidArm.RIGHT ? -1f / 16f : 1f / 16f, 10f / 16f, 0);
            hands[arm == HumanoidArm.RIGHT ? 0 : 1] = new Matrix4f(pose.last().pose());
        }
        HANDS.put(state.id, hands);
    }

    public static Matrix4f handTransform(int entityId, boolean right) {
        var root = ROOTS.get(entityId);
        var hands = HANDS.get(entityId);
        return root == null || hands == null ? null : new Matrix4f(root).mul(hands[right ? 0 : 1]);
    }
}
