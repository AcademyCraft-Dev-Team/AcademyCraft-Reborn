package org.academy.internal.common.compatibility;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.Optional;

/** Isolated optional Curios integration for the magnetic hook belt slot. */
public final class MagneticHookCuriosCompat {
    public static final String BELT_SLOT = "belt";
    private static boolean registered;

    private MagneticHookCuriosCompat() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CuriosApi.registerCurio(Items.MAGNETIC_HOOK.get(), new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext slotContext, ItemStack stack) {
                return BELT_SLOT.equals(slotContext.identifier());
            }
        });
    }

    public static Optional<ItemStack> findEquippedBeltHook(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(
                        stack -> stack.is(Items.MAGNETIC_HOOK.get()), BELT_SLOT))
                .map(result -> result.stack());
    }
}
