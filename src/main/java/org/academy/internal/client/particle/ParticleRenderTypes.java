package org.academy.internal.client.particle;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.annotation.SubscribePacket;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;

import java.util.List;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class ParticleRenderTypes {
    private static final List<String> IMAG_PHASE_HEX_COLORS = List.of(
            "f59090", "b2e8f3", "d1aae1", "f3b6e0", "c4ee9c"
    );

    public static void init() {
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.registerPacketListener(ParticleRenderTypes.class);
    }
/*
    public static final ParticleRenderType PARTICLE_IMAG_PHASE = new ParticleRenderType() {
        @SuppressWarnings("deprecation")
        @Override
        public BufferBuilder begin(Tesselator tesselator, @NotNull TextureManager textureManager) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.depthFunc(519);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "PARTICLE_IMAG_PHASE";
        }

        @Override
        public boolean isTranslucent() {
            return false;
        }
    };*/

    @SubscribePacket
    public static void handleSpawnArcMediumParticle(SpawnArcMediumParticlePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            level.addParticle(ParticleTypes.ARC_MEDIUM.get(), packet.getX(), packet.getY(), packet.getZ(), packet.getYaw(), packet.getPitch(), 0.0D);
        }
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypes.IMAG_PHASE_FLUID.get(), spriteSet -> (type, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
            var particle = new ImagiphaseFluidParticle(level, spriteSet, x, y, z);
            var random = MathUtil.RANDOM;
            particle.scale(random.nextFloat(0.5f, 0.75f));

            var baseHexColor = IMAG_PHASE_HEX_COLORS.get(random.nextInt(IMAG_PHASE_HEX_COLORS.size()));

            var baseR = Integer.parseInt(baseHexColor.substring(0, 2), 16);
            var baseG = Integer.parseInt(baseHexColor.substring(2, 4), 16);
            var baseB = Integer.parseInt(baseHexColor.substring(4, 6), 16);

            var finalR = MathUtil.clamp(baseR + random.nextInt(-20, 20), 0, 255) / 255.0f;
            var finalG = MathUtil.clamp(baseG + random.nextInt(-20, 20), 0, 255) / 255.0f;
            var finalB = MathUtil.clamp(baseB + random.nextInt(-20, 20), 0, 255) / 255.0f;

            particle.setColor(finalR, finalG, finalB);
            return particle;
        });

        event.registerSpriteSet(ParticleTypes.IMAG_PHASE_LEAVES.get(), spriteSet -> (type, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
            var particle = new ImagiphaseLeavesParticle(level, x, y, z, spriteSet);
            var random = MathUtil.RANDOM;
            particle.scale(random.nextFloat(0.5f, 0.75f));

            var baseHexColor = IMAG_PHASE_HEX_COLORS.get(random.nextInt(IMAG_PHASE_HEX_COLORS.size()));

            var baseR = Integer.parseInt(baseHexColor.substring(0, 2), 16);
            var baseG = Integer.parseInt(baseHexColor.substring(2, 4), 16);
            var baseB = Integer.parseInt(baseHexColor.substring(4, 6), 16);

            var finalR = MathUtil.clamp(baseR + random.nextInt(-20, 20), 0, 255) / 255.0f;
            var finalG = MathUtil.clamp(baseG + random.nextInt(-20, 20), 0, 255) / 255.0f;
            var finalB = MathUtil.clamp(baseB + random.nextInt(-20, 20), 0, 255) / 255.0f;

            particle.setColor(finalR, finalG, finalB);
            return particle;
        });

        event.registerSpriteSet(ParticleTypes.ARC_SMALL.get(), spriteSet -> (type, level, x, y, z, xSpeed, ySpeed, zSpeed) ->
                new SmallArcParticle(level, spriteSet, x, y, z)
        );

        event.registerSpriteSet(ParticleTypes.ARC_MEDIUM.get(), spriteSet -> (type, level, x, y, z, yaw, pitch, zSpeed) ->
                new MediumArcParticle(level, x, y, z, (float) yaw, (float) pitch, spriteSet)
        );
    }

    private ParticleRenderTypes() {
    }
}
