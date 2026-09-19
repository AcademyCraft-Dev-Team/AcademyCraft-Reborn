package org.academy.api.common.vfx;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Actual successful repair sites, independent of the repair skill or actor type. */
public final class RepairVisualParts {
    public static final int BODY = 1, HEAD = 2, CHEST = 4, LEGS = 8, FEET = 16, MAIN_HAND = 32, OFF_HAND = 64;
    public static final int ALL = 127;
    private RepairVisualParts() { }

    public static int visibleSlot(LivingEntity actor, ItemStack repaired) {
        if (repaired.isEmpty()) return 0;
        int result = 0;
        for (var slot : EquipmentSlot.values()) {
            if (actor.getItemBySlot(slot) != repaired) continue;
            result |= switch (slot) {
                case HEAD -> HEAD;
                case CHEST -> CHEST;
                case LEGS -> LEGS;
                case FEET -> FEET;
                case MAINHAND -> MAIN_HAND;
                case OFFHAND -> OFF_HAND;
                default -> 0;
            };
        }
        return result;
    }
}
