package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public abstract class FixedTextureSheetParticle extends FixedSingleQuadParticle{
    protected TextureAtlasSprite sprite;

    protected FixedTextureSheetParticle(ClientLevel level, double x, double y, double z, float yaw, float pitch) {
        super(level, x, y, z, yaw, pitch);
    }

    protected FixedTextureSheetParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float yaw, float pitch) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, yaw, pitch);
    }

    protected void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    protected float getU0() {
        return this.sprite.getU0();
    }

    protected float getU1() {
        return this.sprite.getU1();
    }

    protected float getV0() {
        return this.sprite.getV0();
    }

    protected float getV1() {
        return this.sprite.getV1();
    }

    public void pickSprite(SpriteSet sprite) {
        this.setSprite(sprite.get(this.random));
    }

    public void setSpriteFromAge(SpriteSet sprite) {
        if (!this.removed) {
            this.setSprite(sprite.get(this.age, this.lifetime));
        }
    }
}