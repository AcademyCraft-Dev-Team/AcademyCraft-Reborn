package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.resources.R;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

public final class LightShieldVfx implements Vfx {
    private static final float THIRD_PERSON_HALF_SIZE = 1.55f;
    private static final Vector2f UV0 = new Vector2f(0.0f, 0.0f);
    private static final Vector2f UV1 = new Vector2f(1.0f, 0.0f);
    private static final Vector2f UV2 = new Vector2f(1.0f, 1.0f);
    private static final Vector2f UV3 = new Vector2f(0.0f, 1.0f);
    private static final Vector4f COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 0.85f);

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var roots = WingAvatarRegistry.entries();
        if (roots.isEmpty()) return;

        for (Map.Entry<Integer, Matrix4f> entry : roots.entrySet()) {
            var entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player)) continue;
            if (!player.getData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get())) continue;

            var root = entry.getValue();
            var camera = ctx.camera().pos();
            var age = player.tickCount + ctx.partialTick();
            var matrix = new Matrix4f(root);
            matrix.translate(camera.x, camera.y, camera.z);
            matrix.translate(0.0f, -0.25f, -1.4f);
            matrix.rotateX(-player.getXRot() * Mth.DEG_TO_RAD);
            matrix.rotateZ(age * 12.0f * Mth.DEG_TO_RAD);

            var half = THIRD_PERSON_HALF_SIZE;
            var p0 = new Vector3f(-half, -half, 0.0f).mulPosition(matrix);
            var p1 = new Vector3f(half, -half, 0.0f).mulPosition(matrix);
            var p2 = new Vector3f(half, half, 0.0f).mulPosition(matrix);
            var p3 = new Vector3f(-half, half, 0.0f).mulPosition(matrix);
            sink.push(new LightShieldQuadData(
                    R.textures.light_shield_effect,
                    p0, p1, p2, p3,
                    UV0, UV1, UV2, UV3,
                    COLOR
            ));
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }
}
