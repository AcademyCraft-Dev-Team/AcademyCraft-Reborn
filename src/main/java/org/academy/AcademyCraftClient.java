package org.academy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialBlockModelRendererEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.hud.terminal.HUDController;
import org.academy.api.client.network.future.FutureManagerClient;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.network.NetworkManager;
import org.academy.internal.client.app.Apps;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.client.renderer.item.ItemRenderers;
import org.academy.internal.client.renderer.special.WindGenBaseSpecialRenderer;
import org.academy.internal.client.renderer.special.WindGenPillarSpecialRenderer;
import org.academy.internal.client.renderer.special.WindGenTopSpecialRenderer;
import org.academy.internal.client.renderer.special.WirelessNodeSpecialRenderer;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.material.ImagiphasePlasma;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@Mod(value = AcademyCraft.MOD_ID, dist = Dist.CLIENT)
public final class AcademyCraftClient {
    @Nullable
    public static Connection connection;
    public static final NetworkManager CLIENT_NETWORK_MANAGER = new NetworkManager();
    public static final FutureManagerClient CLIENT_FUTURE_MANAGER = new FutureManagerClient();

    public AcademyCraftClient(IEventBus modEventBus) {
        modEventBus.addListener(AcademyCraftClient::onRegisterClientExtensions);
        modEventBus.addListener(AcademyCraftClient::onRegisterRenderStateModifiers);
        modEventBus.addListener(AcademyCraftClient::onRegisterSpecialModelRenderer);
        modEventBus.addListener(AcademyCraftClient::onRegisterSpecialBlockModelRenderer);
    }

    public static void init() {
        CLIENT_NETWORK_MANAGER.clear();
        CLIENT_FUTURE_MANAGER.clear();
        CLIENT_NETWORK_MANAGER.registerPacketListener(CLIENT_FUTURE_MANAGER);
        ItemRenderers.init();
        Screens.register();
        HUDManager.init();
        Apps.register();
        ParticleRenderTypes.init();
        AbilitySystemClient.init();
        HUDController.INSTANCE.init();
        BlurEffect.init();
    }

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        resize(event.getWidth(), event.getHeight());
    }

    public static void resize(int width, int height) {
        PostEffect.resize(width, height);
        BloomEffect.resize(width, height);
        BlurEffect.resize(width, height);
        HUDManager.resize(width, height);
        HUDController.INSTANCE.resize(width, height);
    }

    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(PlayerRenderer.class,
                (abstractClientPlayer, playerRenderState) ->
                        playerRenderState.setRenderData(
                                StormWingEffectRenderer.CONTEXT_KEY,
                                abstractClientPlayer.getData(AttachmentTypes.ACTIVATED_STORM_WING)
                        )
        );
    }

    public static void onRegisterSpecialModelRenderer(RegisterSpecialModelRendererEvent event) {
        event.register(AcademyCraft.academy("wireless_node"), WirelessNodeSpecialRenderer.Unbaked.MAP_CODEC);
        event.register(AcademyCraft.academy("wind_gen_base"), WindGenBaseSpecialRenderer.Unbaked.MAP_CODEC);
        event.register(AcademyCraft.academy("wind_gen_pillar"), WindGenPillarSpecialRenderer.Unbaked.MAP_CODEC);
        event.register(AcademyCraft.academy("wind_gen_top"), WindGenTopSpecialRenderer.Unbaked.MAP_CODEC);
    }

    public static void onRegisterSpecialBlockModelRenderer(RegisterSpecialBlockModelRendererEvent event) {
        event.register(Blocks.WIND_GEN_PILLAR.get(), WindGenPillarSpecialRenderer.Unbaked.INSTANCE);
    }

    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public int getTintColor() {
                return 0XFF000000;
            }

            @Override
            public ResourceLocation getStillTexture() {
                return ImagiphasePlasma.TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ImagiphasePlasma.TEXTURE;
            }
        }, ImagiphasePlasma.FLUID_TYPE);
    }

    @SubscribeEvent
    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        Config.INSTANCE.save();
    }

    public static void sendPacket(Packet<?> packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }

    public static final class Config {
        public static final File CLIENT_CONFIG_FILE;
        public static final AcademyCraftConfig INSTANCE;

        static {
            CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
            AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
            INSTANCE = new AcademyCraftConfig(CLIENT_CONFIG_FILE);
        }

        private Config() {
        }
    }
}