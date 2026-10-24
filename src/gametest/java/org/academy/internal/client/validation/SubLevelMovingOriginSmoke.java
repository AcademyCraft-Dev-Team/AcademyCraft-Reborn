package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelRuntime;
import org.academy.internal.common.sublevel.SubLevelServer;

import java.util.ArrayList;
import java.util.UUID;

/** Launch a real body, then walk/jump/use a host block through its abandoned position while it moves. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelMovingOriginSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelMovingOriginSmoke");
    private static final FixtureInput INPUT = new FixtureInput();
    private static volatile MinecraftServer server;
    private static volatile UUID playerId;
    private static volatile SubLevelRuntime runtime;
    private static volatile SubLevelId structure;
    private static volatile BlockPos origin, lever;
    private static volatile boolean ready, launched, walking, hostUsed;
    private static volatile int movingDuringWalk;
    private static volatile String failure;
    private static boolean finished, launchRequested, jumped, used;
    private static ClientInput originalInput;
    private static int ticks, age, walkTicks, oldPositionTicks, airborne;
    private static double jumpHeight;

    private SubLevelMovingOriginSmoke() {
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            if (server == null) {
                require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                                .startsWith("native-sublevel-moving-origin-"),
                        "Moving-origin smoke requires a disposable game directory");
                server = mc.getSingleplayerServer();
                require(server != null, "Moving-origin smoke requires an integrated server");
                playerId = mc.player.getUUID();
                originalInput = mc.player.input;
                mc.player.input = INPUT;
                mc.options.pauseOnLostFocus = false;
                mc.gui.setScreen(null);
                server.execute(SubLevelMovingOriginSmoke::prepare);
            }
            require(failure == null, failure);
            require(++ticks < 1600, "Moving-origin smoke timed out");
            if (!ready || ++age < 100) return;
            if (!launchRequested) {
                launchRequested = true;
                server.execute(() -> {
                    try {
                        require(runtime.launch(structure, new Vec3(1, 0, 0), 20), "Launch rejected");
                        launched = true;
                    } catch (Throwable problem) {
                        failure = problem.toString();
                    }
                });
            }
            if (!launched) return;
            var index = SubLevelCollisionRuntime.index(mc.level).orElseThrow();
            var entry = index.entry(structure).orElseThrow();
            if (!walking) {
                require(age < 220, "Body never left its launch position");
                if (entry.pose().worldOrigin().x < origin.getX() + 8) return;
                walking = true;
                mc.player.setYRot(0);
                INPUT.forward = true;
            }
            require(++walkTicks < 100, "Old structure position blocked walking: " + mc.player.position());
            var oldBounds = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(5, 3, 2)));
            require(index.query(oldBounds).stream().noneMatch(candidate -> candidate.id().equals(structure)),
                    "Moving body retained its old collision index position");
            var actor = mc.player;
            INPUT.jump = false;
            if (!jumped && actor.getZ() >= origin.getZ() - 0.2) {
                INPUT.jump = true;
                jumped = true;
            }
            if (oldBounds.intersects(actor.getBoundingBox())) oldPositionTicks++;
            if (jumped) {
                jumpHeight = Math.max(jumpHeight, actor.getY() - origin.getY());
                if (!actor.onGround()) airborne++;
            }
            require(Math.abs(actor.getX() - origin.getX() - 2.5) < 0.15,
                    "Old structure position deflected player");
            require(actor.getY() >= origin.getY() - 0.05, "Player fell through the host floor");
            if (!used && actor.getZ() >= origin.getZ() + 1.2) {
                var eye = actor.getEyePosition();
                var leverState = mc.level.getBlockState(lever);
                var leverShape = leverState.getShape(mc.level, lever);
                require(!leverShape.isEmpty(), "Host lever has no outline: " + leverState);
                // A wall lever occupies the back of its cell; the cell center is empty.
                var target = leverShape.bounds().getCenter().add(Vec3.atLowerCornerOf(lever));
                require(SubLevelClientState.raycast(mc.level, eye, target) == null,
                        "Moving body retained a raycast hit at its old position");
                var hit = mc.level.clip(new ClipContext(eye, target,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, actor));
                if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(lever)) {
                    AcademyCraft.LOGGER.error("MOVING_ORIGIN_HOST_RAY_FAILED type={} hit_pos={} hit_location={}"
                                    + " eye={} target={} lever={} lever_state={} outline={}",
                            hit.getType(), hit.getBlockPos(), hit.getLocation(), eye, target,
                            lever, leverState, leverShape.bounds());
                }
                require(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(lever),
                        "Host lever behind the old position is not reachable");
                require(eye.distanceToSqr(hit.getLocation()) < 4.5 * 4.5, "Fixture exceeded normal interaction reach");
                require(mc.gameMode != null, "No client game mode");
                mc.gameMode.useItemOn(actor, InteractionHand.MAIN_HAND, hit);
                used = true;
            }
            if (actor.getZ() < origin.getZ() + 3.2) return;
            INPUT.forward = false;
            require(jumpHeight > 0.95 && airborne >= 8, "Old position blocked jumping: " + jumpHeight);
            require(oldPositionTicks >= 8, "Player did not traverse the former occupied volume");
            require(movingDuringWalk >= 15, "Body stopped before the interaction regression was exercised");
            if (!hostUsed) return;
            AcademyCraft.LOGGER.info("SUBLEVEL_MOVING_ORIGIN_PASSED real_client_input=true native_launch=true"
                            + " old_volume_ticks={} jump_height={} airborne_ticks={} moving_server_ticks={}"
                            + " stale_index=false stale_raycast=false host_lever_used=true",
                    oldPositionTicks, jumpHeight, airborne, movingDuringWalk);
            finish(mc, null);
        } catch (Throwable problem) {
            finish(mc, problem);
        }
    }

    private static void prepare() {
        try {
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null, "Missing moving-origin actor");
            var host = actor.level();
            int x = actor.getBlockX() + 48, z = actor.getBlockZ() + 32;
            origin = new BlockPos(x, host.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            for (var p : BlockPos.betweenClosed(origin.offset(-5, -1, -5), origin.offset(60, 6, 9))) {
                host.getChunkAt(p);
                boolean boundary = p.getY() == origin.getY() - 1
                        || p.getX() == x - 5 || p.getX() == x + 60
                        || p.getZ() == z - 5 || p.getZ() == z + 9;
                host.setBlock(p, (boundary ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            var positions = new ArrayList<BlockPos>();
            for (var p : BlockPos.betweenClosed(origin, origin.offset(4, 2, 1))) {
                host.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                positions.add(p.immutable());
            }
            var result = StructureTransactions.capture(host,
                    new StructureCaptureRequest(positions, positions.size(), 8, StructureCaptureRequest.ALLOW, true));
            require(result.succeeded(), "Moving-origin capture failed: " + result);
            structure = new SubLevelId(result.structure().orElseThrow().id());
            runtime = SubLevelServer.runtime(host).orElseThrow();
            lever = origin.offset(2, 1, 4);
            host.setBlock(lever.south(), Blocks.STONE.defaultBlockState(), 3);
            host.setBlock(lever, Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.WALL)
                    .setValue(LeverBlock.FACING, Direction.NORTH), 3);
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setGameMode(GameType.SURVIVAL);
            actor.getAbilities().flying = false;
            actor.onUpdateAbilities();
            actor.connection.teleport(x + 2.5, origin.getY() + 0.02, z - 2, 0, 10);
            ready = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_MOVING_ORIGIN_READY origin={} structure={} cells=30", origin, structure);
        } catch (Throwable problem) {
            failure = problem.toString();
            AcademyCraft.LOGGER.error("Moving-origin setup failed", problem);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void server(ServerTickEvent.Post event) {
        if (!ENABLED || event.getServer() != server || !walking || finished || failure != null) return;
        try {
            var motion = runtime.universe().motion(structure).orElseThrow();
            var linear = motion.linearVelocity();
            if (linear.x() * linear.x() + linear.y() * linear.y() + linear.z() * linear.z() > 0.25) {
                movingDuringWalk++;
            }
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null, "Moving-origin actor disconnected");
            hostUsed |= actor.level().getBlockState(lever).getValue(LeverBlock.POWERED);
        } catch (Throwable problem) {
            failure = problem.toString();
        }
    }

    private static void finish(Minecraft mc, Throwable problem) {
        if (finished) return;
        finished = true;
        INPUT.forward = false;
        INPUT.jump = false;
        if (mc.player != null && originalInput != null) mc.player.input = originalInput;
        if (problem != null) AcademyCraft.LOGGER.error("SUBLEVEL_MOVING_ORIGIN_FAILED", problem);
        mc.stop();
    }

    private static final class FixtureInput extends ClientInput {
        private boolean forward, jump;

        @Override
        public void tick() {
            keyPresses = new Input(forward, false, false, false, jump, false, false);
            moveVector = forward ? new Vec2(0, 1) : Vec2.ZERO;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
