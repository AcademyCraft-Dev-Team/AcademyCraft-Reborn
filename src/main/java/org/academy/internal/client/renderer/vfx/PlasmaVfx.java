package org.academy.internal.client.renderer.vfx;

import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.util.Mth;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

/**
 * VFX-pipeline port of the original layered plasma layout.
 */
public final class PlasmaVfx implements Vfx {
    private static final int SOURCE_LAYER_COUNT = 28;
    private static final int UPPER_LAYER_COUNT = SOURCE_LAYER_COUNT / 2;
    private static final int MAX_INSTANCES = UPPER_LAYER_COUNT;
    private static final float ENVIRONMENT_RADIUS_SCALE = 4.0f;
    private static final float ENVIRONMENT_HEIGHT_SCALE = 3.0f;
    private static final float ENVIRONMENT_WIDTH_SCALE = 2.75f;
    private static final float ENVIRONMENT_DISPLACEMENT_SCALE = 3.0f;
    private static final float ENVIRONMENT_Y_OFFSET = 4.0f;
    private final Plasma plasma;
    private @Nullable ByteBuffer cloudInstances;
    private boolean expired;

    public PlasmaVfx(Plasma plasma) {
        this.plasma = plasma;
    }

    private static void sampleCore(VfxSink sink, double x, double y, double z,
                                   float progress, float time) {
        var pulse = 0.5f + 0.5f * Mth.sin(time * 0.34f);
        var innerRadius = (0.9f + progress * 2.7f) * (0.96f + pulse * 0.08f);
        var position = new Vector3f((float) x, (float) y, (float) z);
        sink.push(new PlasmaCoreData(position, innerRadius * 2.9f, 0.32f));
        sink.push(new PlasmaCoreData(position, innerRadius * 2.0f, 0.98f));
    }

    @Override
    public void sample(VfxFrameContext context, VfxSink sink) {
        if (plasma.isRemoved() || !plasma.isAlive()) {
            expired = true;
            return;
        }
        var progress = plasma.isLaunched()
                ? 1.0f
                : Mth.clamp(plasma.getGatherProgress(), 0.0f, 1.0f);
        if (progress <= 0.0f) return;
        var eased = (float) Mth.smoothstep(progress);
        var time = plasma.tickCount + context.partialTick();
        var pos = plasma.position();

        if (!plasma.isLaunched()) {
            sampleCloud(context, sink, eased, time);
        }
        sampleCore(sink, pos.x, pos.y, pos.z, eased, time);
    }

    private void sampleCloud(VfxFrameContext context, VfxSink sink, float progress, float time) {
        if (cloudInstances == null) {
            cloudInstances = BufferUtils.createByteBuffer(MAX_INSTANCES * 64);
        }
        cloudInstances.clear();
        var builder = Std140Builder.intoBuffer(cloudInstances);
        var camera = context.camera().pos();
        var center = plasma.position();
        var root = new Matrix4f().translate(
                (float) center.x - camera.x,
                (float) center.y - camera.y,
                (float) center.z - camera.z
        );

        // Keep the original upper fourteen layers. Re-normalizing against fourteen would stretch
        // them back across the lower half and recreate the cloud volume below the plasma core.
        for (var i = 0; i < UPPER_LAYER_COUNT; i++) {
            var normalized = i / (float) (SOURCE_LAYER_COUNT - 1);
            var centered = normalized * 2.0f - 1.0f;
            var timeValue = time * 0.035f;
            var displacementX = (float) ImprovedNoise.noise(i * 0.31, timeValue, 10.0)
                    * 1.7f * ENVIRONMENT_DISPLACEMENT_SCALE * progress;
            var displacementZ = (float) ImprovedNoise.noise(i * 0.31, timeValue, 20.0)
                    * 1.7f * ENVIRONMENT_DISPLACEMENT_SCALE * progress;
            var verticalJitter = (float) ImprovedNoise.noise(i * 0.67, timeValue, 30.0)
                    * 0.28f * ENVIRONMENT_HEIGHT_SCALE;
            var radiusNoise = (float) ImprovedNoise.noise(i * 0.47, timeValue, 40.0) * 0.22f;
            var widthNoise = (float) ImprovedNoise.noise(i * 0.73, timeValue, 50.0) * 0.28f;
            var profile = 0.32f + Mth.square(Math.abs(centered)) * 0.68f;
            var radius = (6.0f + 24.0f * profile) * (0.25f + progress * 0.75f);
            radius *= (1.0f + radiusNoise) * ENVIRONMENT_RADIUS_SCALE;
            var width = Math.max(
                    0.22f,
                    (0.75f + profile * 1.65f) * (1.0f + widthNoise)
                            * ENVIRONMENT_WIDTH_SCALE
            );
            var y = ENVIRONMENT_Y_OFFSET
                    + (13.5f - normalized * 30.0f) * ENVIRONMENT_HEIGHT_SCALE
                    + verticalJitter;
            var rotation = time * (0.012f + normalized * 0.006f) + i * 0.71f;
            var tiltX = (float) ImprovedNoise.noise(i * 0.41, timeValue, 60.0) * 0.09f;
            var tiltZ = (float) ImprovedNoise.noise(i * 0.41, timeValue, 70.0) * 0.09f;

            builder.putMat4f(new Matrix4f(root)
                    .translate(displacementX, y, displacementZ)
                    .rotate(new Quaternionf().rotateY(rotation).rotateX(tiltX).rotateZ(tiltZ))
                    .scale(radius, width, radius));
        }
        cloudInstances.flip();
        sink.push(new PlasmaCloudData(cloudInstances));
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }
}
