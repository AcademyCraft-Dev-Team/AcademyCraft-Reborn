package org.academy.internal.client.renderer.entity.layers.quantum;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.academy.AcademyCraft;

//这里的东西我实在是不知道塞哪，而且也放不进现有架构，我临时塞这了
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public class QuantumEvents {

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.AddLayers event) {
        for (var skinType : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skinType);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer) {
                addQuantumLayerIfPossible(livingRenderer);
            }
        }

        for (var type : event.getEntityTypes()) {
            var renderer = event.getRenderer(type);
            addQuantumLayerIfPossible(renderer);
        }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar(AcademyCraft.MOD_ID);

        registrar.playToClient(
                QuantumSyncPayload.TYPE,
                QuantumSyncPayload.CODEC,
                QuantumSyncPayload::handle
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addQuantumLayerIfPossible(EntityRenderer<?, ?> renderer) {
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            try {
                livingRenderer.addLayer(new QuantumInterferenceLayer(livingRenderer));
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Failed to add Quantum layer to renderer: {}", renderer.getClass().getSimpleName(), e);
            }
        }
    }
}