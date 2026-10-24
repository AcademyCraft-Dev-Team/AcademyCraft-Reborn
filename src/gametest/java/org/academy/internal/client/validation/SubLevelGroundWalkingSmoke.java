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
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelRuntime;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.mixin.common.MixinServerGamePacketListenerNativeStructureAccess;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real client movement across ground-level host/structure seams and half-block steps. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelGroundWalkingSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelGroundWalkingSmoke");
    private static final FixtureInput INPUT = new FixtureInput();
    private static final List<SubLevelId> IDS = new ArrayList<>();
    private static final List<BlockPos> SOURCES = new ArrayList<>();
    private static final StringBuilder SAMPLES = new StringBuilder("tick,phase,x,y,z,on_ground\n");
    private static volatile MinecraftServer server;
    private static volatile UUID playerId;
    private static volatile SubLevelRuntime runtime;
    private static volatile BlockPos origin;
    private static volatile boolean ready;
    private static volatile String failure;
    private static volatile int phase;
    private static volatile int carryPulses;
    private static volatile int teleportGates;
    private static int ticks, age, grounded, groundSamples, serverTicks;
    private static double previousX, maximumBackward, maximumHeightError;
    private static ClientInput oldInput;
    private static boolean finished, startedWalking;

    private SubLevelGroundWalkingSmoke() {
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            if (server == null) {
                require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                        .startsWith("native-sublevel-ground-walk-"), "Ground walk requires a disposable copy");
                server = mc.getSingleplayerServer();
                require(server != null, "Ground walk requires an integrated server");
                playerId = mc.player.getUUID();
                oldInput = mc.player.input;
                mc.player.input = INPUT;
                mc.options.pauseOnLostFocus = false;
                mc.gui.setScreen(null);
                server.execute(SubLevelGroundWalkingSmoke::prepare);
            }
            require(failure == null, failure);
            require(++ticks < 1400, "Ground walk timeout phase=" + phase);
            if (!ready || SubLevelClientState.snapshots(mc.level).size() < 2) return;
            var actor = mc.player;
            age++;
            SAMPLES.append(ticks).append(',').append(phase).append(',').append(actor.getX()).append(',')
                    .append(actor.getY()).append(',').append(actor.getZ()).append(',')
                    .append(actor.onGround()).append('\n');
            if (phase == 0) {
                if (age < 120) return;
                for (var source : SOURCES) {
                    var observed = mc.level.getBlockState(source);
                    if (!observed.isAir()) {
                        var host = runtime.host();
                        server.execute(() -> AcademyCraft.LOGGER.error(
                                "GROUND_SOURCE_COMPARE pos={} client={} server={} full={} tracked={}",
                                source, observed, host.getBlockState(source), host.getChunkAt(source).getFullStatus(),
                                host.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(
                                        source.getX() >> 4, source.getZ() >> 4), false).size()));
                    }
                    require(observed.isAir(), "Captured source cell remains as a client ghost: "
                            + source + " state=" + observed);
                }
                previousX = actor.getX();
                actor.setYRot(-90);
                INPUT.forward = true;
                startedWalking = true;
                phase = 1;
                age = 0;
            } else if (phase == 1 || phase == 3) {
                double dx = actor.getX() - previousX;
                previousX = actor.getX();
                if (age > 5) maximumBackward = Math.max(maximumBackward, phase == 1 ? -dx : dx);
                require(actor.getY() >= origin.getY() + 0.95, "Player fell through ground-level structure");
                if (actor.getX() > origin.getX() + 0.7 && actor.getX() < origin.getX() + 6.5) {
                    maximumHeightError = Math.max(maximumHeightError, Math.abs(actor.getY() - origin.getY() - 1));
                    groundSamples++;
                    if (actor.onGround()) grounded++;
                }
                if (phase == 1 && actor.getX() >= origin.getX() + 15
                        || phase == 3 && actor.getX() <= origin.getX() - 3) {
                    INPUT.forward = false;
                    phase++;
                    age = 0;
                }
                require(age < 180, "Walking stuck at a seam/step: phase=" + phase + " position=" + actor.position());
            } else if (phase == 2 && age >= 15) {
                require(actor.getY() > origin.getY() + 1.95, "Player did not cross both half-block steps");
                actor.setYRot(90);
                previousX = actor.getX();
                INPUT.forward = true;
                phase = 3;
                age = 0;
            } else if (phase == 4 && age >= 20) {
                require(maximumBackward < 0.08, "Walking was repeatedly pulled backwards: " + maximumBackward);
                require(maximumHeightError < 0.08, "Ground-contact camera bounced: " + maximumHeightError);
                require(groundSamples >= 40 && grounded >= groundSamples * 0.80,
                        "Unstable supported walking: " + grounded + "/" + groundSamples);
                require(carryPulses >= 20, "Settling carry was not exercised");
                require(teleportGates == 0, "Support movement opened teleport acknowledgement gates: " + teleportGates);
                finish(mc, null);
            }
        } catch (Throwable problem) {
            finish(mc, problem);
        }
    }

    private static void prepare() {
        try {
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null, "Missing ground walk actor");
            var host = actor.level();
            int x = actor.getBlockX() + 48;
            int z = actor.getBlockZ() + 32;
            int y = host.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            origin = new BlockPos(x, y, z);
            // The lowest layer is a real host floor directly beneath the first physical body.
            // The second body rests on the next host layer, giving two successive half steps.
            for (var p : BlockPos.betweenClosed(origin.offset(-10, -1, -4), origin.offset(23, 6, 4))) {
                host.getChunkAt(p);
                host.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int xx = -10; xx <= 23; xx++) for (int zz = -4; zz <= 4; zz++) {
                host.setBlock(origin.offset(xx, -1, zz), Blocks.STONE.defaultBlockState(), 3);
                if (xx < 0 || xx >= 8) host.setBlock(origin.offset(xx, 0, zz), Blocks.STONE.defaultBlockState(), 3);
                if (xx >= 12) host.setBlock(origin.offset(xx, 1, zz), Blocks.STONE.defaultBlockState(), 3);
            }
            // The template may be near an ocean. Keep actual host terrain contact without
            // letting later fluid ticks refill the captured source cells and fake a ghost.
            for (int yy = 0; yy <= 6; yy++) {
                for (int xx = -10; xx <= 23; xx++) for (int zz : new int[]{-4, 4}) {
                    host.setBlock(origin.offset(xx, yy, zz), Blocks.STONE.defaultBlockState(), 3);
                }
                for (int zz = -4; zz <= 4; zz++) for (int xx : new int[]{-10, 23}) {
                    host.setBlock(origin.offset(xx, yy, zz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            capture(0, 7, 0, false);
            capture(8, 11, 1, true);
            runtime = SubLevelServer.runtime(host).orElseThrow();
            actor.setGameMode(GameType.SURVIVAL);
            actor.getAbilities().flying = false;
            actor.onUpdateAbilities();
            actor.connection.teleport(x - 3.5, y + 1.02, z + 0.5, -90, 15);
            ready = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_WALK_READY origin={} structures={} sources={}", origin, IDS, SOURCES.size());
        } catch (Throwable problem) {
            failure = problem.toString();
            AcademyCraft.LOGGER.error("Ground walking setup failed", problem);
        }
    }

    private static void capture(int x0, int x1, int y, boolean slab) {
        var host = server.getPlayerList().getPlayer(playerId).level();
        var source = new ArrayList<BlockPos>();
        for (int x = x0; x <= x1; x++) for (int z = -1; z <= 1; z++) {
            var p = origin.offset(x, y, z);
            host.setBlock(p, (slab ? Blocks.STONE_SLAB : Blocks.STONE).defaultBlockState(), 3);
            source.add(p);
        }
        var result = StructureTransactions.capture(host,
                new StructureCaptureRequest(source, source.size(), 10, StructureCaptureRequest.ALLOW, true));
        require(result.succeeded(), "Ground walk capture rejected: " + result);
        IDS.add(new SubLevelId(result.structure().orElseThrow().id()));
        SOURCES.addAll(source);
        for (var p : source) require(host.getBlockState(p).isAir(), "Captured source remains in host: " + p);
        var p = source.getFirst();
        AcademyCraft.LOGGER.info("GROUND_SOURCE_CAPTURE pos={} state={} full={} tracked={}",
                p, host.getBlockState(p), host.getChunkAt(p).getFullStatus(),
                host.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(
                        p.getX() >> 4, p.getZ() >> 4), false).size());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void server(ServerTickEvent.Post event) {
        if (!ENABLED || event.getServer() != server || !ready || failure != null || finished) return;
        try {
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null, "Ground walk actor disconnected");
            serverTicks++;
            if (startedWalking && ((MixinServerGamePacketListenerNativeStructureAccess) actor.connection)
                    .academy$awaitingPositionFromClient() != null) teleportGates++;
            if ((phase == 1 || phase == 3) && actor.getX() > origin.getX() - 0.2
                    && actor.getX() < origin.getX() + 8.2) {
                // A few millimetres of solver settling used to teleport the player every tick.
                // Exercise that exact branch deterministically while retaining native terrain.
                require(runtime.shift(IDS.getFirst(), new Vec3(0, serverTicks % 2 == 0 ? 0.003 : -0.003, 0)),
                        "Cannot exercise supported settling movement");
                carryPulses++;
            }
        } catch (Throwable problem) {
            failure = problem.toString();
            AcademyCraft.LOGGER.error("Ground walking server verification failed", problem);
        }
    }

    private static void finish(Minecraft mc, Throwable problem) {
        if (finished) return;
        finished = true;
        INPUT.forward = false;
        if (oldInput != null && mc.player != null) mc.player.input = oldInput;
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("ground-walk-samples.csv"), SAMPLES);
        } catch (Exception output) {
            if (problem == null) problem = output;
            else problem.addSuppressed(output);
        }
        if (problem == null) {
            AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_WALK_PASSED real_client_input=true host_floor=true"
                            + " source_air_server_client=true half_steps=2 directions=2 carry_pulses={}"
                            + " teleport_gates={} backward={} height_error={} grounded={}/{}",
                    carryPulses, teleportGates, maximumBackward, maximumHeightError, grounded, groundSamples);
        } else {
            AcademyCraft.LOGGER.error("SUBLEVEL_GROUND_WALK_FAILED phase={} carry={} teleport_gates={}",
                    phase, carryPulses, teleportGates, problem);
        }
        mc.stop();
    }

    private static final class FixtureInput extends ClientInput {
        private boolean forward;

        @Override
        public void tick() {
            keyPresses = new Input(forward, false, false, false, false, false, false);
            moveVector = forward ? new Vec2(0, 1) : Vec2.ZERO;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
