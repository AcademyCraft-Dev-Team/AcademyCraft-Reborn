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

    protected void setSprite(TextureAtlasSprite newSprite) {
        sprite = newSprite;
    }

    protected float getU0() {
        return sprite.getU0();
    }

    protected float getU1() {
        return sprite.getU1();
    }

    protected float getV0() {
        return sprite.getV0();
    }

    protected float getV1() {
        return sprite.getV1();
    }

    public void pickSprite(SpriteSet newSprite) {
        setSprite(newSprite.get(random));
    }

    public void setSpriteFromAge(SpriteSet newSprite) {
        if (!removed) {
            setSprite(newSprite.get(age, lifetime));
        }
    }
}