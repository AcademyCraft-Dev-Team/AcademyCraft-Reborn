package org.academy.internal.client.animation;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Maps the supplied Gecko player skeleton's root pivot onto the vanilla player model.
 */
public final class GeckoPlayerModelAdapter {
    /**
     * Gecko's {@code all} bone is at the feet, which maps to vanilla model Y=24.
     */
    static final float ROOT_PIVOT_Y = 24.0f;

    private GeckoPlayerModelAdapter() {
    }

    public static void applyRootPivot(ModelPart root) {
        var compensation = rootPivotCompensation(root.xRot, root.yRot, root.zRot);
        root.x += compensation.x;
        root.y += compensation.y;
        root.z += compensation.z;
    }

    static Vector3f rootPivotCompensation(float xRot, float yRot, float zRot) {
        var rotatedPivot = new Vector3f(0.0f, ROOT_PIVOT_Y, 0.0f);
        new Quaternionf().rotationZYX(zRot, yRot, xRot).transform(rotatedPivot);
        return new Vector3f(
                -rotatedPivot.x,
                ROOT_PIVOT_Y - rotatedPivot.y,
                -rotatedPivot.z
        );
    }
}
