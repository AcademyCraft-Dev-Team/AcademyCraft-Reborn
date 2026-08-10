package org.academy.internal.client.renderer.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.Knot;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorVisualStyle;
import org.joml.Vector3f;

import java.util.List;

public final class VectorRedirectVfx implements Vfx {
    private static final long LIFETIME_NANOS = 350_000_000L;
    private static final AttributeCurve TAPER = new AttributeCurve(List.of(
            new Knot(0.0f, 1.0f),
            new Knot(0.82f, 1.0f),
            new Knot(1.0f, 0.05f)
    ));
    private final Vec3 start;
    private final Vec3 end;
    private final float radius;
    private final VectorRedirectKind kind;
    private final VectorVisualStyle style;
    private final ArcPath arcPath;
    private final ArcTube arcTube = new ArcTube();
    private final long createdAt = System.nanoTime();

    public VectorRedirectVfx(
            Vec3 start,
            Vec3 direction,
            float length,
            float radius,
            VectorRedirectKind kind,
            VectorVisualStyle style,
            long seed
    ) {
        this.start = start;
        this.end = start.add(direction.scale(length));
        this.radius = Float.isFinite(radius) ? Math.clamp(radius, 0.02f, 4.0f) : 0.25f;
        this.kind = kind;
        this.style = style;
        var jaggedness = kind == VectorRedirectKind.REFRACTION ? 0.18f : 0.1f;
        this.arcPath = new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                List.of(
                        new JaggedModifier(jaggedness, 4, seed),
                        new TaperModifier(TAPER, this.radius)
                ),
                2.0f,
                List.of()
        );
    }

    @Override
    public void sample(VfxFrameContext context, VfxSink sink) {
        var age = Math.clamp((System.nanoTime() - createdAt) / (double) LIFETIME_NANOS, 0.0, 1.0);
        if (style == VectorVisualStyle.ARC) {
            arcTube.build(arcPath, (float) (age * 8.0));
            if (arcTube.mesh().isEmpty()) return;
            sink.push(new LightningCoreData(arcTube));
            sink.push(new LightningRenderData(arcTube));
            return;
        }

        var direction = end.subtract(start);
        var horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        var yRot = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        var xRot = (float) Math.toDegrees(Math.atan2(-direction.y, horizontalLength));
        var progress = (float) (1.0 - age);
        var kindScale = kind == VectorRedirectKind.REFRACTION ? 0.65f : 1.0f;
        var widthScale = Math.max(0.15f, radius * 2.0f) * kindScale;
        var position = new Vector3f((float) start.x, (float) start.y, (float) start.z);
        sink.push(new BeamCoreData(
                position, yRot, xRot, (float) direction.length(), progress, false, widthScale, kindScale
        ));
        sink.push(new BeamGlowData(
                position, yRot, xRot, (float) direction.length(), progress, false, widthScale, kindScale
        ));
    }

    @Override
    public boolean isAlive() {
        return System.nanoTime() - createdAt < LIFETIME_NANOS;
    }
}
