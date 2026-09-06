package org.academy.internal.common.world.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Standard Misaka network relay satellite (overworld coverage only). */
public final class NetworkRelaySatelliteItem extends Item {
    public NetworkRelaySatelliteItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static boolean isSatellite(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(Items.NETWORK_RELAY_SATELLITE.get())
                || stack.is(Items.HYPER_NETWORK_RELAY_SATELLITE.get()));
    }

    public static boolean isHyper(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.HYPER_NETWORK_RELAY_SATELLITE.get());
    }

    public static ResourceKey<Level> targetDimension(ItemStack stack) {
        if (isHyper(stack)) {
            return HyperNetworkRelaySatelliteItem.targetDimension(stack);
        }
        return Level.OVERWORLD;
    }
}
