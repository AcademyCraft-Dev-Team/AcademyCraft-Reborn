package org.academy.internal.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.level.material.Fluids;
import org.joml.Quaternionf;

public final class ImagPhaseFluidParticle extends SingleQuadParticle {
    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private final float baseRotationX;
    private final float baseRotationY;
    private final float rotationSpeed;

    public ImagPhaseFluidParticle(
            ClientLevel level,
            SpriteSet sprites,
            double x,
            double y,
            double z,
            RandomSource random
    ) {
        super(level, x, y, z, sprites.get(random));
        setSize(0.02F, 0.02F);
        // Match the 1.21.1 SuspendedParticle size/lifetime distribution. The provider
        // applies the original additional 0.5-0.75 scale and pastel color variation.
        quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2.0F;
        quadSize *= random.nextFloat() * 0.6F + 0.2F;
        lifetime = (int) (16.0 / (random.nextFloat() * 0.8 + 0.2));
        hasPhysics = false;
        friction = 1.0F;
        gravity = 0.0F;
        xd = 0.0;
        yd = 0.0;
        zd = 0.0;
        baseRotationX = random.nextFloat() * Mth.TWO_PI;
        baseRotationY = random.nextFloat() * Mth.TWO_PI;
        rotationSpeed = (random.nextFloat() - 0.5F) * 0.025F;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }

        FluidState state = level.getFluidState(BlockPos.containing(x, y, z));
        Fluid fluid = state.getType();
        if (fluid != Fluids.IMAG_PHASE.get() && fluid != Fluids.FLOWING_IMAG_PHASE.get()) {
            remove();
        }
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

        // Three mutually perpendicular star planes form a spatial particle instead of a
        // surface decal or a single camera-facing quad.
        extractRotatedQuad(renderState, rotation, renderX, renderY, renderZ, partialTick);
        extractRotatedQuad(
                renderState,
                new Quaternionf(rotation).rotateY(HALF_PI),
                renderX,
                renderY,
                renderZ,
                partialTick
        );
        extractRotatedQuad(
                renderState,
                new Quaternionf(rotation).rotateX(HALF_PI),
                renderX,
                renderY,
                renderZ,
                partialTick
        );
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }
}
