package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.academy.internal.common.sublevel.SubLevelId;

import java.util.ArrayList;
import java.util.UUID;

/** Negative collision regression: walk and jump inside a hollow body's outer bounding box. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelEmptySpaceSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelEmptySpaceSmoke");
    private static final FixtureInput INPUT = new FixtureInput();
    private static volatile MinecraftServer server;
    private static volatile UUID playerId;
    private static volatile SubLevelId structure;
    private static volatile BlockPos origin;
    private static volatile String failure;
    private static volatile boolean ready;
    private static boolean finished, started, jumped;
    private static ClientInput originalInput;
    private static int ticks, age, insideOuterBounds, airborne;
    private static double jumpHeight;

    private SubLevelEmptySpaceSmoke() {
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            if (server == null) {
                require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                                .startsWith("native-sublevel-empty-space-"),
                        "Empty-space smoke requires a disposable game directory");
                server = mc.getSingleplayerServer();
                require(server != null, "Empty-space smoke requires an integrated server");
                playerId = mc.player.getUUID();
                originalInput = mc.player.input;
                mc.player.input = INPUT;
                mc.options.pauseOnLostFocus = false;
                mc.gui.setScreen(null);
                server.execute(SubLevelEmptySpaceSmoke::prepare);
            }
            require(failure == null, failure);
            require(++ticks < 1200, "Empty-space smoke timed out");
            if (!ready || SubLevelClientState.snapshots(mc.level).isEmpty()) return;
            if (++age < 100) return;
            var actor = mc.player;
            if (!started) {
                started = true;
                actor.setYRot(0);
                INPUT.forward = true;
            }
            INPUT.jump = false;
            if (!jumped && actor.getZ() >= origin.getZ() + 0.8) {
                jumped = true;
                INPUT.jump = true;
            }
            var entry = SubLevelCollisionRuntime.index(mc.level).orElseThrow().entry(structure).orElseThrow();
            if (entry.worldBounds().intersects(actor.getBoundingBox())) insideOuterBounds++;
            if (jumped) {
                jumpHeight = Math.max(jumpHeight, actor.getY() - origin.getY());
                if (!actor.onGround()) airborne++;
            }
            require(Math.abs(actor.getX() - origin.getX() - 2.5) < 0.15, "Invisible side contact deflected corridor movement");
            require(actor.getY() >= origin.getY() - 0.05, "Player fell through host floor");
            require(age < 200, "Invisible structure boundary blocked the corridor: " + actor.position());
            if (actor.getZ() < origin.getZ() + 7) return;
            INPUT.forward = false;
            require(jumpHeight > 0.95 && airborne >= 8, "Invisible ceiling or support blocked jumping: " + jumpHeight);
            require(insideOuterBounds >= 15, "Player did not traverse the body's empty outer bounds");
            AcademyCraft.LOGGER.info("SUBLEVEL_EMPTY_SPACE_PASSED real_client_input=true"
                            + " inside_structure_aabb_ticks={} jump_height={} airborne_ticks={} hollow_corridor=true",
                    insideOuterBounds, jumpHeight, airborne);
            finish(mc, null);
        } catch (Throwable problem) {
            finish(mc, problem);
        }
    }

    private static void prepare() {
        try {
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null, "Missing empty-space actor");
            var host = actor.level();
            int x = actor.getBlockX() + 48, z = actor.getBlockZ() + 32;
            origin = new BlockPos(x, host.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            for (var p : BlockPos.betweenClosed(origin.offset(-5, -1, -5), origin.offset(9, 6, 13))) {
                host.getChunkAt(p);
                boolean boundary = p.getY() == origin.getY() - 1
                        || p.getX() == x - 5 || p.getX() == x + 9
                        || p.getZ() == z - 5 || p.getZ() == z + 13;
                host.setBlock(p, (boundary ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            var positions = new ArrayList<BlockPos>();
            for (int xx : new int[]{0, 4}) for (int yy = 0; yy < 4; yy++) for (int zz = 0; zz < 5; zz++) {
                var p = origin.offset(xx, yy, zz);
                host.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                positions.add(p);
            }
            var result = StructureTransactions.capture(host,
                    new StructureCaptureRequest(positions, positions.size(), 8, StructureCaptureRequest.ALLOW, true));
            require(result.succeeded(), "Empty frame capture failed: " + result);
            structure = new SubLevelId(result.structure().orElseThrow().id());
            actor.setGameMode(GameType.SURVIVAL);
            actor.getAbilities().flying = false;
            actor.onUpdateAbilities();
            actor.connection.teleport(x + 2.5, origin.getY() + 0.02, z - 2, 0, 10);
            ready = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_EMPTY_SPACE_READY origin={} structure={} cells=40", origin, structure);
        } catch (Throwable problem) {
            failure = problem.toString();
            AcademyCraft.LOGGER.error("Empty-space setup failed", problem);
        }
    }

    private static void finish(Minecraft mc, Throwable problem) {
        if (finished) return;
        finished = true;
        INPUT.forward = false;
        INPUT.jump = false;
        if (mc.player != null && originalInput != null) mc.player.input = originalInput;
        if (problem != null) AcademyCraft.LOGGER.error("SUBLEVEL_EMPTY_SPACE_FAILED", problem);
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
