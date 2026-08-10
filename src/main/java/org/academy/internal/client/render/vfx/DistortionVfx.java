package org.academy.internal.client.render.vfx;

import net.minecraft.util.Mth;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.resources.R;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class DistortionVfx implements Vfx {
    public static final DistortionVfx INSTANCE = new DistortionVfx();
    private static final float INTENSITY = 0.8f;
    private static final Vector2f UV0 = new Vector2f(0.0f, 0.0f);
    private static final Vector2f UV1 = new Vector2f(1.0f, 0.0f);
    private static final Vector2f UV2 = new Vector2f(1.0f, 1.0f);
    private static final Vector2f UV3 = new Vector2f(0.0f, 1.0f);

    private final List<Effect> activeEffects = new ArrayList<>();

    public void trigger(float cx, float cy, float cz, float lifetime, float intensity,
                        float cr, float cg, float cb, float ca,
                        float er, float eg, float eb, float ea) {
        activeEffects.add(new Effect(cx, cy, cz, lifetime, intensity,
                cr, cg, cb, ca, er, eg, eb, ea));
    }

    @Override
    public void update(float dt, VfxFrameContext ctx) {
        if (dt <= 0.0f) return;
        activeEffects.removeIf(effect -> {
            effect.time += dt;
            return effect.time >= effect.lifetime;
        });
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (activeEffects.isEmpty()) return;
        var camera = ctx.camera();
        var list = List.copyOf(activeEffects);
        for (var effect : list) {
            renderRings(sink, effect, camera.pos().x, camera.pos().y, camera.pos().z);
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private void renderRings(VfxSink sink, Effect effect,
                             float camX, float camY, float camZ) {
        var halfLife = effect.lifetime * 0.5f;
        var tearProgress = effect.time <= halfLife
                ? effect.time / halfLife
                : 2.0f - effect.time / halfLife;
        var alpha = INTENSITY * effect.intensity
                * (1.0f - Math.abs(effect.time - halfLife) / halfLife);
        if (alpha <= 0.0f) return;

        var radius = 1.5f * Math.min(tearProgress, 1.0f);
        var rings = 8;
        var segments = 32;
        var relX = effect.centerX - camX;
        var relY = effect.centerY - camY;
        var relZ = effect.centerZ - camZ;

        for (var ring = 0; ring < rings; ring++) {
            var r1 = radius * ring / rings;
            var r2 = radius * (ring + 1) / rings;
            var t = (float) ring / rings;
            var ringR = effect.coreR + (effect.edgeR - effect.coreR) * t;
            var ringG = effect.coreG + (effect.edgeG - effect.coreG) * t;
            var ringB = effect.coreB + (effect.edgeB - effect.coreB) * t;
            var ringA = (effect.coreA + (effect.edgeA - effect.coreA) * t) * alpha;

            for (var seg = 0; seg < segments; seg++) {
                var a1 = (float) seg / segments * Mth.TWO_PI;
                var a2 = (float) (seg + 1) / segments * Mth.TWO_PI;
                var cos1 = Mth.cos(a1);
                var sin1 = Mth.sin(a1);
                var cos2 = Mth.cos(a2);
                var sin2 = Mth.sin(a2);
                sink.push(new DistortionData(
                        R.textures.white_wing,
                        new Vector3f(relX + cos1 * r1, relY, relZ + sin1 * r1),
                        new Vector3f(relX + cos2 * r1, relY, relZ + sin2 * r1),
                        new Vector3f(relX + cos2 * r2, relY, relZ + sin2 * r2),
                        new Vector3f(relX + cos1 * r2, relY, relZ + sin1 * r2),
                        UV0, UV1, UV2, UV3,
                        new Vector4f(ringR, ringG, ringB, ringA)
                ));
            }
        }
    }

    private static final class Effect {
        private final float centerX;
        private final float centerY;
        private final float centerZ;
        private final float lifetime;
        private final float intensity;
        private final float coreR;
        private final float coreG;
        private final float coreB;
        private final float coreA;
        private final float edgeR;
        private final float edgeG;
        private final float edgeB;
        private final float edgeA;
        private float time;

        private Effect(float centerX, float centerY, float centerZ, float lifetime,
                       float intensity, float coreR, float coreG, float coreB, float coreA,
                       float edgeR, float edgeG, float edgeB, float edgeA) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.lifetime = lifetime;
            this.intensity = intensity;
            this.coreR = coreR;
            this.coreG = coreG;
            this.coreB = coreB;
            this.coreA = coreA;
            this.edgeR = edgeR;
            this.edgeG = edgeG;
            this.edgeB = edgeB;
            this.edgeA = edgeA;
        }
    }
}
