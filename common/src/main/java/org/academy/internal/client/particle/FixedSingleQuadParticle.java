package org.academy.internal.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class FixedSingleQuadParticle extends Particle {
    protected float quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
    protected float pitch;
    protected float yaw;
    protected float oPitch;
    protected float oYaw;

    protected FixedSingleQuadParticle(ClientLevel level, double x, double y, double z, float yaw, float pitch) {
        super(level, x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
        this.oYaw = yaw;
        this.oPitch = pitch;
    }

    protected FixedSingleQuadParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float yaw, float pitch) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.yaw = yaw;
        this.pitch = pitch;
        this.oYaw = yaw;
        this.oPitch = pitch;
    }

    @Override
    public void tick() {
        super.tick();
        this.oPitch = this.pitch;
        this.oYaw = this.yaw;
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera renderInfo, float partialTicks) {
        Vec3 cameraPos = renderInfo.getPosition();
        float particleX = (float)(Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x());
        float particleY = (float)(Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y());
        float particleZ = (float)(Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z());

        Quaternionf particleOrientation = new Quaternionf();
        float currentYawRad = Mth.lerp(partialTicks, this.oYaw, this.yaw) * ((float)Math.PI / 180F);
        float currentPitchRad = Mth.lerp(partialTicks, this.oPitch, this.pitch) * ((float)Math.PI / 180F);
        float currentRollRad = Mth.lerp(partialTicks, this.oRoll, this.roll) * ((float)Math.PI / 180F);

        particleOrientation.rotateY(currentYawRad);
        particleOrientation.rotateX(currentPitchRad);
        if (this.roll != 0.0F) {
            particleOrientation.rotateZ(currentRollRad);
        }

        Vector3f[] quadVertices = new Vector3f[]{
                new Vector3f(-1.0F, -1.0F, 0.0F),
                new Vector3f(-1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, -1.0F, 0.0F)
        };
        float particleSize = this.getQuadSize(partialTicks);

        for(int i = 0; i < 4; ++i) {
            Vector3f vertex = quadVertices[i];
            vertex.rotate(particleOrientation);
            vertex.mul(particleSize);
            vertex.add(particleX, particleY, particleZ);
        }

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        buffer.vertex(quadVertices[0].x(), quadVertices[0].y(), quadVertices[0].z()).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(quadVertices[1].x(), quadVertices[1].y(), quadVertices[1].z()).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(quadVertices[2].x(), quadVertices[2].y(), quadVertices[2].z()).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(quadVertices[3].x(), quadVertices[3].y(), quadVertices[3].z()).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();

        buffer.vertex(quadVertices[0].x(), quadVertices[0].y(), quadVertices[0].z()).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(quadVertices[3].x(), quadVertices[3].y(), quadVertices[3].z()).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(quadVertices[2].x(), quadVertices[2].y(), quadVertices[2].z()).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(quadVertices[1].x(), quadVertices[1].y(), quadVertices[1].z()).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    public float getQuadSize(float scaleFactor) {
        return this.quadSize;
    }

    public @NotNull Particle scale(float scale) {
        this.quadSize *= scale;
        return super.scale(scale);
    }

    protected abstract float getU0();

    protected abstract float getU1();

    protected abstract float getV0();

    protected abstract float getV1();
}