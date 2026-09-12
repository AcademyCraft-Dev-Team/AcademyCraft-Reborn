package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.world.level.block.entity.OwnedDevice;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Prevents unauthorized players from breaking protected Misaka/network devices.
 * Allow: device owner, DESTROY, or OWNER network permission on the device's connected network.
 * Null / unbound network → owner only.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaDeviceProtection {
    private MisakaDeviceProtection() {
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BlockEntity be)) {
            return;
        }
        if (!(be instanceof OwnedDevice owned)) {
            return;
        }
        if (owned.getOwnerUuid() == null) {
            return; // legacy unowned devices remain breakable until claimed
        }
        if (owned.isOwner(player)) {
            return;
        }
        if (hasNetworkDestroyPermission(player, be)) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.translatable("message.academy.device_break_denied"));
    }

    private static boolean hasNetworkDestroyPermission(ServerPlayer player, BlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        var server = level.getServer();
        if (server == null) {
            return false;
        }
        UUID networkId = resolveDeviceNetworkId(level, be);
        if (networkId == null) {
            return false;
        }
        var gov = MisakaNetworkGovernance.get(server);
        return gov.hasPermission(player, networkId, MisakaNetworkPermission.DESTROY)
                || gov.hasPermission(player, networkId, MisakaNetworkPermission.OWNER);
    }

    private static @Nullable UUID resolveDeviceNetworkId(ServerLevel level, BlockEntity be) {
        BlockPos nodePos = null;
        if (be instanceof WirelessNodeBlockEntity) {
            nodePos = be.getBlockPos();
        } else if (be instanceof WirelessUser user) {
            nodePos = user.getConnectedNodePosition();
        }
        if (nodePos == null) {
            return null;
        }
        return MisakaNAT.get().resolveNetworkId(level.getServer().overworld(), nodePos);
    }
}
