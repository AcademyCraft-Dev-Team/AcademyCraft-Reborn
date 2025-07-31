package org.academy;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.network.future.FutureManagerClient;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.util.StencilUtil;
import org.academy.api.common.network.NetworkManager;
import org.academy.api.common.network.future.FutureManager;
import org.academy.internal.client.app.Apps;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.academy.internal.client.renderer.entity.EntityRenderers;
import org.academy.internal.client.renderer.item.ItemRenderers;
import org.academy.internal.common.world.level.material.ImagiphasePlasma;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class AcademyCraftClient {
    public static Connection connection;
    public static final File CLIENT_CONFIG_FILE;
    public static final AcademyCraftConfig CLIENT_CONFIG;
    public static final FutureManager FUTURE_MANAGER = new FutureManager();
    public static final NetworkManager CLIENT_NETWORK_MANAGER = new NetworkManager();
    public static final FutureManagerClient CLIENT_FUTURE_MANAGER = new FutureManagerClient(FUTURE_MANAGER);

    static {
        CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
        CLIENT_CONFIG = new AcademyCraftConfig(CLIENT_CONFIG_FILE);
    }

    public static void init() {
        CLIENT_NETWORK_MANAGER.clear();
        CLIENT_FUTURE_MANAGER.clear();
        CLIENT_NETWORK_MANAGER.registerPacketListener(CLIENT_FUTURE_MANAGER);
        ItemRenderers.init();
        Screens.register();
        DataTerminalHUD.init();
        HUDManager.init();
        Apps.register();
        EntityRenderers.init();
        ParticleRenderTypes.init();
        BloomEffect.init();
        BlurEffect.init();
        StencilUtil.init();

        AbilitySystemClient.init();
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public int getTintColor() {
                return 0XFF000000;
            }

            @Override
            public @NotNull ResourceLocation getStillTexture() {
                return ImagiphasePlasma.TEXTURE;
            }

            @Override
            public @NotNull ResourceLocation getFlowingTexture() {
                return ImagiphasePlasma.TEXTURE;
            }
        }, ImagiphasePlasma.FLUID_TYPE);
    }

    @SubscribeEvent
    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        CLIENT_CONFIG.save();
    }

    public static void sendPacket(Packet<?> packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }
}