package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class VectorFieldRenderer {
    private final Vector3f[][] arrows;
    private final float size;
    private final Vector3f baseColor;
    private final float[][] phaseOffsets;
    private float time;

    public VectorFieldRenderer(int gridX, int gridZ, float size) {
        arrows = new Vector3f[gridX][gridZ];
        this.size = size;
        baseColor = new Vector3f(0, 0.8f, 1.0f);
        phaseOffsets = new float[gridX][gridZ];
        for (var x = 0; x < gridX; x++) {
            for (var z = 0; z < gridZ; z++) {
                arrows[x][z] = new Vector3f(0, 1, 0);
                phaseOffsets[x][z] = hash(x * 7919L + z * 6271L) * 2.0f;
            }
        }
    }

    public void setDirection(int x, int z, float dx, float dy, float dz) {
        arrows[x][z].set(dx, dy, dz).normalize();
    }

    public void setAllDirections(java.util.function.BiFunction<Integer, Integer, Vector3f> directionFn) {
        for (var x = 0; x < arrows.length; x++) {
            for (var z = 0; z < arrows[0].length; z++) {
                var dir = directionFn.apply(x, z);
                if (dir != null) arrows[x][z].set(dir).normalize();
            }
        }
    }

    public void setColor(float r, float g, float b) {
        baseColor.set(r, g, b);
    }

    public void update(float deltaTime) {
        time += deltaTime;
    }

    public void render(PoseStack poseStack, VertexConsumer vc, Camera camera,
                       float centerX, float centerY, float centerZ,
                       float gridSpacing) {
        var camPos = camera.position();
        var halfGridX = (arrows.length - 1) * gridSpacing * 0.5f;
        var halfGridZ = (arrows[0].length - 1) * gridSpacing * 0.5f;

        var waveSpeed = 2.5f;

        for (var x = 0; x < arrows.length; x++) {
            for (var z = 0; z < arrows[0].length; z++) {
                var dir = arrows[x][z];
                if (dir.lengthSquared() < 0.01f) continue;

                var worldX = centerX - halfGridX + x * gridSpacing;
                var worldZ = centerZ - halfGridZ + z * gridSpacing;

                var relX = worldX - (float) camPos.x;
                var relY = centerY - (float) camPos.y;
                var relZ = worldZ - (float) camPos.z;

                // Traveling wave: each arrow pulses at a different time
                var phase = phaseOffsets[x][z];
                var wave = (float) Math.sin(time * waveSpeed + phase * Math.PI);
                var alpha = 0.25f + 0.25f * wave;

                // Color by field strength
                var strength = dir.length();
                var cr = baseColor.x + (1.0f - baseColor.x) * (1.0f - strength);
                var cg = baseColor.y * strength;
                var cb = baseColor.z * strength;

                renderArrow(poseStack, vc, relX, relY, relZ,
                        dir.x, dir.y, dir.z, size, alpha, cr, cg, cb);
            }
        }
    }

    private void renderArrow(PoseStack poseStack, VertexConsumer vc,
                             float x, float y, float z,
                             float dx, float dy, float dz,
                             float arrowSize, float alpha,
                             float r, float g, float b) {
        var pos = new Vector3f(x, y, z);
        var dir = new Vector3f(dx, dy, dz).normalize();
        var tip = new Vector3f(pos).fma(arrowSize, dir);

        var headSize = arrowSize * 0.3f;
        var shaftThickness = arrowSize * 0.06f;

        // Compute perpendicular directions for the shaft cross-section
        var perp = new Vector3f();
        if (Math.abs(dx) < 0.9f) {
            perp = new Vector3f(1, 0, 0).cross(dir).normalize();
        } else {
            perp = new Vector3f(0, 1, 0).cross(dir).normalize();
        }
        if (perp.length() < 0.01f) perp = new Vector3f(0, 0, 1).cross(dir).normalize();
        var perp2 = new Vector3f().set(perp).cross(dir).normalize();

        var st = shaftThickness * 0.5f;
        var shaftBase = new Vector3f(pos).fma(-st, perp);
        var shaftRight = new Vector3f(pos).fma(st, perp);
        var shaftTop = new Vector3f(pos).fma(st, perp2);
        var shaftBottom = new Vector3f(pos).fma(-st, perp2);

        var shaftEnd = new Vector3f(tip).fma(-headSize, dir);
        var esBase = new Vector3f(shaftEnd).fma(-st, perp);
        var esRight = new Vector3f(shaftEnd).fma(st, perp);
        var esTop = new Vector3f(shaftEnd).fma(st, perp2);
        var esBottom = new Vector3f(shaftEnd).fma(-st, perp2);

        var shaftA = alpha * 0.7f;

        var headLeft = new Vector3f(shaftEnd).fma(-headSize * 0.5f, perp);
        var headRight = new Vector3f(shaftEnd).fma(headSize * 0.5f, perp);
        var headTopV = new Vector3f(shaftEnd).fma(headSize * 0.5f, perp2);
        var headBottomV = new Vector3f(shaftEnd).fma(-headSize * 0.5f, perp2);

        var pose = poseStack.last();
        var headA = alpha;

        // Shaft: 4 faces (each a quad)
        // Face 1: +perp side
        vc.addVertex(pose, shaftRight.x, shaftRight.y, shaftRight.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, shaftBottom.x, shaftBottom.y, shaftBottom.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esBottom.x, esBottom.y, esBottom.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esRight.x, esRight.y, esRight.z).setColor(r, g, b, shaftA);
        // Face 2: -perp side
        vc.addVertex(pose, shaftTop.x, shaftTop.y, shaftTop.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, shaftBase.x, shaftBase.y, shaftBase.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esBase.x, esBase.y, esBase.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esTop.x, esTop.y, esTop.z).setColor(r, g, b, shaftA);
        // Face 3: +perp2 side
        vc.addVertex(pose, shaftTop.x, shaftTop.y, shaftTop.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, shaftRight.x, shaftRight.y, shaftRight.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esRight.x, esRight.y, esRight.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esTop.x, esTop.y, esTop.z).setColor(r, g, b, shaftA);
        // Face 4: -perp2 side
        vc.addVertex(pose, shaftBase.x, shaftBase.y, shaftBase.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, shaftBottom.x, shaftBottom.y, shaftBottom.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esBottom.x, esBottom.y, esBottom.z).setColor(r, g, b, shaftA);
        vc.addVertex(pose, esBase.x, esBase.y, esBase.z).setColor(r, g, b, shaftA);

        // Arrowhead: 4 triangles
        vc.addVertex(pose, tip.x, tip.y, tip.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headLeft.x, headLeft.y, headLeft.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headTopV.x, headTopV.y, headTopV.z).setColor(r, g, b, headA);

        vc.addVertex(pose, tip.x, tip.y, tip.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headTopV.x, headTopV.y, headTopV.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headRight.x, headRight.y, headRight.z).setColor(r, g, b, headA);

        vc.addVertex(pose, tip.x, tip.y, tip.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headRight.x, headRight.y, headRight.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headBottomV.x, headBottomV.y, headBottomV.z).setColor(r, g, b, headA);

        vc.addVertex(pose, tip.x, tip.y, tip.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headBottomV.x, headBottomV.y, headBottomV.z).setColor(r, g, b, headA);
        vc.addVertex(pose, headLeft.x, headLeft.y, headLeft.z).setColor(r, g, b, headA);
    }

    private static float hash(long seed) {
        var x = (seed ^ 0x9E3779B9L) * 0x9E3779B9L;
        return (float) ((x ^ (x >>> 16)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }
}
