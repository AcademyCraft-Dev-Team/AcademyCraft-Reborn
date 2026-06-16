package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ParticleEmitter {
    private final List<Particle> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private boolean active = true;
    private float emissionRate;
    private float emissionAccumulator;
    private int maxParticles;
    private float baseSize;
    private float endSize;
    private float sizeVariation;
    private float baseLifetime;
    private float lifetimeVariation;
    private float velocityBase;
    private float velocityVariation;
    private float gravity;
    private float damping;
    private float red, green, blue;
    private float colorVariation;
    private float startAlpha;
    private float endAlpha;
    private SpreadMode spreadMode = SpreadMode.SPHERE;
    private float spreadAngle;
    private Vector3f emissionDirection = new Vector3f(0, 1, 0);
    private Vector3f emitterPosition = new Vector3f();
    private boolean useRotation;
    private float rotationSpeedBase;
    private float rotationSpeedVariation;

    // Reusable temp vectors to reduce GC pressure in hot render loop
    private final Vector3f tmpCamPos = new Vector3f();
    private final Vector3f tmpCamRight = new Vector3f();
    private final Vector3f tmpCamUp = new Vector3f();
    private final Vector3f tmpPos = new Vector3f();
    private final Vector3f tmpHalfRight = new Vector3f();
    private final Vector3f tmpHalfUp = new Vector3f();
    private final Vector3f tmpV1 = new Vector3f();
    private final Vector3f tmpV2 = new Vector3f();
    private final Vector3f tmpV3 = new Vector3f();
    private final Vector3f tmpV4 = new Vector3f();

    public enum SpreadMode {
        SPHERE,
        HEMISPHERE_UP,
        CONE,
        HORIZONTAL_RING,
        VERTICAL_DISC
    }

    public ParticleEmitter() {
        maxParticles = 100;
        emissionRate = 20;
        baseSize = 0.15f;
        endSize = 0.15f;
        sizeVariation = 0.05f;
        baseLifetime = 1.0f;
        lifetimeVariation = 0.2f;
        velocityBase = 0.5f;
        velocityVariation = 0.3f;
        gravity = 0.0f;
        damping = 0.98f;
        red = 1.0f;
        green = 1.0f;
        blue = 1.0f;
        colorVariation = 0.1f;
        startAlpha = 1.0f;
        endAlpha = 0.0f;
        spreadAngle = (float) Math.PI;
        useRotation = false;
        rotationSpeedBase = 1.0f;
        rotationSpeedVariation = 0.5f;
    }

    public void setPosition(Vector3f pos) {
        emitterPosition.set(pos);
    }

    public void setPosition(float x, float y, float z) {
        emitterPosition.set(x, y, z);
    }

    public void setColor(float r, float g, float b) {
        red = r;
        green = g;
        blue = b;
    }

    public void setEmissionRate(float rate) {
        emissionRate = rate;
    }

    public void setMaxParticles(int max) {
        maxParticles = max;
    }

    public void setSize(float base, float variation) {
        baseSize = base;
        sizeVariation = variation;
    }

    public void setEndSize(float endSize) {
        this.endSize = endSize;
    }

    public void setLifetime(float base, float variation) {
        baseLifetime = base;
        lifetimeVariation = variation;
    }

    public void setVelocity(float base, float variation) {
        velocityBase = base;
        velocityVariation = variation;
    }

    public void setGravity(float gravity) {
        this.gravity = gravity;
    }

    public void setDamping(float damping) {
        this.damping = damping;
    }

    public void setAlpha(float start, float end) {
        startAlpha = start;
        endAlpha = end;
    }

    public void setRotation(boolean enabled, float speedBase, float speedVariation) {
        useRotation = enabled;
        rotationSpeedBase = speedBase;
        rotationSpeedVariation = speedVariation;
    }

    public void setSpreadMode(SpreadMode mode, float angle) {
        spreadMode = mode;
        spreadAngle = angle;
    }

    public void setEmissionDirection(Vector3f dir) {
        emissionDirection.set(dir).normalize();
    }

    public void setEmissionDirection(float x, float y, float z) {
        emissionDirection.set(x, y, z).normalize();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void burst(int count) {
        for (var i = 0; i < count && particles.size() < maxParticles; i++) {
            particles.add(createParticle(random.nextFloat()));
        }
    }

    public void update(float deltaTime) {
        if (!active && particles.isEmpty()) return;

        if (active) {
            emissionAccumulator += emissionRate * deltaTime;
            var toEmit = (int) emissionAccumulator;
            emissionAccumulator -= toEmit;

            for (var i = 0; i < toEmit && particles.size() < maxParticles; i++) {
                particles.add(createParticle(0));
            }
        }

        for (var i = particles.size() - 1; i >= 0; i--) {
            var p = particles.get(i);
            p.age += deltaTime;
            if (p.age >= p.lifetime) {
                particles.remove(i);
                continue;
            }
            p.velocity.y -= gravity * deltaTime;
            p.velocity.mul((float) Math.pow(damping, deltaTime * 60));
            p.position.add(p.velocity.x * deltaTime, p.velocity.y * deltaTime, p.velocity.z * deltaTime);
            if (useRotation) {
                p.rotation += p.angularVelocity * deltaTime;
            }
        }
    }

    public void render(PoseStack poseStack, VertexConsumer vc, Camera camera) {
        if (particles.isEmpty()) return;

        tmpCamPos.set(
                (float) camera.position().x,
                (float) camera.position().y,
                (float) camera.position().z
        );
        var camForward = new Vector3f(camera.forwardVector());
        var camWorldUp = new Vector3f(0, 1, 0);
        tmpCamRight.set(camForward).cross(camWorldUp).normalize();
        tmpCamUp.set(tmpCamRight).cross(camForward).normalize();

        for (var p : particles) {
            var lifeProgress = p.age / p.lifetime;
            var alpha = startAlpha + (endAlpha - startAlpha) * lifeProgress;
            if (alpha <= 0) continue;

            // Size over life
            var currentSize = p.startSize + (p.endSize - p.startSize) * lifeProgress;

            var r = p.r;
            var g = p.g;
            var b = p.b;

            tmpPos.set(
                    p.position.x - tmpCamPos.x,
                    p.position.y - tmpCamPos.y,
                    p.position.z - tmpCamPos.z
            );

            if (useRotation && Math.abs(p.rotation) > 0.001f) {
                var cos = (float) Math.cos(p.rotation);
                var sin = (float) Math.sin(p.rotation);

                // Rotated right vector scaled by half width
                var rx = (tmpCamRight.x * cos - tmpCamUp.x * sin) * currentSize * 0.5f;
                var ry = (tmpCamRight.y * cos - tmpCamUp.y * sin) * currentSize * 0.5f;
                var rz = (tmpCamRight.z * cos - tmpCamUp.z * sin) * currentSize * 0.5f;

                // Rotated up vector scaled by half height
                var ux = (tmpCamRight.x * sin + tmpCamUp.x * cos) * currentSize * 0.5f;
                var uy = (tmpCamRight.y * sin + tmpCamUp.y * cos) * currentSize * 0.5f;
                var uz = (tmpCamRight.z * sin + tmpCamUp.z * cos) * currentSize * 0.5f;

                tmpV1.set(tmpPos).sub(rx, ry, rz).sub(ux, uy, uz);
                tmpV2.set(tmpPos).add(rx, ry, rz).sub(ux, uy, uz);
                tmpV3.set(tmpPos).add(rx, ry, rz).add(ux, uy, uz);
                tmpV4.set(tmpPos).sub(rx, ry, rz).add(ux, uy, uz);
            } else {
                var hw = currentSize * 0.5f;
                tmpHalfRight.set(tmpCamRight).mul(hw);
                tmpHalfUp.set(tmpCamUp).mul(hw);

                tmpV1.set(tmpPos).sub(tmpHalfRight).sub(tmpHalfUp);
                tmpV2.set(tmpPos).add(tmpHalfRight).sub(tmpHalfUp);
                tmpV3.set(tmpPos).add(tmpHalfRight).add(tmpHalfUp);
                tmpV4.set(tmpPos).sub(tmpHalfRight).add(tmpHalfUp);
            }

            var poseEntry = poseStack.last();
            vc.addVertex(poseEntry, tmpV1.x, tmpV1.y, tmpV1.z).setUv(0, 1).setColor(r, g, b, alpha);
            vc.addVertex(poseEntry, tmpV2.x, tmpV2.y, tmpV2.z).setUv(1, 1).setColor(r, g, b, alpha);
            vc.addVertex(poseEntry, tmpV3.x, tmpV3.y, tmpV3.z).setUv(1, 0).setColor(r, g, b, alpha);
            vc.addVertex(poseEntry, tmpV4.x, tmpV4.y, tmpV4.z).setUv(0, 0).setColor(r, g, b, alpha);
        }
    }

    public void clear() {
        particles.clear();
    }

    public int getParticleCount() {
        return particles.size();
    }

    private Particle createParticle(float ageOffset) {
        var dir = generateDirection();
        var speed = velocityBase + (random.nextFloat() - 0.5f) * 2 * velocityVariation;
        var vel = new Vector3f(dir).mul(speed);

        var size = baseSize + (random.nextFloat() - 0.5f) * 2 * sizeVariation;
        var particleEndSize = endSize > 0 ? endSize + (random.nextFloat() - 0.5f) * 2 * sizeVariation : size;

        return new Particle(
                new Vector3f(emitterPosition),
                vel,
                clampColor(red + (random.nextFloat() - 0.5f) * 2 * colorVariation),
                clampColor(green + (random.nextFloat() - 0.5f) * 2 * colorVariation),
                clampColor(blue + (random.nextFloat() - 0.5f) * 2 * colorVariation),
                size,
                particleEndSize,
                baseLifetime + (random.nextFloat() - 0.5f) * 2 * lifetimeVariation,
                ageOffset * baseLifetime,
                useRotation ? (random.nextFloat() - 0.5f) * 2 * (float) Math.PI : 0,
                useRotation ? rotationSpeedBase + (random.nextFloat() - 0.5f) * 2 * rotationSpeedVariation : 0
        );
    }

    private float clampColor(float c) {
        return Math.clamp(c, 0, 1);
    }

    private Vector3f generateDirection() {
        return switch (spreadMode) {
            case SPHERE -> {
                var theta = random.nextFloat() * (float) Math.PI * 2;
                var phi = (float) Math.acos(2 * random.nextFloat() - 1);
                yield new Vector3f(
                        (float) (Math.sin(phi) * Math.cos(theta)),
                        (float) (Math.sin(phi) * Math.sin(theta)),
                        (float) Math.cos(phi)
                );
            }
            case HEMISPHERE_UP -> {
                var theta = random.nextFloat() * (float) Math.PI * 2;
                var phi = (float) Math.acos(random.nextFloat());
                yield new Vector3f(
                        (float) (Math.sin(phi) * Math.cos(theta)),
                        (float) Math.cos(phi),
                        (float) (Math.sin(phi) * Math.sin(theta))
                );
            }
            case CONE -> {
                var halfAngle = spreadAngle * 0.5f;
                var cosHalfAngle = (float) Math.cos(halfAngle);
                var phi = (float) Math.acos(1 - random.nextFloat() * (1 - cosHalfAngle));
                var theta = random.nextFloat() * (float) Math.PI * 2;
                var localDir = new Vector3f(
                        (float) (Math.sin(phi) * Math.cos(theta)),
                        (float) Math.cos(phi),
                        (float) (Math.sin(phi) * Math.sin(theta))
                );
                var axis = new Vector3f();
                var worldUp = new Vector3f(0, 1, 0);
                var dot = emissionDirection.dot(worldUp);
                if (Math.abs(dot) > 0.999f) {
                    axis.set(1, 0, 0);
                } else {
                    emissionDirection.cross(worldUp, axis);
                }
                var rotation = new org.joml.Quaternionf().rotateTo(worldUp, emissionDirection);
                localDir.rotate(rotation);
                yield localDir;
            }
            case HORIZONTAL_RING -> {
                var theta = random.nextFloat() * (float) Math.PI * 2;
                var phi = (float) (Math.PI / 2 + (random.nextFloat() - 0.5f) * spreadAngle);
                yield new Vector3f(
                        (float) (Math.sin(phi) * Math.cos(theta)),
                        0,
                        (float) (Math.sin(phi) * Math.sin(theta))
                ).normalize();
            }
            case VERTICAL_DISC -> {
                var theta = random.nextFloat() * (float) Math.PI * 2;
                var spread = (random.nextFloat() - 0.5f) * spreadAngle;
                yield new Vector3f(
                        (float) Math.cos(theta),
                        (float) Math.sin(spread),
                        (float) Math.sin(theta)
                ).normalize();
            }
        };
    }

    public static final class Particle {
        final Vector3f position;
        final Vector3f velocity;
        final float r, g, b;
        final float startSize;
        final float endSize;
        final float lifetime;
        final float angularVelocity;
        float age;
        float rotation;

        Particle(Vector3f position, Vector3f velocity, float r, float g, float b,
                float startSize, float endSize, float lifetime, float age,
                float rotation, float angularVelocity) {
            this.position = position;
            this.velocity = velocity;
            this.r = r;
            this.g = g;
            this.b = b;
            this.startSize = startSize;
            this.endSize = endSize;
            this.lifetime = lifetime;
            this.age = age;
            this.rotation = rotation;
            this.angularVelocity = angularVelocity;
        }
    }
}
