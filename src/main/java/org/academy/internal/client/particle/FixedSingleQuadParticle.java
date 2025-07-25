package org.academy.internal.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class FixedSingleQuadParticle extends Particle {
    protected float quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2.0F;
    protected float pitch;
    protected float yaw;
    protected float oPitch;
    protected float oYaw;

    protected FixedSingleQuadParticle(ClientLevel level, double x, double y, double z, float newYaw, float newPitch) {
        super(level, x, y, z);
        yaw = newYaw;
        pitch = newPitch;
        oYaw = newYaw;
        oPitch = newPitch;
    }

    protected FixedSingleQuadParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float newYaw, float newPitch) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        yaw = newYaw;
        pitch = newPitch;
        oYaw = newYaw;
        oPitch = newPitch;
    }

    @Override
    public void tick() {
        super.tick();
        oPitch = pitch;
        oYaw = yaw;
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera renderInfo, float partialTicks) {
        var cameraPos = renderInfo.getPosition();
        var particleX = (float) (Mth.lerp(partialTicks, xo, x) - cameraPos.x());
        var particleY = (float) (Mth.lerp(partialTicks, yo, y) - cameraPos.y());
        var particleZ = (float) (Mth.lerp(partialTicks, zo, z) - cameraPos.z());

        var particleOrientation = new Quaternionf();
        var currentYawRad = Mth.lerp(partialTicks, oYaw, yaw) * ((float) Math.PI / 180F);
        var currentPitchRad = Mth.lerp(partialTicks, oPitch, pitch) * ((float) Math.PI / 180F);
        var currentRollRad = Mth.lerp(partialTicks, oRoll, roll) * ((float) Math.PI / 180F);

        particleOrientation.rotateY(currentYawRad);
        particleOrientation.rotateX(currentPitchRad);
        if (roll != 0.0F) {
            particleOrientation.rotateZ(currentRollRad);
        }

        var quadVertices = new Vector3f[]{
                new Vector3f(-1.0F, -1.0F, 0.0F),
                new Vector3f(-1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, -1.0F, 0.0F)
        };
        var particleSize = getQuadSize(partialTicks);

        for (var i = 0; i < 4; ++i) {
            var vertex = quadVertices[i];
            vertex.rotate(particleOrientation);
            vertex.mul(particleSize);
            vertex.add(particleX, particleY, particleZ);
        }

        var u0 = getU0();
        var u1 = getU1();
        var v0 = getV0();
        var v1 = getV1();
        var light = getLightColor(partialTicks);

        buffer.addVertex(quadVertices[0].x(), quadVertices[0].y(), quadVertices[0].z()).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(quadVertices[1].x(), quadVertices[1].y(), quadVertices[1].z()).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(quadVertices[2].x(), quadVertices[2].y(), quadVertices[2].z()).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(quadVertices[3].x(), quadVertices[3].y(), quadVertices[3].z()).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);

        buffer.addVertex(quadVertices[0].x(), quadVertices[0].y(), quadVertices[0].z()).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(quadVertices[3].x(), quadVertices[3].y(), quadVertices[3].z()).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(quadVertices[2].x(), quadVertices[2].y(), quadVertices[2].z()).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(quadVertices[1].x(), quadVertices[1].y(), quadVertices[1].z()).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    public float getQuadSize(float newScaleFactor) {
        return quadSize;
    }

    @Override
    public @NotNull Particle scale(float newScale) {
        quadSize *= newScale;
        return super.scale(newScale);
    }

    protected abstract float getU0();

    protected abstract float getU1();

    protected abstract float getV0();

    protected abstract float getV1();
}