package org.academy.internal.server.misaka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaComputeDevice;
import org.academy.api.common.misaka.MisakaComputeDevices;
import org.academy.api.common.misaka.MisakaComputePresence;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/** Scans whether registered Misaka compute devices are present on a player. */
public final class MisakaDevicePresence {
    private MisakaDevicePresence() {
    }

    public static boolean isPresent(ServerPlayer player, MisakaComputeDevice device) {
        if (player == null || device == null) {
            return false;
        }
        try {
            return switch (device.presence()) {
                case WORN -> hasWorn(player, device);
                case LOADED -> hasLoaded(player, device);
                case HELD -> hasInInventory(player, device);
            };
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            AcademyCraft.getLogger().warn("Misaka device {} presence check failed: {}", device.id(), t.toString());
            return false;
        }
    }

    public static boolean isActivelyUsing(ServerPlayer player, MisakaComputeDevice device) {
        if (player == null || device == null || device.presence() != MisakaComputePresence.HELD) {
            return false;
        }
        if (!player.isUsingItem()) {
            return false;
        }
        try {
            InteractionHand hand = player.getUsedItemHand();
            if (safeMatches(device, player.getItemInHand(hand))) {
                return true;
            }
            // Use-item copy can diverge from the hand stack for one tick; accept either.
            return safeMatches(device, player.getUseItem());
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            return false;
        }
    }

    public static List<MisakaComputeDevice> presentDevices(ServerPlayer player) {
        var out = new ArrayList<MisakaComputeDevice>();
        for (var device : MisakaComputeDevices.snapshot()) {
            if (isPresent(player, device)) {
                out.add(device);
            }
        }
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    private static boolean hasWorn(ServerPlayer player, MisakaComputeDevice device) {
        if (!ModList.get().isLoaded("curios")) {
            return false;
        }
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    for (var stacksHandler : handler.getCurios().values()) {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (safeMatches(device, stacks.getStackInSlot(i))) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    private static boolean hasLoaded(ServerPlayer player, MisakaComputeDevice device) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!safeMatches(device, stack)) {
                continue;
            }
            try {
                if (device.isLoaded(player, stack)) {
                    return true;
                }
            } catch (Throwable t) {
                if (t instanceof VirtualMachineError) {
                    throw (VirtualMachineError) t;
                }
                AcademyCraft.getLogger().warn("Misaka device {} isLoaded failed: {}", device.id(), t.toString());
            }
        }
        return false;
    }

    private static boolean hasInInventory(ServerPlayer player, MisakaComputeDevice device) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (safeMatches(device, inv.getItem(i))) {
                return true;
            }
        }
        return safeMatches(device, player.getOffhandItem());
    }

    private static boolean safeMatches(MisakaComputeDevice device, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return device.matches(stack);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            AcademyCraft.getLogger().warn("Misaka device {} matches failed: {}", device.id(), t.toString());
            return false;
        }
    }
}
