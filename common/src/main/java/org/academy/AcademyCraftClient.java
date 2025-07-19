package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.client.network.future.FutureManagerClient;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.future.FutureManager;
import org.academy.internal.client.app.Apps;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.academy.api.client.renderer.BlurRenderer;
import org.academy.internal.client.renderer.entity.EntityRenderers;
import org.academy.internal.client.renderer.item.ItemRenderers;

import java.io.File;

public final class AcademyCraftClient {
    public static final File CLIENT_CONFIG_FILE;
    public static final AcademyCraftConfig CLIENT_CONFIG;
    public static final NetworkSystem NETWORK_SYSTEM = new NetworkSystem();
    public static final FutureManager FUTURE_MANAGER = new FutureManager();
    public static final NetworkManagerClient CLIENT_NETWORK_MANAGER = new NetworkManagerClient(NETWORK_SYSTEM);
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
        AbilitySystemClient.init();
        ItemRenderers.init();
        Screens.register();
        DataTerminalHUD.init();
        HUDManager.init();
        Apps.register();
        EntityRenderers.init();
        ParticleRenderTypes.init();
        BlurRenderer.init();
    }
}