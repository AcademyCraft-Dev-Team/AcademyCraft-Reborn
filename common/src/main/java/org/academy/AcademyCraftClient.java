package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.AcademyCraftClientConfig;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.hud.HUDManager;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.hud.DataTerminalHUD;
import org.academy.internal.client.renderer.item.ItemRenderers;

import java.io.File;

public final class AcademyCraftClient {
    public static final File CLIENT_CONFIG_FILE;
    public static final AcademyCraftClientConfig CLIENT_CONFIG;

    static {
        CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
        CLIENT_CONFIG = new AcademyCraftClientConfig();
    }

    public static void init() {
        AbilitySystemClient.init();
        ItemRenderers.init();
        Screens.register();
        NetworkSystemClient.init();
        DataTerminalHUD.init();
        HUDManager.init();
    }
}