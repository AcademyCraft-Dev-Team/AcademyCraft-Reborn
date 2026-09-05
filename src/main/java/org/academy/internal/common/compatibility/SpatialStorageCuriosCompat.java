package org.academy.internal.common.compatibility;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/** Loaded only when Curios is present. The curios:curio tag permits every slot type. */
public final class SpatialStorageCuriosCompat {
    private SpatialStorageCuriosCompat() {
    }

    public static void register() {
        CuriosApi.registerCurio(Items.SPATIAL_STORAGE_UNIT.get(), new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext context, ItemStack stack) {
                return true;
            }
        });
    }

    public static List<ItemStack> equipped(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findCurios(stack -> stack.is(Items.SPATIAL_STORAGE_UNIT.get()))
                        .stream().map(result -> result.stack()).toList())
                .orElse(List.of());
    }
}
