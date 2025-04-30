package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.AcademyCraftClientConfig;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.internal.client.gui.screens.Screens;
import org.academy.internal.client.renderer.item.ItemRenderers;

import java.io.File;

public final class AcademyCraftClient {
    public static final File CLIENT_CONFIG_FILE;
    public static final AcademyCraftClientConfig<SkillClientConfig> CLIENT_CONFIG;

    static {
        CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
        CLIENT_CONFIG = new AcademyCraftClientConfig<>();
    }

    public static void init() {
        AbilitySystemClient.init();
        ItemRenderers.init();
        NetworkSystemClient.init();
        Screens.register();
    }
}