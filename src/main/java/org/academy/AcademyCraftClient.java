package org.academy;

import dev.felnull.specialmodelloader.api.event.SpecialModelLoaderEvents;
import net.minecraft.client.Minecraft;

import java.io.File;

public final class AcademyCraftClient {
    public static File clientConfigFile;
    public static AcademyCraftClientConfig clientConfig;

    public static void init() {
        clientConfigFile = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(clientConfigFile);
        clientConfig = new AcademyCraftClientConfig().loadConfig(clientConfigFile);
        SpecialModelLoaderEvents.LOAD_SCOPE.register(location -> AcademyCraft.MOD_ID.equals(location.getNamespace()));
    }
}