package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;

public class ImagiphaseLeavesParticle extends SingleQuadParticle {
    protected final SpriteSet sprites;
    private boolean hasStartedFalling = false;
    private final int hangingTicks = 40;

    public ImagiphaseLeavesParticle(ClientLevel level, double x, double y, double z, SpriteSet newSpriteSet) {
        super(level, x, y, z, newSpriteSet.first());
        sprites = newSpriteSet;
        setSize(0.1F, 0.1F);
        quadSize = 0.3f;
        var totalFallingStateDuration = 40;
        lifetime = hangingTicks + totalFallingStateDuration;

        setSprite(sprites.get(0, 6));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;

        if (!hasStartedFalling) {
            if (age >= hangingTicks) {
                hasStartedFalling = true;
                gravity = 0.0015F + random.nextFloat() * 0.015F;
            } else {
                move(xd, yd, zd);
                xd *= 0.98D;
                yd *= 0.98D;
                zd *= 0.98D;
            }
        }

        if (hasStartedFalling) {
            yd -= gravity;
            move(xd, yd, zd);

            xd *= 0.98D;
            zd *= 0.98D;

            if (!removed) {
                setSprite(sprites.get(age % 7, 6));
            }
        }

        if (age++ >= lifetime) {
            remove();
        }
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }
}