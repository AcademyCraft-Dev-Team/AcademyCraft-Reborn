package org.academy.internal.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.academy.api.common.wireless.WirelessUser;

public final class EnergyChargeHelper {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private EnergyChargeHelper() {
    }

    public static int charge(EnergyHandler handler) {
        if (handler == null) return 0;
        try (var transaction = Transaction.openRoot()) {
            var inserted = handler.insert(Integer.MAX_VALUE, transaction);
            if (inserted > 0) transaction.commit();
            return inserted;
        }
    }

    public static boolean chargeItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var access = ItemAccess.forStack(stack);
        return charge(access.getCapability(Capabilities.Energy.ITEM)) > 0;
    }

    public static boolean chargeEquipment(LivingEntity entity) {
        var charged = chargeItem(entity.getMainHandItem());
        charged |= chargeItem(entity.getOffhandItem());
        for (var slot : ARMOR_SLOTS) {
            charged |= chargeItem(entity.getItemBySlot(slot));
        }
        return charged;
    }

    public static boolean chargeBlock(Level level, BlockPos pos, Direction side) {
        var charged = charge(level.getCapability(Capabilities.Energy.BLOCK, pos, side)) > 0;
        if (charged) return true;

        if (level.getBlockEntity(pos) instanceof WirelessUser user) {
            return user.receiveEnergy(Integer.MAX_VALUE, false) > 0;
        }
        return false;
    }

    public static boolean chargeEntity(LivingEntity entity) {
        var charged = charge(entity.getCapability(Capabilities.Energy.ENTITY, null)) > 0;
        return chargeEquipment(entity) || charged;
    }
}
