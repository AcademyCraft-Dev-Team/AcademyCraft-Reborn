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
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePose;
import org.academy.mixin.common.AbstractFurnaceBlockEntityAccessor;

/**
 * Phase 3 server-side interaction bridge. It uses the real plot Level for vanilla block use,
 * placement and destruction; client raycast/packet routing is added in Phase 4.
 */
public final class SubLevelInteractionService {
    private SubLevelInteractionService() {
    }

    /** Legacy proximity fallback for USE when the client/server pose gap exceeds the tight guard. */
    public static boolean validateProximity(MinecraftServer server, SubLevelId id, BlockPos local,
                                            Vec3 point, ServerPlayer player) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return false;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return false;
        var center = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local));
        double reach = player.blockInteractionRange() + 1.0;
        return player.getEyePosition().distanceToSqr(center) <= reach * reach
                && point.distanceToSqr(center) <= 4.0;
    }

    /** Tight USE guard: target block, face direction and reach, tolerant of the pose gap. */
    public static boolean validateUse(MinecraftServer server, SubLevelId id, BlockPos local,
                                      Vec3 point, ServerPlayer player) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return false;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return false;
        return validateUseAtPose(subLevel, local, point, player, subLevel.pose())
                || validateUseAtPose(subLevel, local, point, player, subLevel.previousPose());
    }

    private static boolean validateUseAtPose(SubLevel subLevel, BlockPos local, Vec3 point,
                                             ServerPlayer player, StructurePose pose) {
        var eye = player.getEyePosition();
        var center = pose.localToWorldPoint(Vec3.atCenterOf(local));
        double reach = player.blockInteractionRange() + 0.75;
        if (eye.distanceToSqr(center) > reach * reach) return false;
        if (point.distanceToSqr(center) > 0.81) return false;
        var localEye = pose.worldToLocalPoint(eye);
        var localPoint = pose.worldToLocalPoint(point);
        var localCenter = Vec3.atCenterOf(local);
        if (!new AABB(local).inflate(0.55).contains(localPoint)) return false;
        var look = localCenter.subtract(localEye);
        var target = localPoint.subtract(localEye);
        return look.lengthSqr() > 1.0E-8 && target.lengthSqr() > 1.0E-8
                && look.normalize().dot(target.normalize()) >= 0.5;
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
        return validateRayAtPose(player, subLevel, runtime.view(id), local, point, eye, reach, subLevel.pose())
                || validateRayAtPose(player, subLevel, runtime.view(id), local, point, eye, reach, subLevel.previousPose());
    }

    private static boolean validateRayAtPose(ServerPlayer player, SubLevel subLevel,
                                             org.academy.internal.common.structure.space.NativeStructureView view,
                                             BlockPos local, Vec3 point, Vec3 eye, double reach,
                                             StructurePose pose) {
        var localPoint = pose.worldToLocalPoint(point);
        if (!new AABB(local).inflate(1.5).contains(localPoint)) return false;
        var localEye = pose.worldToLocalPoint(eye);
        var center = Vec3.atCenterOf(local);
        var look = pose.worldToLocalDirection(player.getViewVector(1.0F)).normalize();
        var lookHit = view.clip(new ClipContext(localEye, localEye.add(look.scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        var pointHit = view.clip(new ClipContext(localEye, localPoint,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        var centerHit = view.clip(new ClipContext(localEye, center,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return lookHit.getType() == HitResult.Type.BLOCK && lookHit.getBlockPos().equals(local)
                || pointHit.getType() == HitResult.Type.BLOCK && pointHit.getBlockPos().equals(local)
                || centerHit.getType() == HitResult.Type.BLOCK && centerHit.getBlockPos().equals(local);
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
        if ((state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST))
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
            final var menuContainer = new SubLevelContainer(runtime.host(), container, center);
            final int menuRows = rows;
            final var menuTitle = title;
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                    new ChestMenu(menuRows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3,
                            containerId, inventory, menuContainer, menuRows), menuTitle));
            return InteractionResult.SUCCESS;
        }
        var blockEntity = runtime.storage().blockEntity(subLevel.plot(), local);
        if (blockEntity instanceof HopperBlockEntity hopper) {
            var center = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local));
            var proxy = new SubLevelContainer(runtime.host(), hopper, center);
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                    new HopperMenu(containerId, inventory, proxy), Component.translatable("container.hopper")));
            return InteractionResult.SUCCESS;
        }
        if (blockEntity instanceof DispenserBlockEntity dispenser) {
            var center = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local));
            var proxy = new SubLevelContainer(runtime.host(), dispenser, center);
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                    new DispenserMenu(containerId, inventory, proxy), Component.translatable("container.dispenser")));
            return InteractionResult.SUCCESS;
        }
        if (blockEntity instanceof BarrelBlockEntity barrel) {
            var center = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local));
            var proxy = new SubLevelContainer(runtime.host(), barrel, center);
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                    new ChestMenu(MenuType.GENERIC_9x3, containerId, inventory, proxy, 3),
                    Component.translatable("container.barrel")));
            return InteractionResult.SUCCESS;
        }
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            var center = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local));
            var proxy = new SubLevelContainer(runtime.host(), furnace, center);
            var data = ((AbstractFurnaceBlockEntityAccessor) furnace).academy$dataAccess();
            var title = Component.translatable("container.furnace");
            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) -> {
                if (state.is(Blocks.BLAST_FURNACE)) {
                    return new BlastFurnaceMenu(containerId, inventory, proxy, data);
                }
                if (state.is(Blocks.SMOKER)) {
                    return new SmokerMenu(containerId, inventory, proxy, data);
                }
                return new FurnaceMenu(containerId, inventory, proxy, data);
            }, title));
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
