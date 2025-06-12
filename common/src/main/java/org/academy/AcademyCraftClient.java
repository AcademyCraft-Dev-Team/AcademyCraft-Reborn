package org.academy;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.BusBuilderImpl;
import net.neoforged.bus.api.IEventBus;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.future.FutureManagerClient;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.future.FutureManager;
import org.academy.internal.client.app.Apps;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.academy.internal.client.renderer.entity.EntityRenderers;
import org.academy.internal.client.renderer.item.ItemRenderers;

import java.io.File;

public final class AcademyCraftClient {
    public static final File CLIENT_CONFIG_FILE;
    public static final AcademyCraftConfig CLIENT_CONFIG;
    public static final IEventBus EVENT_BUS = new BusBuilderImpl().build();
    public static final NetworkSystem NETWORK_SYSTEM_INSTANCE = new NetworkSystem();
    public static final FutureManager FUTURE_MANAGER_INSTANCE = new FutureManager();
    public static final NetworkSystemClient NETWORK_SYSTEM_CLIENT_INSTANCE = new NetworkSystemClient(NETWORK_SYSTEM_INSTANCE);
    public static final FutureManagerClient FUTURE_MANAGER_CLIENT_INSTANCE = new FutureManagerClient(FUTURE_MANAGER_INSTANCE, NETWORK_SYSTEM_CLIENT_INSTANCE);

    static {
        CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
        CLIENT_CONFIG = new AcademyCraftConfig(CLIENT_CONFIG_FILE);
    }

    public static void init() {
        NETWORK_SYSTEM_CLIENT_INSTANCE.clear();
        FUTURE_MANAGER_CLIENT_INSTANCE.clear();
        AbilitySystemClient.init();
        ItemRenderers.init();
        Screens.register();
        DataTerminalHUD.init();
        HUDManager.init();
        Apps.register();
        EntityRenderers.init();
        ParticleRenderTypes.init();
    }
}