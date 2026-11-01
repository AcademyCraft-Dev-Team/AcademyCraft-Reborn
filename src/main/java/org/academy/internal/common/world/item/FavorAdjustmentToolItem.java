package org.academy.internal.common.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.HashSet;
import java.util.Locale;
import java.util.function.Consumer;

public final class FavorAdjustmentToolItem extends Item {
    private static final int COOLDOWN_TICKS = 5;

    public FavorAdjustmentToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                 InteractionHand hand) {
        if (!(target instanceof MisakaSisterEntity sister)) {
            return InteractionResult.PASS;
        }
        return sister.forceMobInteract(player, hand);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.academy.favor_tool.aim"));
        }
        return InteractionResult.SUCCESS.withoutItem();
    }

    /** Called before normal relationship and incapacitation gates in the sister interaction. */
    public void adjustFavor(ServerPlayer player, MisakaSisterEntity sister, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        if (!player.isCreative()) {
            player.sendOverlayMessage(Component.translatable("message.academy.favor_tool.creative_only"));
            return;
        }
        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            return;
        }
        if (!record.awakened) {
            player.sendOverlayMessage(Component.translatable("message.academy.favor_tool.unawakened"));
            return;
        }
        String name = player.getGameProfile().name();
        int before = record.favorByPlayerName.getOrDefault(name, 0);
        var previousBeneficiaries = new HashSet<>(FavorService.benevolentNames(record));
        previousBeneficiaries.add(record.lastInteractedBenevolentPlayerName);
        int after = FavorService.adjustFavorDirectly(record, name, player.isShiftKeyDown() ? -1 : 1);
        var server = player.level().getServer();
        MisakaSisterRoster.get(server).setDirty();
        var relation = Component.translatable("misaka.relation."
                + FavorService.relation(record, name).name().toLowerCase(Locale.ROOT));
        player.sendOverlayMessage(Component.translatable(
                "message.academy.favor_tool.adjusted", sister.getDisplayName(), before, after, relation));
        InteractionGate.scheduleAfterInteract(server, record, name);
        // Players losing the highest-favor position must also have their old CP removed.
        server.execute(() -> MisakaComputeContribution.refreshCpForNames(server, previousBeneficiaries));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.academy.favor_adjustment_tool.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.favor_adjustment_tool.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.favor_adjustment_tool.tooltip.3")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
