package org.academy.internal.client.particle;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ParticleRenderTypes {
    public static final Map<ParticleType<?>, ParticleEngine.SpriteParticleRegistration<?>> PARTICLE_PROVIDERS = new HashMap<>();
    public static final List<String> IMAG_PHASE_HEX_COLORS = List.of(
            "f59090", "b2e8f3", "d1aae1", "f3b6e0", "c4ee9c"
    );

    static {
        PARTICLE_PROVIDERS.put(ParticleTypes.IMAG_PHASE_FLUID, new ParticleEngine.SpriteParticleRegistration<>() {
            @Override
            public @NotNull ParticleProvider<ParticleOptions> create(@NotNull SpriteSet spriteSet) {
                return new ParticleProvider<>() {
                    @Override
                    public @NotNull Particle createParticle(@NotNull ParticleOptions type, @NotNull ClientLevel level,
                                                            double x, double y, double z,
                                                            double xSpeed, double ySpeed, double zSpeed) {
                        ImagPhaseFluidParticle particle = new ImagPhaseFluidParticle(level, spriteSet, x, y, z);
                        Random random = MathUtil.RANDOM;
                        particle.scale(random.nextFloat(0.5f, 0.75f));

                        String baseHexColor = IMAG_PHASE_HEX_COLORS.get(random.nextInt(IMAG_PHASE_HEX_COLORS.size()));

                        int baseR = Integer.parseInt(baseHexColor.substring(0, 2), 16);
                        int baseG = Integer.parseInt(baseHexColor.substring(2, 4), 16);
                        int baseB = Integer.parseInt(baseHexColor.substring(4, 6), 16);

                        int rOffset = random.nextInt(-20, 20);
                        int gOffset = random.nextInt(-20, 20);
                        int bOffset = random.nextInt(-20, 20);

                        float finalR = MathUtil.clamp(baseR + rOffset, 0, 255) / 255.0f;
                        float finalG = MathUtil.clamp(baseG + gOffset, 0, 255) / 255.0f;
                        float finalB = MathUtil.clamp(baseB + bOffset, 0, 255) / 255.0f;

                        particle.setColor(finalR, finalG, finalB);
                        return particle;
                    }
                };
            }
        });
        PARTICLE_PROVIDERS.put(ParticleTypes.IMAG_PHASE_LEAVES, new ParticleEngine.SpriteParticleRegistration<>() {
            @Override
            public @NotNull ParticleProvider<ParticleOptions> create(@NotNull SpriteSet spriteSet) {
                return new ParticleProvider<>() {
                    @Override
                    public @NotNull Particle createParticle(@NotNull ParticleOptions type, @NotNull ClientLevel level,
                                                            double x, double y, double z,
                                                            double xSpeed, double ySpeed, double zSpeed) {
                        ImagPhaseLeavesParticle particle = new ImagPhaseLeavesParticle(level, x, y, z, spriteSet);
                        Random random = MathUtil.RANDOM;
                        particle.scale(random.nextFloat(0.5f, 0.75f));

                        String baseHexColor = IMAG_PHASE_HEX_COLORS.get(random.nextInt(IMAG_PHASE_HEX_COLORS.size()));

                        int baseR = Integer.parseInt(baseHexColor.substring(0, 2), 16);
                        int baseG = Integer.parseInt(baseHexColor.substring(2, 4), 16);
                        int baseB = Integer.parseInt(baseHexColor.substring(4, 6), 16);

                        int rOffset = random.nextInt(-20, 20);
                        int gOffset = random.nextInt(-20, 20);
                        int bOffset = random.nextInt(-20, 20);

                        float finalR = MathUtil.clamp(baseR + rOffset, 0, 255) / 255.0f;
                        float finalG = MathUtil.clamp(baseG + gOffset, 0, 255) / 255.0f;
                        float finalB = MathUtil.clamp(baseB + bOffset, 0, 255) / 255.0f;

                        particle.setColor(finalR, finalG, finalB);
                        return particle;
                    }
                };
            }
        });
        PARTICLE_PROVIDERS.put(ParticleTypes.ARC_SMALL, new ParticleEngine.SpriteParticleRegistration<>() {
            @Override
            public @NotNull ParticleProvider<ParticleOptions> create(@NotNull SpriteSet spriteSet) {
                return new ParticleProvider<>() {
                    @Override
                    public @NotNull Particle createParticle(@NotNull ParticleOptions particleOptions, @NotNull ClientLevel clientLevel,
                                                            double x, double y, double z, double xSpeed,
                                                            double ySpeed, double zSpeed) {
                        return new SmallArcParticle(clientLevel, spriteSet, x, y, z);
                    }
                };
            }
        });
        PARTICLE_PROVIDERS.put(ParticleTypes.ARC_MEDIUM, new ParticleEngine.SpriteParticleRegistration<>() {
            @Override
            public @NotNull ParticleProvider<ParticleOptions> create(@NotNull SpriteSet spriteSet) {
                return new ParticleProvider<>() {
                    @Override
                    public @NotNull Particle createParticle(@NotNull ParticleOptions type, @NotNull ClientLevel level, double x, double y, double z, double yaw, double pitch, double zSpeed) {
                        return new MediumArcParticle(level, x, y, z, (float) yaw, (float) pitch, spriteSet);
                    }
                };
            }
        });
    }

    public static final ParticleRenderType IMAG_PHASE = new ParticleRenderType() {
        @SuppressWarnings("deprecation")
        @Override
        public void begin(@NotNull BufferBuilder builder, @NotNull TextureManager textureManager) {
            RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.getTranslucentTarget();
            if (renderTarget != null) {
                renderTarget.bindWrite(false);
            }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(@NotNull Tesselator tesselator) {
            tesselator.end();
        }
    };

    public static void init() {
        AcademyCraftClient.NETWORK_SYSTEM_CLIENT_INSTANCE.registerPacketListener(ParticleRenderTypes.class);
    }

    @SubscribePacket
    public static void handleSpawnArcMediumParticle(SpawnArcMediumParticlePacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.addParticle(ParticleTypes.ARC_MEDIUM, packet.getX(), packet.getY(), packet.getZ(), packet.getYaw(), packet.getPitch(), 0.0D);
        }
    }

    private ParticleRenderTypes() {
    }
}