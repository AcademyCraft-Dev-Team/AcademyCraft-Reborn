package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.config.AcademyCraftClientConfig;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.common.wireless.WirelessManager;
import org.academy.internal.client.gui.AbilityDeveloperScreen;
import org.academy.internal.client.renderer.item.ItemRenderers;

import java.io.File;

public final class AcademyCraftClient {
    public static final File CLIENT_CONFIG_FILE;
    public static final AcademyCraftClientConfig<SkillClientConfig> CLIENT_CONFIG;

    static {
        CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
        CLIENT_CONFIG = new AcademyCraftClientConfig<>();
        WirelessManager.initClient();
        AbilityDeveloperScreen.initS2CPacket();
        ItemRenderers.init();
    }
}