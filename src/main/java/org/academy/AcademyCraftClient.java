package org.academy;

import dev.felnull.specialmodelloader.api.event.SpecialModelLoaderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.academy.api.client.input.InputSystem;
import org.academy.internal.AcademyCraftConfig;

import java.io.File;

@Environment(EnvType.CLIENT)
public class AcademyCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AcademyCraft.clientConfigFile = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
        AcademyCraft.checkFile(AcademyCraft.clientConfigFile);
        AcademyCraft.clientConfig = AcademyCraftConfig.loadConfig(AcademyCraft.clientConfigFile, AcademyCraftConfig.Env.CLIENT);
        SpecialModelLoaderEvents.LOAD_SCOPE.register(location -> AcademyCraft.MOD_ID.equals(location.getNamespace()));
        InputSystem.init();
        AbilitySystemClient.init();
    }
}