package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import org.joml.Vector3f;

public final class SpatialDistortionRenderer {
    private float time;
    private float lifetime;
    private float intensity;
    private float coreR, coreG, coreB, coreA;
    private float edgeR, edgeG, edgeB, edgeA;
    private float centerX, centerY, centerZ;
    private boolean active;
    private float tearProgress;

    public SpatialDistortionRenderer() {
        lifetime = 1.0f;
        intensity = 0.8f;
        coreR = 0.6f; coreG = 0.0f; coreB = 1.0f; coreA = 0.6f;
        edgeR = 1.0f; edgeG = 0.3f; edgeB = 0.8f; edgeA = 0.2f;
    }

    public void trigger(float centerX, float centerY, float centerZ, float lifetime) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.lifetime = lifetime;
        time = 0;
        active = true;
        tearProgress = 0;
    }

    public void setColors(float coreR, float coreG, float coreB, float coreA,
                          float edgeR, float edgeG, float edgeB, float edgeA) {
        this.coreR = coreR; this.coreG = coreG; this.coreB = coreB; this.coreA = coreA;
        this.edgeR = edgeR; this.edgeG = edgeG; this.edgeB = edgeB; this.edgeA = edgeA;
    }

    public boolean isActive() {
        return active;
    }

    public void update(float deltaTime) {
        if (active) {
            time += deltaTime;
            if (time >= lifetime) active = false;
            var halfLife = lifetime * 0.5f;
            if (time <= halfLife) {
                tearProgress = time / halfLife;
            } else {
                tearProgress = 2.0f - time / halfLife;
            }
        }
    }

    public void render(PoseStack poseStack, VertexConsumer vc, Camera camera) {
        if (!active) return;

        var camPos = camera.position();
        var relX = centerX - (float) camPos.x;
        var relY = centerY - (float) camPos.y;
        var relZ = centerZ - (float) camPos.z;

        var alpha = intensity * (1.0f - Math.abs(time - lifetime * 0.5f) / (lifetime * 0.5f));
        if (alpha <= 0) return;

        var radius = 1.5f * Math.min(tearProgress, 1.0f);
        var rings = 8;
        var segments = 32;
        var pose = poseStack.last();

        for (var ring = 0; ring < rings; ring++) {
            var r1 = radius * ring / rings;
            var r2 = radius * (ring + 1) / rings;
            var t = (float) ring / rings;
            var ringR = coreR + (edgeR - coreR) * t;
            var ringG = coreG + (edgeG - coreG) * t;
            var ringB = coreB + (edgeB - coreB) * t;
            var ringA = (coreA + (edgeA - coreA) * t) * alpha;

            for (var seg = 0; seg < segments; seg++) {
                var a1 = (float) seg / segments * (float) Math.PI * 2;
                var a2 = (float) (seg + 1) / segments * (float) Math.PI * 2;

                var cos1 = (float) Math.cos(a1);
                var sin1 = (float) Math.sin(a1);
                var cos2 = (float) Math.cos(a2);
                var sin2 = (float) Math.sin(a2);

                var x1 = relX + cos1 * r1;
                var z1 = relZ + sin1 * r1;
                var x2 = relX + cos2 * r1;
                var z2 = relZ + sin2 * r1;
                var x3 = relX + cos2 * r2;
                var z3 = relZ + sin2 * r2;
                var x4 = relX + cos1 * r2;
                var z4 = relZ + sin1 * r2;

                vc.addVertex(pose, x1, relY, z1).setColor(ringR, ringG, ringB, ringA);
                vc.addVertex(pose, x2, relY, z2).setColor(ringR, ringG, ringB, ringA);
                vc.addVertex(pose, x3, relY, z3).setColor(ringR, ringG, ringB, ringA);
                vc.addVertex(pose, x4, relY, z4).setColor(ringR, ringG, ringB, ringA);
            }
        }
    }
}
