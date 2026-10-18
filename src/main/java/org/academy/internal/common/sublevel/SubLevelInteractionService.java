package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.AcademyCraft;

/**
 * Phase 3 server-side interaction bridge. It uses the real plot Level for vanilla block use,
 * placement and destruction; client raycast/packet routing is added in Phase 4.
 */
public final class SubLevelInteractionService {
    private SubLevelInteractionService() {
    }

    /** Server-side line-of-sight validation for a client interaction packet. */
    public static boolean validateRay(MinecraftServer server, SubLevelId id, BlockPos local,
                                      Vec3 point, ServerPlayer player) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return false;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return false;
        var eye = player.getEyePosition();
        double reach = player.blockInteractionRange() + 1.0;
        if (eye.distanceToSqr(point) > reach * reach) {
            AcademyCraft.LOGGER.info("SubLevel ray rejected distance local={} eye={} point={} reach={}", local, eye, point, reach);
            return false;
        }
        var pose = subLevel.pose();
        var localPoint = pose.worldToLocalPoint(point);
        if (!new AABB(local).inflate(1.5).contains(localPoint)) {
            AcademyCraft.LOGGER.debug("SubLevel ray rejected box local={} localPoint={} point={}", local, localPoint, point);
            return false;
        }
        var localEye = pose.worldToLocalPoint(eye);
        var center = Vec3.atCenterOf(local);
        var view = runtime.view(id);
        var look = pose.worldToLocalDirection(player.getViewVector(1.0F)).normalize();
        var lookHit = view.clip(new ClipContext(localEye, localEye.add(look.scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        var pointHit = view.clip(new ClipContext(localEye, localPoint,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        var centerHit = view.clip(new ClipContext(localEye, center,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean valid = lookHit.getType() == HitResult.Type.BLOCK && lookHit.getBlockPos().equals(local)
                || pointHit.getType() == HitResult.Type.BLOCK && pointHit.getBlockPos().equals(local)
                || centerHit.getType() == HitResult.Type.BLOCK && centerHit.getBlockPos().equals(local);
        if (!valid) {
            AcademyCraft.LOGGER.debug("SubLevel ray rejected local={} eye={} point={} pointHit={} centerHit={}",
                    local, eye, point, pointHit.getType(), centerHit.getType());
        }
        return valid;
    }

    public static boolean breakBlock(MinecraftServer server, SubLevelId id, BlockPos local,
                                     ServerPlayer player) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return false;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return false;
        var level = runtime.storage().level();
        var world = runtime.storage().worldPosition(subLevel.plot(), local);
        if (!level.hasChunkAt(world) || level.getBlockState(world).isAir()) return false;
        if (player != null && !level.mayInteract(player, world)) return false;
        if (!level.destroyBlock(world, player != null, player)) return false;
        runtime.refreshGeometry(id);
        return true;
    }

    public static InteractionResult useBlock(MinecraftServer server, SubLevelId id, BlockPos local,
                                             Direction face, Vec3 hit, ServerPlayer player,
                                             InteractionHand hand) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return InteractionResult.FAIL;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return InteractionResult.FAIL;
        var level = runtime.storage().level();
        var world = runtime.storage().worldPosition(subLevel.plot(), local);
        var state = level.getBlockState(world);
        if (!level.hasChunkAt(world) || state.isAir()) return InteractionResult.FAIL;
        if (!level.mayInteract(player, world)) return InteractionResult.FAIL;
        if (state.is(Blocks.CHEST)
                && runtime.storage().blockEntity(subLevel.plot(), local) instanceof ChestBlockEntity chest) {
            Container container = chest;
            int rows = 3;
            var title = Component.translatable("container.chest");
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                var otherPos = ChestBlock.getConnectedBlockPos(local, state);
                if (runtime.storage().blockEntity(subLevel.plot(), otherPos) instanceof ChestBlockEntity other) {
                    container = new CompoundContainer(chest, other);
                    rows = 6;
                    title = Component.translatable("container.chestDouble");
                }
            }
            var center = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local));
            final var menuContainer = new SubLevelChestContainer(runtime.host(), container, center);
            final int menuRows = rows;
            final var menuTitle = title;
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                    new ChestMenu(menuRows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3,
                            containerId, inventory, menuContainer, menuRows), menuTitle));
            return InteractionResult.SUCCESS;
        }
        var result = new BlockHitResult(hit, face, world, false);
        var stack = player.getItemInHand(hand);
        var before = level.getBlockState(world);
        var interaction = before.useItemOn(stack, level, player, hand, result);
        if (!interaction.consumesAction()) {
            interaction = before.useWithoutItem(level, player, result);
        }
        var after = level.getBlockState(world);
        AcademyCraft.LOGGER.debug("SubLevel use {}: {} -> {} result={}", world, before, after, interaction);
        if (interaction.consumesAction()) runtime.refreshGeometry(id);
        return interaction;
    }

    public static InteractionResult placeBlock(MinecraftServer server, SubLevelId id, BlockPos local,
                                               Direction face, Vec3 hit, ServerPlayer player,
                                               InteractionHand hand) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return InteractionResult.FAIL;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return InteractionResult.FAIL;
        var level = runtime.storage().level();
        var world = runtime.storage().worldPosition(subLevel.plot(), local);
        if (!level.hasChunkAt(world) || !level.mayInteract(player, world)) return InteractionResult.FAIL;
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem)) return InteractionResult.FAIL;
        var hitResult = new BlockHitResult(hit, face, world, false);
        var context = new BlockPlaceContext(level, player, hand, stack, hitResult);
        var result = ((BlockItem) stack.getItem()).place(context);
        if (result.consumesAction()) runtime.refreshGeometry(id);
        return result;
    }
}
