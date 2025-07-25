package org.academy.internal.client.particle;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.academy.api.client.render.post.BloomEffect;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.client.renderer.RenderStateShard.*;
import static org.academy.api.client.util.RenderStateUtil.BLOOM_TARGET;

public class ImagiphaseFluidParticle extends SuspendedParticle {
    public static final RenderType RENDER_TYPE = RenderType.create(
            "imag_phase",
            DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState
                    .builder()
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(BLOOM_TARGET)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_PARTICLES, false, false))
                    .createCompositeState(false)
    );

    static {
        BloomEffect.addFixedBuffer(RENDER_TYPE);
    }

    public ImagiphaseFluidParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, sprites, x, y, z);
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera renderInfo, float partialTicks) {
        super.render(BloomEffect.BUFFER_SOURCE.getBuffer(RENDER_TYPE), renderInfo, partialTicks);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}