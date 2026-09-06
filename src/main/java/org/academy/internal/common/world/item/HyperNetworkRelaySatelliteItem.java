package org.academy.internal.common.world.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Hyperdimensional Misaka relay satellite; target dimension is stored on the stack. */
public final class HyperNetworkRelaySatelliteItem extends Item {
    public static final Identifier DEFAULT_TARGET = Identifier.withDefaultNamespace("the_nether");

    public HyperNetworkRelaySatelliteItem(Properties properties) {
        super(properties.stacksTo(16)
                .component(ItemDataComponents.RELAY_TARGET_DIMENSION.get(), DEFAULT_TARGET));
    }

    public static ResourceKey<Level> targetDimension(ItemStack stack) {
        Identifier id = stack.getOrDefault(
                ItemDataComponents.RELAY_TARGET_DIMENSION.get(),
                DEFAULT_TARGET
        );
        if (id == null) {
            id = DEFAULT_TARGET;
        }
        var key = ResourceKey.create(Registries.DIMENSION, id);
        if (Level.OVERWORLD.equals(key)) {
            return Level.NETHER;
        }
        return key;
    }

    public static void setTargetDimension(ItemStack stack, ResourceKey<Level> dimension) {
        if (stack == null || dimension == null || Level.OVERWORLD.equals(dimension)) {
            return;
        }
        stack.set(ItemDataComponents.RELAY_TARGET_DIMENSION.get(), dimension.identifier());
    }
}
