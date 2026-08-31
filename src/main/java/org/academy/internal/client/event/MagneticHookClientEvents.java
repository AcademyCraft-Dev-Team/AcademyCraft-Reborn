package org.academy.internal.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.AcademyCraft;
import org.academy.api.client.input.MouseButtonEvent;
import org.academy.internal.common.compatibility.MagneticHookCuriosCompat;
import org.academy.internal.common.network.MagneticHookActionPacket;
import org.misaka.MisakaNetworkClient;

/** Curios-belt middle-mouse controls for the magnetic hook. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MagneticHookClientEvents {
    private static final int MIDDLE_MOUSE_BUTTON = 2;

    private MagneticHookClientEvents() {
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (event.button != MIDDLE_MOUSE_BUTTON || event.action != InputConstants.PRESS
                || !ModList.get().isLoaded("curios")) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.screen() != null
                || !minecraft.isWindowActive() || player.isSpectator()
                || MagneticHookCuriosCompat.findEquippedBeltHook(player).isEmpty()) {
            return;
        }
        MisakaNetworkClient.send(new MagneticHookActionPacket(player.isShiftKeyDown()));
        event.setCanceled(true);
    }
}
