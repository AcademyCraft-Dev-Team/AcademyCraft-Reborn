package org.academy.internal.client.particle;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.core.particles.SpawnArcMediumParticlePacket;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.List;

@EventBusSubscriber(Dist.CLIENT)
public final class ParticleRenderTypes {
    private static final List<String> IMAG_PHASE_HEX_COLORS = List.of(
            "f59090", "b2e8f3", "d1aae1", "f3b6e0", "c4ee9c"
    );

    public static void init() {
        MisakaNetworkClient.NETWORK_MANAGER.registerPacketListener(ParticleRenderTypes.class);
    }

    @SubscribePacket
    public static void handleSpawnArcMediumParticle(SpawnArcMediumParticlePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            level.addParticle(ParticleTypes.ARC_MEDIUM.get(), packet.getX(), packet.getY(), packet.getZ(), packet.getYaw(), packet.getPitch(), 0.0D);
        }
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
       /* event.registerSpriteSet(ParticleTypes.IMAG_PHASE_FLUID.get(), spriteSet -> (particleType, level, x, y, z, xSpeed, ySpeed, zSpeed, random) -> {
            var particle = new ImagiphaseFluidParticle(level, spriteSet, x, y, z);
            var rd = MathUtil.RANDOM;
            particle.scale(rd.nextFloat(0.5f, 0.75f));

            var baseHexColor = IMAG_PHASE_HEX_COLORS.get(rd.nextInt(IMAG_PHASE_HEX_COLORS.size()));

            var baseR = Integer.parseInt(baseHexColor.substring(0, 2), 16);
            var baseG = Integer.parseInt(baseHexColor.substring(2, 4), 16);
            var baseB = Integer.parseInt(baseHexColor.substring(4, 6), 16);

            float val2 = baseR + rd.nextInt(-20, 20);
            var finalR = Mth.clamp(val2, (float) 0, (float) 255) / 255.0f;
            float val1 = baseG + rd.nextInt(-20, 20);
            var finalG = Mth.clamp(val1, (float) 0, (float) 255) / 255.0f;
            float val = baseB + rd.nextInt(-20, 20);
            var finalB = Mth.clamp(val, (float) 0, (float) 255) / 255.0f;

            particle.setColor(finalR, finalG, finalB);
            return particle;
        });*/
    }

    private ParticleRenderTypes() {
    }
}
