package org.academy.internal.common.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.misaka.MisakaOrbitalChargeDisplay;
import org.academy.internal.server.misaka.MisakaOrbitalCharge;
import org.academy.internal.server.misaka.MisakaOrbitalStrikeSupport;
import org.academy.internal.server.world.level.storage.MisakaRelayEntry;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class LaserDesignatorItem extends Item {
    public LaserDesignatorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static Optional<UUID> boundSatellite(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.LASER_DESIGNATOR.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(ItemDataComponents.DESIGNATOR_SATELLITE_ID.get()));
    }

    public static void bind(ItemStack stack, UUID satelliteId) {
        stack.set(ItemDataComponents.DESIGNATOR_SATELLITE_ID.get(), satelliteId);
    }

    public static void unbind(ItemStack stack) {
        stack.remove(ItemDataComponents.DESIGNATOR_SATELLITE_ID.get());
    }

    public static boolean isDesignator(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.LASER_DESIGNATOR.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                var eye = player.getEyePosition();
                var look = player.getViewVector(1.0f);
                var end = eye.add(look.scale(MisakaOrbitalStrikeSupport.DESIGNATOR_RANGE));
                BlockHitResult airCheck = level.clip(new ClipContext(
                        eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
                ));
                if (airCheck.getType() == HitResult.Type.BLOCK) {
                    return InteractionResult.PASS;
                }
                unbind(stack);
                if (player instanceof ServerPlayer serverPlayer) {
                    MisakaOrbitalCharge.clear(serverPlayer);
                    serverPlayer.sendOverlayMessage(Component.translatable("message.academy.laser_designator_unbound"));
                }
            }
            return InteractionResult.SUCCESS;
        }

        UUID satId = boundSatellite(stack).orElse(null);
        if (satId == null) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.academy.laser_designator_unbound_need"));
            }
            return InteractionResult.FAIL;
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID satId = boundSatellite(stack).orElse(null);
        if (satId == null) {
            player.stopUsingItem();
            MisakaOrbitalCharge.clear(player);
            return;
        }
        boolean fired = MisakaOrbitalCharge.tickCharge(
                player,
                satId,
                p -> MisakaOrbitalCharge.clipTarget(p, MisakaOrbitalStrikeSupport.DESIGNATOR_RANGE)
        );
        if (fired) {
            player.stopUsingItem();
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            MisakaOrbitalCharge.onReleased(player);
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return MisakaOrbitalChargeDisplay.isActive();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(MisakaOrbitalChargeDisplay.progress() * 13.0f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float p = MisakaOrbitalChargeDisplay.progress();
        int r = (int) ((1.0f - p) * 80 + p * 40);
        int g = (int) ((1.0f - p) * 180 + p * 220);
        int b = (int) ((1.0f - p) * 255 + p * 120);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltipAdder,
            TooltipFlag flag
    ) {
        boundSatellite(stack).ifPresentOrElse(
                id -> tooltipAdder.accept(Component.translatable(
                        "item.academy.laser_designator.bound",
                        MisakaRelayEntry.shortId(id)
                )),
                () -> tooltipAdder.accept(Component.translatable("item.academy.laser_designator.unbound"))
        );
        tooltipAdder.accept(Component.translatable("item.academy.laser_designator.charge_hint"));
    }
}
