package org.academy.internal.server.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AbilityBlockDrops;
import org.academy.internal.common.compatibility.BeyondDimensionsSpatialCompat;
import org.academy.internal.common.compatibility.SpatialStorageCuriosCompat;
import org.academy.internal.common.world.item.ItemDataComponents;
import org.academy.internal.common.world.item.SpatialStorageUnitItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class SpatialStorageService {
    private SpatialStorageService() {
    }

    public static List<ItemStack> carriedUnits(ServerPlayer player) {
        var units = new ArrayList<ItemStack>();
        addUnit(units, player.getMainHandItem());
        addUnit(units, player.getOffhandItem());
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) addUnit(units, inventory.getItem(slot));
        if (ModList.get().isLoaded("curios")) units.addAll(SpatialStorageCuriosCompat.equipped(player));
        return units;
    }

    private static void addUnit(List<ItemStack> units, ItemStack stack) {
        if (stack.getItem() instanceof SpatialStorageUnitItem) units.add(stack);
    }

    public static boolean hasEnabledUnit(ServerPlayer player) {
        return carriedUnits(player).stream().anyMatch(SpatialStorageUnitItem::isEnabled);
    }

    public static UUID id(ItemStack unit) {
        var id = unit.get(ItemDataComponents.SPATIAL_STORAGE_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            unit.set(ItemDataComponents.SPATIAL_STORAGE_ID.get(), id);
        }
        return id;
    }

    /** Does not mutate the supplied drop; the caller must consume it when true is returned. */
    public static boolean collect(ServerPlayer player, ItemStack drop) {
        if (drop.isEmpty()) return false;
        for (var unit : carriedUnits(player)) {
            if (!SpatialStorageUnitItem.isEnabled(unit)) continue;
            SpatialStorageSavedData.get(player.level().getServer()).store(id(unit), drop);
            return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    private static void captureDrop(EntityJoinLevelEvent event) {
        var player = AbilityBlockDrops.currentBreaker();
        if (player == null || event.loadedFromDisk() || event.getLevel().isClientSide()
                || event.getLevel() != player.level() || !(event.getEntity() instanceof ItemEntity item)) return;
        if (collect(player, item.getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !ModList.get().isLoaded("beyonddimensions")) return;
        var visited = new HashSet<UUID>();
        var data = SpatialStorageSavedData.get(player.level().getServer());
        for (var unit : carriedUnits(player)) {
            var id = unit.get(ItemDataComponents.SPATIAL_STORAGE_ID.get());
            if (id != null && visited.add(id)) BeyondDimensionsSpatialCompat.transfer(player, data, id);
        }
    }
}
