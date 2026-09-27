package org.academy.internal.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.post.GlowEffect;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ImagPhaseFluidParticle extends SingleQuadParticle {
    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private final float baseRotationX;
    private final float baseRotationY;
    private final float rotationSpeed;
    private final boolean dripping;
    private final Vector3f vertexScratch = new Vector3f();

    public ImagPhaseFluidParticle(
            ClientLevel level,
            SpriteSet sprites,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            RandomSource random
    ) {
        super(level, x, y, z, sprites.get(random));
        setSize(0.02F, 0.02F);
        quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2.0F;
        quadSize *= random.nextFloat() * 0.6F + 0.2F;
        dripping = ySpeed < 0.0;
        if (dripping) {
            lifetime = 80 + random.nextInt(41);
            hasPhysics = true;
            friction = 0.98F;
            gravity = 0.35F;
            xd = xSpeed;
            yd = ySpeed;
            zd = zSpeed;
        } else {
            lifetime = (int) (16.0 / (random.nextFloat() * 0.8 + 0.2));
            hasPhysics = false;
            friction = 1.0F;
            gravity = 0.0F;
            xd = 0.0;
            yd = 0.0;
            zd = 0.0;
        }
        baseRotationX = random.nextFloat() * Mth.TWO_PI;
        baseRotationY = random.nextFloat() * Mth.TWO_PI;
        rotationSpeed = (random.nextFloat() - 0.5F) * 0.025F;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (dripping) if (onGround) remove();
    }

    @Override
    public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
        Vec3 cameraPos = camera.position();
        float renderX = (float) (Mth.lerp(partialTick, xo, x) - cameraPos.x());
        float renderY = (float) (Mth.lerp(partialTick, yo, y) - cameraPos.y());
        float renderZ = (float) (Mth.lerp(partialTick, zo, z) - cameraPos.z());
        float spin = (age + partialTick) * rotationSpeed;
        Quaternionf rotation = new Quaternionf()
                .rotateX(baseRotationX + spin * 0.7F)
                .rotateY(baseRotationY + spin);

        var postBuffer = GlowEffect.getBefore().getBuffer(Render.RenderTypes.IMAG_PHASE_PARTICLE_POST);
        extractPostQuad(postBuffer, rotation, renderX, renderY, renderZ, partialTick);
        extractPostQuad(
                postBuffer,
                new Quaternionf(rotation).rotateY(HALF_PI),
                renderX,
                renderY,
                renderZ,
                partialTick
        );
        extractPostQuad(
                postBuffer,
                new Quaternionf(rotation).rotateX(HALF_PI),
                renderX,
                renderY,
                renderZ,
                partialTick
        );
    }

    private void extractPostQuad(
            VertexConsumer output,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float partialTick
    ) {
        float scale = getQuadSize(partialTick);
        postVertex(output, rotation, x, y, z, 1.0F, -1.0F, scale, getU1(), getV1());
        postVertex(output, rotation, x, y, z, 1.0F, 1.0F, scale, getU1(), getV0());
        postVertex(output, rotation, x, y, z, -1.0F, 1.0F, scale, getU0(), getV0());
        postVertex(output, rotation, x, y, z, -1.0F, -1.0F, scale, getU0(), getV1());
    }

    private void postVertex(
            VertexConsumer output,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float cornerX,
            float cornerY,
            float scale,
            float u,
            float v
    ) {
        var vertex = vertexScratch.set(cornerX, cornerY, 0.0F)
                .rotate(rotation)
                .mul(scale)
                .add(x, y, z);
        output.addVertex(vertex.x(), vertex.y(), vertex.z())
                .setUv(u, v)
                .setColor(rCol, gCol, bCol, alpha);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return Layer.TRANSLUCENT_ITEMS;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }
}
