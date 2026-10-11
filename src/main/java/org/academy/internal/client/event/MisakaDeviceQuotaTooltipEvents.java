package org.academy.internal.client.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaComputeDevice;
import org.academy.api.common.misaka.MisakaComputeDevices;
import org.academy.internal.client.misaka.MisakaDeviceQuotaClient;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MisakaDeviceQuotaTooltipEvents {
    private MisakaDeviceQuotaTooltipEvents() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player == null) {
            return;
        }
        var stack = event.getItemStack();
        for (var reg : MisakaComputeDevices.clientSnapshot()) {
            if (!safeMatches(reg, stack)) {
                continue;
            }
            var row = MisakaDeviceQuotaClient.row(reg.id());
            if (row == null) {
                break;
            }
            if (row.present() && row.effectivePercent() > 0) {
                event.getToolTip().add(Component.translatable(
                                "tooltip.academy.misaka_device_quota", row.effectivePercent())
                        .withStyle(ChatFormatting.AQUA));
            } else if (row.present() && row.lastPercent() > 0) {
                event.getToolTip().add(Component.translatable(
                                "tooltip.academy.misaka_device_quota_insufficient",
                                row.lastPercent(),
                                MisakaDeviceQuotaClient.remainingPercentSlots())
                        .withStyle(ChatFormatting.GOLD));
            } else if (!row.present() && row.lastPercent() > 0) {
                event.getToolTip().add(Component.translatable(
                                "tooltip.academy.misaka_device_quota_unequipped", row.lastPercent())
                        .withStyle(ChatFormatting.GRAY));
            }
            break;
        }
    }

    private static boolean safeMatches(MisakaComputeDevice device, net.minecraft.world.item.ItemStack stack) {
        try {
            return device.matches(stack);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            AcademyCraft.getLogger().debug("Misaka device matches failed for {}", device.id(), t);
            return false;
        }
    }
}
