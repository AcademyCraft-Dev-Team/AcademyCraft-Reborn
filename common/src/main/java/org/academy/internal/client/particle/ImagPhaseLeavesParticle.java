package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.NotNull;

public class ImagPhaseLeavesParticle extends TextureSheetParticle {
    protected final SpriteSet sprites;
    private boolean hasStartedFalling = false;
    private final int hangingTicks = 40;

    public ImagPhaseLeavesParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.sprites = spriteSet;
        this.setSize(0.1F, 0.1F);
        quadSize = 0.3f;
        int totalFallingStateDuration = 40;
        this.lifetime = this.hangingTicks + totalFallingStateDuration;

        pickSprite(spriteSet);
        this.setSprite(this.sprites.get(0, 6));
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (!this.hasStartedFalling) {
            if (this.age >= this.hangingTicks) {
                this.hasStartedFalling = true;
                this.gravity = 0.0015F + random.nextFloat() * 0.015F;
            } else {
                this.move(this.xd, this.yd, this.zd);
                this.xd *= 0.98D;
                this.yd *= 0.98D;
                this.zd *= 0.98D;
            }
        }

        if (this.hasStartedFalling) {
            this.yd -= this.gravity;
            this.move(this.xd, this.yd, this.zd);

            this.xd *= 0.98D;
            this.zd *= 0.98D;

            if (!this.removed && this.sprites != null) {
                    this.setSprite(this.sprites.get(age % 7, 6));
            }
        }

        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }
}