package org.academy.internal.common.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.entity.projectile.MagneticHook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Reusable launcher and recall controller for up to three magnetic hooks. */
public final class MagneticHookItem extends Item {
    public static final int MAX_ACTIVE_HOOKS = 3;
    private static final double RECALL_RANGE = 64.0;
    private static final float LAUNCH_SPEED = 1.8f;
    private static final int USE_COOLDOWN_TICKS = 4;

    public MagneticHookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            performAction(serverPlayer, player.getItemInHand(hand), player.isShiftKeyDown());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                recallAttachedTo(serverPlayer, context.getClickedPos());
                addCooldown(player, context.getItemInHand());
            } else {
                fire(serverPlayer, context.getItemInHand());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, net.minecraft.world.entity.LivingEntity target,
                                                   InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                recallAttachedTo(serverPlayer, target);
                addCooldown(player, stack);
            } else {
                fire(serverPlayer, stack);
            }
        }
        return InteractionResult.SUCCESS;
    }

    public static void performAction(ServerPlayer player, ItemStack stack, boolean recall) {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        if (recall) {
            recallLookedAt(player);
            addCooldown(player, stack);
        } else {
            fire(player, stack);
        }
    }

    /** Launches a new hook, retracting the oldest owned hook when the three-hook limit is reached. */
    public static void fire(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        var hooks = findOwnedHooks(player);
        while (hooks.size() >= MAX_ACTIVE_HOOKS) {
            hooks.removeFirst().discard();
        }

        var level = player.level();
        var hook = new MagneticHook(level, player, level.getGameTime());
        hook.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, LAUNCH_SPEED, 0.0f);
        level.addFreshEntity(hook);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.65f, 0.85f);
        addCooldown(player, stack);
    }

    /** Recalls the owned hook attached to the block or entity currently under the player's crosshair. */
    public static boolean recallLookedAt(ServerPlayer player) {
        var hooks = findOwnedHooks(player);
        if (hooks.isEmpty()) return false;
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        if (look.lengthSqr() <= 1.0e-8) return false;
        var end = eye.add(look.normalize().scale(RECALL_RANGE));
        var level = player.level();
        var blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        var blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : eye.distanceToSqr(blockHit.getLocation());
        var entityHit = ProjectileUtil.getEntityHitResult(
                level,
                player,
                eye,
                end,
                player.getBoundingBox().expandTowards(look.scale(RECALL_RANGE)).inflate(1.0),
                entity -> entity != player && !entity.isSpectator(),
                0.3f
        );

        MagneticHook selected = null;
        if (entityHit != null && eye.distanceToSqr(entityHit.getLocation()) < blockDistance) {
            var target = entityHit.getEntity();
            if (target instanceof MagneticHook hook && hook.isOwnedBy(player)) {
                selected = hook;
            } else {
                selected = hooks.stream().filter(hook -> hook.isAttachedTo(target)).findFirst().orElse(null);
            }
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            selected = hooks.stream().filter(hook -> hook.isAttachedTo(blockHit.getBlockPos()))
                    .findFirst().orElse(null);
        }
        return recall(player, selected);
    }

    public static boolean recallAttachedTo(ServerPlayer player, Entity target) {
        return recall(player, findOwnedHooks(player).stream()
                .filter(hook -> hook.isAttachedTo(target)).findFirst().orElse(null));
    }

    public static boolean recallAttachedTo(ServerPlayer player, net.minecraft.core.BlockPos target) {
        return recall(player, findOwnedHooks(player).stream()
                .filter(hook -> hook.isAttachedTo(target)).findFirst().orElse(null));
    }

    private static boolean recall(ServerPlayer player, MagneticHook hook) {
        if (hook == null || hook.isRemoved()) return false;
        hook.discard();
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.55f, 1.35f);
        return true;
    }

    private static List<MagneticHook> findOwnedHooks(ServerPlayer player) {
        var hooks = new ArrayList<MagneticHook>();
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof MagneticHook hook && hook.isOwnedBy(player) && !hook.isRemoved()) {
                    hooks.add(hook);
                }
            }
        }
        hooks.sort(Comparator.comparingLong(MagneticHook::launchOrder)
                .thenComparingInt(Entity::getId));
        return hooks;
    }

    private static void addCooldown(Player player, ItemStack stack) {
        player.getCooldowns().addCooldown(stack, USE_COOLDOWN_TICKS);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.academy.magnetic_hook.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.magnetic_hook.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.magnetic_hook.tooltip.3")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.academy.magnetic_hook.tooltip.4")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
