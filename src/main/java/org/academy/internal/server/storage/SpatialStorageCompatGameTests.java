package org.academy.internal.server.storage;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.internal.common.world.item.ItemDataComponents;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.UUID;

/** Kept separate so base game tests never resolve optional Curios classes. */
final class SpatialStorageCompatGameTests {
    private SpatialStorageCompatGameTests() {
    }

    static void verify(GameTestHelper helper, ServerPlayer player, ItemStack unit, UUID id) {
        var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
        curios.reset();
        var slot = curios.getCurios().entrySet().stream()
                .filter(entry -> entry.getValue().getStacks().getSlots() > 0).findFirst().orElseThrow();
        helper.assertTrue(CuriosApi.isStackValid(
                new SlotContext(slot.getKey(), player, 0, false, true), unit),
                "Universal Curios slot rejected the unit");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        unit.set(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), true);
        slot.getValue().getStacks().setStackInSlot(0, unit);
        helper.assertTrue(SpatialStorageService.hasEnabledUnit(player), "Equipped Curios unit was not found");
        helper.assertTrue(SpatialStorageService.collect(player, new ItemStack(net.minecraft.world.item.Items.STONE, 2)),
                "Curios-only unit did not collect drops");
        var data = SpatialStorageSavedData.get(player.level().getServer());
        long before = data.count(id);
        try {
            var netClass = Class.forName("com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet");
            helper.assertTrue(netClass.getMethod("getNetFromPlayer", Player.class).invoke(null, player) == null,
                    "Test player unexpectedly owns a network");
            player.tickCount = 20;
            SpatialStorageService.tick(new PlayerTickEvent.Post(player));
            helper.assertTrue(data.count(id) == before, "Missing network consumed items");
            var network = netClass.getMethod("createNewNetForPlayer", Player.class, long.class, int.class)
                    .invoke(null, player, 5L, 1);
            helper.assertTrue(network != null, "Unable to create a real BeyondDimensions network");
            SpatialStorageService.tick(new PlayerTickEvent.Post(player));
            helper.assertTrue(data.count(id) == before - 5, "Automatic network insertion did not honor capacity");
            var storage = netClass.getMethod("getUnifiedStorage").invoke(network);
            var keyClass = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.IStackKey");
            var key = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey")
                    .getConstructor(ItemStack.class).newInstance(new ItemStack(net.minecraft.world.item.Items.STONE));
            var entry = storage.getClass().getMethod("getStackByKey", keyClass).invoke(storage, key);
            helper.assertTrue((long) entry.getClass().getMethod("amount").invoke(entry) == 5,
                    "Accepted items did not reach the dimension network");
            SpatialStorageService.tick(new PlayerTickEvent.Post(player));
            helper.assertTrue(data.count(id) == before - 5, "Full network lost or duplicated items");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("BeyondDimensions integration test failed", exception);
        } finally {
            slot.getValue().getStacks().setStackInSlot(0, ItemStack.EMPTY);
        }
    }
}
