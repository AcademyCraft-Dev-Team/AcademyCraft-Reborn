package org.academy.internal.common.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.academy.internal.server.storage.SpatialStorageSavedData;
import org.academy.internal.server.storage.SpatialStorageService;

import java.util.function.Consumer;

public final class SpatialStorageUnitItem extends Item {
    public SpatialStorageUnitItem(Properties properties) {
        super(properties.stacksTo(1).component(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), false));
    }

    public static boolean isEnabled(ItemStack stack) {
        return stack.getItem() instanceof SpatialStorageUnitItem
                && stack.getOrDefault(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), false);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isEnabled(stack);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) toggle(serverPlayer, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!context.isSecondaryUseActive()) {
            if (context.getPlayer() instanceof ServerPlayer player) toggle(player, stack);
            return InteractionResult.SUCCESS;
        }
        var level = context.getLevel();
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        if (player == null || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        var destination = level.getCapability(Capabilities.Item.BLOCK, pos, context.getClickedFace());
        if (destination == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            var data = SpatialStorageSavedData.get(serverPlayer.level().getServer());
            long moved = data.transfer(SpatialStorageService.id(stack), Integer.MAX_VALUE, (resource, count) -> {
                int accepted;
                try (var transaction = Transaction.openRoot()) {
                    accepted = destination.insert(resource, (int) Math.min(count, Integer.MAX_VALUE), transaction);
                    transaction.commit();
                }
                return accepted;
            });
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.academy.spatial_storage_unit.transferred", moved,
                    data.count(SpatialStorageService.id(stack))), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static void toggle(ServerPlayer player, ItemStack stack) {
        SpatialStorageService.id(stack);
        stack.set(ItemDataComponents.SPATIAL_STORAGE_ENABLED.get(), !isEnabled(stack));
        player.sendSystemMessage(Component.translatable(isEnabled(stack)
                ? "item.academy.spatial_storage_unit.enabled" : "item.academy.spatial_storage_unit.disabled"), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(isEnabled(stack)
                ? "item.academy.spatial_storage_unit.enabled" : "item.academy.spatial_storage_unit.disabled")
                .withStyle(isEnabled(stack) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        for (int line = 1; line <= 4; line++) tooltip.accept(Component.translatable(
                "item.academy.spatial_storage_unit.tooltip." + line).withStyle(ChatFormatting.GRAY));
    }
}
