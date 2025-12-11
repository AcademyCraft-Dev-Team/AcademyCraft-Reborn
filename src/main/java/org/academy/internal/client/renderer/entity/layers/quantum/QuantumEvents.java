package org.academy.internal.client.renderer.entity.layers.quantum;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType; // [重要] 1.21 新增的枚举类
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.academy.AcademyCraft;
import org.jetbrains.annotations.NotNull;

//这里的东西我实在是不知道塞哪，而且也放不进现有架构，我临时塞这了
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public class QuantumEvents {

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.AddLayers event) {
        //为玩家模型注册量子化层
        for (PlayerModelType skinType : event.getSkins()) {
            AvatarRenderer<@NotNull AbstractClientPlayer> renderer = event.getPlayerRenderer(skinType);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer) {
                addQuantumLayerIfPossible(livingRenderer);
            }
        }

        //为所有生物实体添加量子化层
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            addQuantumLayerIfPossible(renderer);
        }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(AcademyCraft.MOD_ID);

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