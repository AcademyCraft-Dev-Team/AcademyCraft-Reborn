package org.academy;

import net.minecraft.client.Minecraft;

import java.io.File;

public final class AcademyCraftClient {
    public static File clientConfigFile;
    public static AcademyCraftClientConfig clientConfig;

    static {
        clientConfigFile = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(clientConfigFile);
        clientConfig = new AcademyCraftClientConfig().loadConfig(clientConfigFile);
    }
}