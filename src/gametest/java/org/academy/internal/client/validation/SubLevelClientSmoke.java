package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.common.network.SubLevelInteractPacket;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Real integrated-client visual probe for a captured SubLevel. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelClientSmoke");
    private static volatile boolean requested;
    private static volatile boolean failed;
    private static volatile boolean screenshotSaved;
    private static volatile boolean screenshotRequested;
    private static volatile boolean collisionProbed;
    private static volatile boolean breakSent;
    private static volatile boolean breakSeen;
    private static volatile boolean placeSent;
    private static volatile boolean placeSeen;
    private static volatile boolean leverRequested;
    private static volatile boolean useSent;
    private static volatile boolean useSeen;
    private static double initialPoseY = Double.NaN;
    private static int holdTicks;
    private static volatile SubLevelCaptureService.Result result;
    private static int ticks;
    private static int stableFrames;
    private static Path screenshotPath;

    private SubLevelClientSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || failed || screenshotSaved) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (++ticks > 600) {
            fail("timeout");
            return;
        }
        if (!requested) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return;
            requested = true;
            server.execute(() -> capture(server, minecraft));
            return;
        }
        if (result == null) return;
        NativeStructureProjectionEntity projection = null;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity candidate && candidate.isClientOnly()
                    && candidate.view().cells().size() >= result.blocks() - 1) {
                projection = candidate;
                break;
            }
        }
        if (projection == null) return;
        aimAt(minecraft, projection);
        if (Boolean.getBoolean("academy.sublevel.clientBreakProbe") && !breakSeen) {
            if (!breakSent) {
                if (!org.academy.internal.client.sublevel.SubLevelClientInteraction.attack(minecraft)) {
                    fail("client SubLevel attack raycast missed");
                    return;
                }
                breakSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BREAK_SENT");
                return;
            }
            if (projection.view().cells().size() == result.blocks() - 1) {
                breakSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BREAK_CONFIRMED cells={}", projection.view().cells().size());
            } else {
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.clientPlaceProbe") && breakSeen && !placeSeen) {
            if (!placeSent) {
                var eye = minecraft.player.getEyePosition();
                var view = minecraft.player.getViewVector(1.0F);
                var hit = org.academy.internal.client.sublevel.SubLevelClientState.raycast(
                        minecraft.level, eye, eye.add(view.scale(minecraft.player.blockInteractionRange() + 0.5)));
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PLACE_RAY eye={} view={} yaw={} pitch={} hit={}",
                        eye, view, minecraft.player.getYRot(), minecraft.player.getXRot(), hit);
                if (!org.academy.internal.client.sublevel.SubLevelClientInteraction.use(minecraft)) {
                    fail("client SubLevel place raycast missed");
                    return;
                }
                placeSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PLACE_SENT");
                return;
            }
            if (projection.view().cells().size() == result.blocks()) {
                placeSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PLACE_CONFIRMED cells={}", projection.view().cells().size());
            } else {
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.clientUseProbe") && placeSeen && !useSeen) {
            if (!leverRequested) {
                leverRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareLever(server, result.id()));
                return;
            }
            var lever = findLever(projection);
            if (lever == null) return;
            aimAtCell(minecraft, projection, lever.position());
            if (!useSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        lever.position().getX() + 0.5, lever.position().getY() + 0.5, lever.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), lever.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                useSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_USE_SENT local={}", lever.position());
                return;
            }
            if (lever.state().getValue(LeverBlock.POWERED)) {
                useSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_USE_CONFIRMED powered=true");
            } else {
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.clientCollision") && !collisionProbed) {
            collisionProbed = true;
            var center = projection.renderPose(1.0F).localToWorldPoint(new Vec3(1, 1, 1));
            var query = new net.minecraft.world.phys.AABB(center.x - 0.5, center.y - 0.5, center.z - 0.5,
                    center.x + 0.5, center.y + 0.5, center.z + 0.5);
            int collisions = 0;
            for (var shape : minecraft.level.getBlockCollisions(minecraft.player, query)) {
                if (!shape.isEmpty()) collisions++;
            }
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_COLLISION_PROBE inside={}", collisions);
            if (collisions == 0) {
                fail("client collision index produced no shapes");
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.fallProbe")) {
            if (Double.isNaN(initialPoseY)) initialPoseY = projection.viewState().pose().worldOrigin().y;
            if (holdTicks++ < 100) return;
            if (holdTicks == 101) {
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_FALL_PROBE y={} initial_y={} delta={}",
                        projection.viewState().pose().worldOrigin().y, initialPoseY,
                        projection.viewState().pose().worldOrigin().y - initialPoseY);
            }
        }
        var frames = NativeStructureProjectionRenderer.submittedFrames(projection.getId());
        if (frames <= 0) return;
        if (++stableFrames < 20) return;
        if (screenshotRequested) return;
        screenshotRequested = true;
        var name = "sublevel-client-smoke-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
        screenshotPath = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                if (!Files.isRegularFile(screenshotPath) || Files.size(screenshotPath) <= 0) {
                    fail("screenshot missing: " + message.getString());
                    return;
                }
                screenshotSaved = true;
                AcademyCraft.LOGGER.info("SUBLEVEL_CLIENT_SMOKE_PASSED id={} cells={} frames={} screenshot={}",
                        result.id().value(), result.blocks(), frames, screenshotPath);
                minecraft.stop();
            } catch (Throwable error) {
                fail(error.toString());
            }
        });
    }

    private static void prepareLever(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) {
                    nearest = distance;
                    local = cell.position();
                }
            }
            var lever = Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, Direction.NORTH)
                    .setValue(LeverBlock.POWERED, false);
            if (!runtime.storage().setBlock(subLevel.plot(), local, lever, 3)) {
                throw new IllegalStateException("plot setBlock failed at " + local);
            }
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_USE_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static org.academy.internal.common.structure.space.NativeStructureView.Cell findLever(
            NativeStructureProjectionEntity projection) {
        for (var cell : projection.view().cells()) {
            if (cell.state().is(Blocks.LEVER)) return cell;
        }
        return null;
    }

    private static void aimAtCell(Minecraft minecraft, NativeStructureProjectionEntity projection, BlockPos local) {
        var target = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5));
        var eye = minecraft.player.getEyePosition();
        var delta = target.subtract(eye);
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        minecraft.player.setXRot((float) Math.toDegrees(-Math.atan2(delta.y, horizontal)));
    }

    private static void aimAt(Minecraft minecraft, NativeStructureProjectionEntity projection) {
        var cells = projection.view().cells();
        if (cells.isEmpty()) return;
        var cell = cells.get(cells.size() - 1);
        var target = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
        var eye = minecraft.player.getEyePosition();
        var delta = target.subtract(eye);
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        minecraft.player.setXRot((float) Math.toDegrees(-Math.atan2(delta.y, horizontal)));
    }

    private static void capture(MinecraftServer server, Minecraft minecraft) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var player = server.getPlayerList().getPlayers().getFirst();
            var look = player.getLookAngle();
            var horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() < 1.0e-4) horizontal = Vec3.atLowerCornerOf(player.getDirection().getUnitVec3i());
            var center = BlockPos.containing(player.getEyePosition().add(horizontal.normalize().scale(3)));
            var level = player.level();
            var groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
            var base = center.above(Math.max(2, groundY + 2 - center.getY())).offset(-1, 0, -1);
            var positions = new ArrayList<BlockPos>(8);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        var position = base.offset(x, y, z);
                        level.setBlock(position, Blocks.GLOWSTONE.defaultBlockState(), 3);
                        positions.add(position);
                    }
                }
            }
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_GROUND base_y={} terrain_y={}", base.getY(), groundY);
            result = SubLevelCaptureService.capture(level, runtime, positions);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOWSTONE, 64));
            AcademyCraft.LOGGER.info("SUBLEVEL_CLIENT_SMOKE_CAPTURED id={} blocks={} boxes={}",
                    result.id().value(), result.blocks(), result.boxes());
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void fail(String message) {
        failed = true;
        AcademyCraft.LOGGER.error("SUBLEVEL_CLIENT_SMOKE_FAILED {}", message);
        Minecraft.getInstance().stop();
    }
}
