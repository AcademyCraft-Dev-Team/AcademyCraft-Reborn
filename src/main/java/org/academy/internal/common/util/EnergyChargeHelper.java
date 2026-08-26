package org.academy.internal.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.academy.api.common.energy.AcademyEnergyItem;
import org.academy.api.common.wireless.WirelessUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.OptionalDouble;

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
        if (stack.getItem() instanceof AcademyEnergyItem energyItem) {
            return energyItem.receiveEnergy(stack, Integer.MAX_VALUE, false) > 0;
        }
        var access = ItemAccess.forStack(stack);
        return charge(access.getCapability(Capabilities.Energy.ITEM)) > 0;
    }

    /**
     * Transfers stored machine energy into an item capability in one server-side operation.
     */
    public static int transferToItem(WirelessUser source, ItemStack stack, int maxTransfer) {
        if (source == null || stack.isEmpty() || maxTransfer <= 0) return 0;
        if (stack.getItem() instanceof AcademyEnergyItem energyItem) {
            return transferToAcademyItem(source, stack, energyItem, maxTransfer);
        }
        return transferToEnergyHandler(
                source,
                ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM),
                maxTransfer
        );
    }

    /**
     * Transfers into an actual container slot, preserving item replacements made by NeoForge
     * energy handlers when their data components change.
     */
    public static int transferToItem(
            WirelessUser source,
            Container container,
            int slot,
            int maxTransfer
    ) {
        if (source == null || container == null || slot < 0
                || slot >= container.getContainerSize() || maxTransfer <= 0) return 0;
        var stack = container.getItem(slot);
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof AcademyEnergyItem energyItem) {
            var transferred = transferToAcademyItem(source, stack, energyItem, maxTransfer);
            if (transferred > 0) container.setChanged();
            return transferred;
        }
        var access = ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(container), slot);
        return transferToEnergyHandler(
                source,
                access.getCapability(Capabilities.Energy.ITEM),
                maxTransfer
        );
    }

    static int transferToAcademyItem(
            WirelessUser source,
            ItemStack stack,
            AcademyEnergyItem target,
            int maxTransfer
    ) {
        var available = source.extractEnergy(maxTransfer, true);
        if (available <= 0) return 0;
        var accepted = target.receiveEnergy(stack, available, true);
        if (accepted <= 0) return 0;
        var extracted = source.extractEnergy(accepted, false);
        if (extracted <= 0) return 0;
        return target.receiveEnergy(stack, extracted, false);
    }

    private static int transferToEnergyHandler(
            WirelessUser source,
            EnergyHandler handler,
            int maxTransfer
    ) {
        if (handler == null) return 0;
        var available = source.extractEnergy(maxTransfer, true);
        if (available <= 0) return 0;
        try (var transaction = Transaction.openRoot()) {
            var inserted = handler.insert(available, transaction);
            if (inserted <= 0 || source.extractEnergy(inserted, true) != inserted) return 0;
            var extracted = source.extractEnergy(inserted, false);
            if (extracted != inserted) return 0;
            transaction.commit();
            return inserted;
        }
    }

    public static boolean hasEnergyStorage(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof AcademyEnergyItem
                || ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM) != null);
    }

    public static boolean isEnergyItemFull(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() instanceof AcademyEnergyItem energyItem) {
            return energyItem.receiveEnergy(stack, 1, true) <= 0;
        }
        var handler = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
        if (handler == null) return true;
        try (var transaction = Transaction.openRoot()) {
            return handler.insert(1, transaction) <= 0;
        }
    }

    public static boolean chargeEquipment(LivingEntity entity) {
        var charged = chargeItem(entity.getMainHandItem());
        charged |= chargeItem(entity.getOffhandItem());
        for (var slot : ARMOR_SLOTS) {
            charged |= chargeItem(entity.getItemBySlot(slot));
        }
        return charged;
    }

    public static boolean chargeHotbar(Player player) {
        var charged = false;
        for (var slot = 0; slot < 9; slot++) charged |= chargeItem(player.getInventory().getItem(slot));
        return charged;
    }

    public static boolean hasFullyChargedEquipment(Player player, boolean includeHotbar) {
        var found = false;
        var stacks = new ArrayList<ItemStack>();
        stacks.add(player.getMainHandItem());
        stacks.add(player.getOffhandItem());
        for (var slot : ARMOR_SLOTS) stacks.add(player.getItemBySlot(slot));
        if (includeHotbar) {
            for (var slot = 0; slot < 9; slot++) stacks.add(player.getInventory().getItem(slot));
        }
        for (var stack : stacks) {
            if (!hasEnergyStorage(stack)) continue;
            found = true;
            if (!isEnergyItemFull(stack)) return false;
        }
        return found;
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

    public static boolean hasBlockEnergyStorage(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WirelessUser) return true;
        for (var side : Direction.values()) {
            if (level.getCapability(Capabilities.Energy.BLOCK, pos, side) != null) return true;
        }
        return false;
    }

    public static OptionalDouble blockEnergyFraction(Level level, BlockPos pos) {
        var stored = 0L;
        var capacity = 0L;
        var seen = Collections.newSetFromMap(
                new IdentityHashMap<EnergyHandler, Boolean>());
        for (var side : Direction.values()) {
            var handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
            if (handler == null || !seen.add(handler)) continue;
            stored += Math.max(0, handler.getAmountAsInt());
            capacity += Math.max(0, handler.getCapacityAsInt());
        }
        if (capacity <= 0L && level.getBlockEntity(pos) instanceof WirelessUser user) {
            stored = Math.max(0, user.getEnergyStored());
            capacity = Math.max(0, user.getMaxEnergyStorage());
        }
        return capacity <= 0L
                ? OptionalDouble.empty()
                : OptionalDouble.of(Math.clamp((double) stored / capacity, 0.0, 1.0));
    }

    public static OptionalDouble entityEnergyFraction(LivingEntity entity) {
        var stored = 0L;
        var capacity = 0L;
        var handlers = new ArrayList<EnergyHandler>();
        var entityHandler = entity.getCapability(Capabilities.Energy.ENTITY, null);
        if (entityHandler != null) handlers.add(entityHandler);
        var stacks = new ArrayList<ItemStack>();
        stacks.add(entity.getMainHandItem());
        stacks.add(entity.getOffhandItem());
        for (var slot : ARMOR_SLOTS) stacks.add(entity.getItemBySlot(slot));
        for (var stack : stacks) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof AcademyEnergyItem energyItem) {
                stored += Math.max(0, energyItem.getEnergyStored(stack));
                capacity += Math.max(0, energyItem.getMaxEnergyStored(stack));
                continue;
            }
            var handler = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
            if (handler != null) handlers.add(handler);
        }
        for (var handler : handlers) {
            stored += Math.max(0, handler.getAmountAsInt());
            capacity += Math.max(0, handler.getCapacityAsInt());
        }
        return capacity <= 0L
                ? OptionalDouble.empty()
                : OptionalDouble.of(Math.clamp((double) stored / capacity, 0.0, 1.0));
    }
}
