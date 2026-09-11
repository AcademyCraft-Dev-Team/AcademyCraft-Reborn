package org.academy.internal.common.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.server.misaka.MisakaOrbitalStrikeSupport;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;

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
                    serverPlayer.sendOverlayMessage(Component.translatable("message.academy.laser_designator_unbound"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        UUID satId = boundSatellite(stack).orElse(null);
        if (satId == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.academy.laser_designator_unbound_need"));
            return InteractionResult.FAIL;
        }
        var eye = player.getEyePosition();
        var look = player.getViewVector(1.0f);
        var end = eye.add(look.scale(MisakaOrbitalStrikeSupport.DESIGNATOR_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.academy.laser_designator_no_target"));
            return InteractionResult.FAIL;
        }
        var target = hit.getBlockPos();
        var result = MisakaRelayRegistry.get(serverLevel.getServer())
                .beginOrbitalStrike(serverLevel.getServer(), satId, target, serverPlayer);
        serverPlayer.sendOverlayMessage(Component.translatable(messageKey(result)));
        return result == MisakaOrbitalStrikeSupport.BeginResult.OK
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    private static String messageKey(MisakaOrbitalStrikeSupport.BeginResult result) {
        return switch (result) {
            case OK -> "message.academy.laser_designator_strike_ok";
            case NOT_FOUND -> "message.academy.laser_designator_sat_missing";
            case BAD_PHASE -> "message.academy.laser_designator_bad_phase";
            case NOT_BOUND -> "message.academy.laser_designator_laser_unbound";
            case FORCE_CRASH_ARMED -> "message.academy.laser_designator_force_crash";
            case BUSY -> "message.academy.laser_designator_busy";
            case COOLDOWN -> "message.academy.laser_designator_cooldown";
            case NO_POWER -> "message.academy.laser_designator_no_power";
            case WRONG_DIMENSION -> "message.academy.laser_designator_wrong_dim";
            case CHUNK_UNLOADED -> "message.academy.laser_designator_chunk";
        };
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
                id -> {
                    String raw = id.toString().replace("-", "");
                    String shortId = raw.length() >= 8 ? raw.substring(0, 8) : raw;
                    tooltipAdder.accept(Component.translatable("item.academy.laser_designator.bound", shortId));
                },
                () -> tooltipAdder.accept(Component.translatable("item.academy.laser_designator.unbound"))
        );
    }
}
