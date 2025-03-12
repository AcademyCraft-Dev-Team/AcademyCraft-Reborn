package org.academy.forge;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.academy.AcademyCraft;

@Mod(AcademyCraft.MOD_ID)
@Mod.EventBusSubscriber(modid = AcademyCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AcademyCraftForge {
    public AcademyCraftForge(FMLJavaModLoadingContext fmlJavaModLoadingContext) {
        AcademyCraftRegisterForge.init(fmlJavaModLoadingContext.getModEventBus());
    }

    @SubscribeEvent
    public static void onInitialize(FMLCommonSetupEvent event) {
        AcademyCraft.init();
    }
}