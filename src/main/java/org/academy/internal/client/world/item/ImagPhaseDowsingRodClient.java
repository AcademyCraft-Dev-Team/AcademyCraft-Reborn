package org.academy.internal.client.world.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.academy.internal.common.world.item.ImagPhaseDowsingRodItem;
import org.academy.internal.common.world.item.Items;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.List;

public final class ImagPhaseDowsingRodClient {
    private static List<BlockPos> targetSections = List.of();
    private static ClientLevel lastLevel;

    private ImagPhaseDowsingRodClient() {
    }

    public static void init() {
        MisakaNetworkClient.NETWORK_MANAGER.register(ImagPhaseDowsingRodClient.class);
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            clear();
        }
        if (player == null || !(player.getMainHandItem().is(Items.IMAG_PHASE_DOWSING_ROD.get())
                || player.getOffhandItem().is(Items.IMAG_PHASE_DOWSING_ROD.get()))) {
            clear();
        }
    }

    public static List<BlockPos> targetSections() {
        return targetSections;
    }

    private static void clear() {
        targetSections = List.of();
    }

    @SubscribePacket
    public static void handleSync(ImagPhaseDowsingRodItem.SyncPacket packet) {
        targetSections = packet.sections();
    }
}
