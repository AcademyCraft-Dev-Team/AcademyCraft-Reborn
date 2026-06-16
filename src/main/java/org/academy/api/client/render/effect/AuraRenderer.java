package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class AuraRenderer {
    private static final int DEFAULT_RINGS = 32;
    private static final int DEFAULT_SEGMENTS = 64;
    private static final int HALO_RINGS = 16;
    private static final int HALO_SEGMENTS = 32;

    private final List<AuraLayer> layers = new ArrayList<>();
    private float time;
    private boolean active = true;
    private boolean rimEnabled;
    private float rimIntensity = 0.4f;
    private float haloRadius = 1.15f;
    private boolean haloEnabled;
    private float haloAlpha = 0.15f;

    public AuraRenderer() {
    }

    public AuraLayer addLayer() {
        var layer = new AuraLayer();
        layers.add(layer);
        return layer;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setRimEnabled(boolean enabled, float intensity) {
        rimEnabled = enabled;
        rimIntensity = Math.clamp(intensity, 0, 1);
    }

    public void setHaloEnabled(boolean enabled, float radiusMult, float alpha) {
        haloEnabled = enabled;
        haloRadius = radiusMult;
        haloAlpha = alpha;
    }

    public void update(float deltaTime) {
        if (active) time += deltaTime;
    }

    public void renderHemisphere(PoseStack poseStack, VertexConsumer vc,
                                 float centerX, float centerY, float centerZ,
                                 float radius, int rings, int segments) {
        if (!active) return;
        var r = rings > 0 ? rings : DEFAULT_RINGS;
        var s = segments > 0 ? segments : DEFAULT_SEGMENTS;

        renderSpherePatch(poseStack, vc, centerX, centerY, centerZ, radius, r, s, 0, 0.5f);
    }

    public void renderSphere(PoseStack poseStack, VertexConsumer vc,
                             float centerX, float centerY, float centerZ,
                             float radius, int rings, int segments) {
        if (!active) return;
        var r = rings > 0 ? rings : DEFAULT_RINGS;
        var s = segments > 0 ? segments : DEFAULT_SEGMENTS;

        renderSpherePatch(poseStack, vc, centerX, centerY, centerZ, radius, r, s, 0, 1.0f);

        // Halo shell
        if (haloEnabled && layers.isEmpty()) {
            renderHaloShell(poseStack, vc, centerX, centerY, centerZ, radius * haloRadius, r, s);
        }
    }

    private void renderSpherePatch(PoseStack poseStack, VertexConsumer vc,
                                    float centerX, float centerY, float centerZ,
                                    float radius, int rings, int segments,
                                    float phiStart, float phiRange) {
        for (var lat = 0; lat < rings; lat++) {
            var phi1 = (float) (Math.PI * (phiStart + phiRange * lat / rings));
            var phi2 = (float) (Math.PI * (phiStart + phiRange * (lat + 1) / rings));

            for (var lon = 0; lon < segments; lon++) {
                var theta1 = (float) (2 * Math.PI * lon / segments);
                var theta2 = (float) (2 * Math.PI * (lon + 1) / segments);

                var v1 = spherePoint(phi1, theta1, radius).add(centerX, centerY, centerZ);
                var v2 = spherePoint(phi1, theta2, radius).add(centerX, centerY, centerZ);
                var v3 = spherePoint(phi2, theta2, radius).add(centerX, centerY, centerZ);
                var v4 = spherePoint(phi2, theta1, radius).add(centerX, centerY, centerZ);

                var n1 = spherePoint(phi1, theta1, 1);
                var n2 = spherePoint(phi1, theta2, 1);
                var n3 = spherePoint(phi2, theta2, 1);
                var n4 = spherePoint(phi2, theta1, 1);

                var pose = poseStack.last();

                if (layers.isEmpty()) {
                    // No layers: render default white/transparent sphere
                    var defaultAlpha = 0.3f * (1.0f + 0.2f * (float) Math.sin(time * 3.0f));
                    vc.addVertex(pose, v1.x, v1.y, v1.z).setColor(1, 1, 1, defaultAlpha);
                    vc.addVertex(pose, v2.x, v2.y, v2.z).setColor(1, 1, 1, defaultAlpha);
                    vc.addVertex(pose, v3.x, v3.y, v3.z).setColor(1, 1, 1, defaultAlpha);
                    vc.addVertex(pose, v4.x, v4.y, v4.z).setColor(1, 1, 1, defaultAlpha);
                } else {
                    for (var layer : layers) {
                        var color = layer.getColorAt(time, v1);
                        var rimBoost = 1.0f;
                        if (rimEnabled) {
                            // Fresnel: boost alpha at equator (high phi = grazing angle)
                            var avgPhi = (phi1 + phi2) * 0.5f;
                            var fresnel = (float) Math.abs(Math.cos(avgPhi));
                            fresnel = 1.0f - fresnel;
                            rimBoost = 1.0f + fresnel * rimIntensity;
                        }

                        var finalAlpha = Math.clamp(color.w * rimBoost, 0, 1);
                        vc.addVertex(pose, v1.x, v1.y, v1.z).setColor(color.x, color.y, color.z, finalAlpha);
                        vc.addVertex(pose, v2.x, v2.y, v2.z).setColor(color.x, color.y, color.z, finalAlpha)
                                .setNormal(pose, n2.x, n2.y, n2.z);
                        vc.addVertex(pose, v3.x, v3.y, v3.z).setColor(color.x, color.y, color.z, finalAlpha)
                                .setNormal(pose, n3.x, n3.y, n3.z);
                        vc.addVertex(pose, v4.x, v4.y, v4.z).setColor(color.x, color.y, color.z, finalAlpha)
                                .setNormal(pose, n4.x, n4.y, n4.z);
                    }
                }
            }
        }
    }

    private void renderHaloShell(PoseStack poseStack, VertexConsumer vc,
                                  float centerX, float centerY, float centerZ,
                                  float radius, int rings, int segments) {
        for (var lat = 0; lat < rings; lat++) {
            var phi1 = (float) (Math.PI * lat / rings);
            var phi2 = (float) (Math.PI * (lat + 1) / rings);

            for (var lon = 0; lon < segments; lon++) {
                var theta1 = (float) (2 * Math.PI * lon / segments);
                var theta2 = (float) (2 * Math.PI * (lon + 1) / segments);

                var v1 = spherePoint(phi1, theta1, radius).add(centerX, centerY, centerZ);
                var v2 = spherePoint(phi1, theta2, radius).add(centerX, centerY, centerZ);
                var v3 = spherePoint(phi2, theta2, radius).add(centerX, centerY, centerZ);
                var v4 = spherePoint(phi2, theta1, radius).add(centerX, centerY, centerZ);

                // Halo is brightest at the equator (Fresnel-like), fades at poles
                var avgPhi = (phi1 + phi2) * 0.5f;
                var equatorDist = (float) Math.abs(avgPhi - Math.PI * 0.5f) / (float) (Math.PI * 0.5f);
                var fresnel = 1.0f - equatorDist;
                fresnel = fresnel * fresnel;

                var alpha = haloAlpha * fresnel * (0.8f + 0.2f * (float) Math.sin(time * 2.5f));
                if (alpha < 0.005f) continue;

                var pose = poseStack.last();
                vc.addVertex(pose, v1.x, v1.y, v1.z).setColor(0.8f, 0.9f, 1f, alpha);
                vc.addVertex(pose, v2.x, v2.y, v2.z).setColor(0.8f, 0.9f, 1f, alpha);
                vc.addVertex(pose, v3.x, v3.y, v3.z).setColor(0.8f, 0.9f, 1f, alpha);
                vc.addVertex(pose, v4.x, v4.y, v4.z).setColor(0.8f, 0.9f, 1f, alpha);
            }
        }
    }

    public void renderOrbitingParticles(PoseStack poseStack, VertexConsumer vc,
                                         float centerX, float centerY, float centerZ,
                                         float radius, int count) {
        if (!active) return;
        var pose = poseStack.last();

        for (var i = 0; i < count; i++) {
            var orbitSpeed = 0.8f + (i * 0.15f);
            var orbitAngle = time * orbitSpeed + (float) (i * Math.PI * 2 / count);
            var pitchAngle = (float) (Math.PI * 0.5f + Math.sin(time * 1.3f + i) * 0.4f);

            var px = centerX + radius * (float) Math.cos(pitchAngle) * (float) Math.cos(orbitAngle);
            var py = centerY + radius * (float) Math.sin(pitchAngle);
            var pz = centerZ + radius * (float) Math.cos(pitchAngle) * (float) Math.sin(orbitAngle);

            var pulse = 0.5f + 0.5f * (float) Math.sin(time * 5.0f + i * 1.7f);
            var alpha = 0.15f + pulse * 0.4f;
            var size = 0.03f + pulse * 0.02f;

            // Small billboarded cross
            vc.addVertex(pose, px - size, py, pz).setColor(1f, 1f, 1f, alpha);
            vc.addVertex(pose, px + size, py, pz).setColor(1f, 1f, 1f, alpha);
            vc.addVertex(pose, px, py + size, pz).setColor(1f, 1f, 1f, alpha);
            vc.addVertex(pose, px, py - size, pz).setColor(1f, 1f, 1f, alpha);
        }
    }

    private static Vector3f spherePoint(float phi, float theta, float radius) {
        return new Vector3f(
                (float) (radius * Math.sin(phi) * Math.cos(theta)),
                (float) (radius * Math.cos(phi)),
                (float) (radius * Math.sin(phi) * Math.sin(theta))
        );
    }

    public static final class AuraLayer {
        float innerR = 1, innerG = 1, innerB = 1, innerA = 0.3f;
        float outerR = 1, outerG = 1, outerB = 1, outerA = 0.1f;
        float pulseFrequency = 2.0f;
        float pulseAmplitude = 0.2f;
        float noiseScale = 0.5f;
        float noiseSpeed = 0.3f;

        public AuraLayer setInnerColor(float r, float g, float b, float a) {
            innerR = r; innerG = g; innerB = b; innerA = a;
            return this;
        }

        public AuraLayer setOuterColor(float r, float g, float b, float a) {
            outerR = r; outerG = g; outerB = b; outerA = a;
            return this;
        }

        public AuraLayer setPulse(float frequency, float amplitude) {
            pulseFrequency = frequency;
            pulseAmplitude = amplitude;
            return this;
        }

        public AuraLayer setNoise(float scale, float speed) {
            noiseScale = scale;
            noiseSpeed = speed;
            return this;
        }

        Vector4f getColorAt(float time, Vector3f worldPos) {
            var noise = simplexNoise(
                    worldPos.x * noiseScale + time * noiseSpeed,
                    worldPos.y * noiseScale,
                    worldPos.z * noiseScale
            );
            var pulse = 1.0f + (float) Math.sin(time * pulseFrequency * Math.PI * 2) * pulseAmplitude;
            var n = (noise + 1.0f) * 0.5f;
            var a = (innerA + (outerA - innerA) * n) * pulse;
            var r = innerR + (outerR - innerR) * n;
            var g = innerG + (outerG - innerG) * n;
            var b = innerB + (outerB - outerB) * n;
            return new Vector4f(r, g, b, Math.clamp(a, 0, 1));
        }

        private static float simplexNoise(float x, float y, float z) {
            var n = 0f;
            var amp = 1f;
            var freq = 1f;
            for (var i = 0; i < 3; i++) {
                n += amp * hashNoise(x * freq, y * freq, z * freq);
                amp *= 0.5f;
                freq *= 2.0f;
            }
            return n;
        }

        private static float hashNoise(float x, float y, float z) {
            var ix = (int) Math.floor(x);
            var iy = (int) Math.floor(y);
            var iz = (int) Math.floor(z);
            var fx = x - ix;
            var fy = y - iy;
            var fz = z - iz;
            fx = fx * fx * (3f - 2f * fx);
            fy = fy * fy * (3f - 2f * fy);
            fz = fz * fz * (3f - 2f * fz);
            return lerp(fz,
                    lerp(fy, lerp(fx, grad(ix, iy, iz, fx, fy, fz), grad(ix + 1, iy, iz, fx - 1, fy, fz)),
                         lerp(fx, grad(ix, iy + 1, iz, fx, fy - 1, fz), grad(ix + 1, iy + 1, iz, fx - 1, fy - 1, fz))),
                    lerp(fy, lerp(fx, grad(ix, iy, iz + 1, fx, fy, fz - 1), grad(ix + 1, iy, iz + 1, fx - 1, fy, fz - 1)),
                         lerp(fx, grad(ix, iy + 1, iz + 1, fx, fy - 1, fz - 1), grad(ix + 1, iy + 1, iz + 1, fx - 1, fy - 1, fz - 1))));
        }

        private static float grad(int ix, int iy, int iz, float dx, float dy, float dz) {
            var h = ix * 374761393 + iy * 668265263 + iz * 1274126177;
            h = (h ^ (h >> 13)) * 1274126177;
            h = h ^ (h >> 16);
            var gx = ((h & 1) == 0 ? 1f : -1f) * dx;
            var gy = ((h & 2) == 0 ? 1f : -1f) * dy;
            var gz = ((h & 4) == 0 ? 1f : -1f) * dz;
            return gx + gy + gz;
        }

        private static float lerp(float t, float a, float b) {
            return a + t * (b - a);
        }
    }
}
