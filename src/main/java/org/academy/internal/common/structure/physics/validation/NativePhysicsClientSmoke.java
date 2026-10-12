package org.academy.internal.common.structure.physics.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit title-screen probe: native module access is exercised without opening a save. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class NativePhysicsClientSmoke {
    private static boolean finished;
    private static int loadingTicks;

    private NativePhysicsClientSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!"client".equals(System.getProperty("academy.nativePhysicsSmoke")) || finished) return;
        var minecraft = Minecraft.getInstance();
        var directory = NativePhysicsSmokeSupport.verifyDirectory("client", minecraft.gameDirectory.toPath());
        if (!(minecraft.gui.screen() instanceof TitleScreen)) {
            if (++loadingTicks > 1200) {
                finished = true;
                minecraft.stop();
                throw new IllegalStateException("Native physics client did not reach its title screen");
            }
            return;
        }
        finished = true;
        try {
            NativePhysicsSmokeSupport.runAndRecord("client", directory);
        } finally {
            minecraft.stop();
        }
    }
}
