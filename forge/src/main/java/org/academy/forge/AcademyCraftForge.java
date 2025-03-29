package org.academy.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.academy.AcademyCraft;
import org.academy.forge.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntityForge;

@Mod(AcademyCraft.MOD_ID)
@Mod.EventBusSubscriber(modid = AcademyCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AcademyCraftForge {
    public AcademyCraftForge() {
        AcademyCraftRegisterForge.init(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(AcademyCraftForge::onServer);
    }

    @SubscribeEvent
    public static void onInitialize(FMLCommonSetupEvent event) {
        AcademyCraft.init();
    }

    public static void onServer(ServerStartedEvent event) {
        AbilityDeveloperBlockEntityForge.initServer();
    }
}