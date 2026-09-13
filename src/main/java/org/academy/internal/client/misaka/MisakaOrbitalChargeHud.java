package org.academy.internal.client.misaka;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.misaka.MisakaOrbitalChargeDisplay;
import org.academy.internal.common.network.misaka.MisakaOrbitalChargePacket;

/**
 * Action-bar charge readout for the laser designator (hotbar durability bar uses
 * {@link MisakaOrbitalChargeDisplay}).
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MisakaOrbitalChargeHud {
    private static int lastOverlayTick = Integer.MIN_VALUE;

    private MisakaOrbitalChargeHud() {
    }

    public static void apply(MisakaOrbitalChargePacket packet) {
        if (packet == null || !packet.active()) {
            clearOverlay();
            MisakaOrbitalChargeDisplay.clear();
            return;
        }
        MisakaOrbitalChargeDisplay.set(true, packet.charge(), packet.need(), packet.rate());
        showOverlay(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!MisakaOrbitalChargeDisplay.isActive()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) {
            clearOverlay();
            MisakaOrbitalChargeDisplay.clear();
            return;
        }
        showOverlay(false);
    }

    private static void showOverlay(boolean force) {
        var player = Minecraft.getInstance().player;
        if (player == null || !MisakaOrbitalChargeDisplay.isActive()) {
            return;
        }
        int tick = player.tickCount;
        if (!force && tick - lastOverlayTick < 2) {
            return;
        }
        lastOverlayTick = tick;
        float progress = MisakaOrbitalChargeDisplay.progress();
        int filled = Math.round(progress * 20.0f);
        var bar = new StringBuilder(22).append('[');
        for (int i = 0; i < 20; i++) {
            bar.append(i < filled ? '|' : '.');
        }
        bar.append(']');
        player.sendOverlayMessage(Component.translatable(
                "message.academy.laser_designator_charging_hud",
                bar.toString(),
                String.format("%.2f", progress * 100.0f),
                Math.round(MisakaOrbitalChargeDisplay.rate())
        ));
    }

    private static void clearOverlay() {
        lastOverlayTick = Integer.MIN_VALUE;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.empty());
        }
    }
}
