package org.academy.validation.parity;

import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Backend-neutral pilot measurement, deliberately separate from H gameplay acceptance. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class StructureParityProbe {
    private static final String MODE = System.getProperty("academy.structureParity", "");
    private static final BlockPos BASE = new BlockPos(0, 192, 0);
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static final List<Map<String, Object>> samples = new ArrayList<>();
    private static final List<BlockPos> cells = new ArrayList<>();
    private static MinecraftServer server;
    private static ServerLevel host;
    private static Entity actor;
    private static StructureRef reference;
    private static UUID transaction, control = UUID.randomUUID();
    private static Vec3 baseline, launchPosition, expectedCenter;
    private static StructurePhysicsControl anchor;
    private static long tickStart, captureStart, launchStart;
    private static int age, lastTick = -1, activeTicks, reachedAt = -1, hoverValidTicks, hoverStreak, longestHover;
    private static boolean captured, launched, done;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (MODE.isEmpty()) return;
        server = event.getServer();
        host = server.overworld();
        try {
            String actual = StructureSpaces.status(server).mode().name();
            if (!actual.equals(MODE.equals("local") ? "NATIVE" : "SABLE")) throw new IllegalStateException(actual);
            result.put("backend", MODE);
            result.put("fixture", System.getProperty("academy.structureParity.fixture"));
            result.put("scope", "dedicated-server/shared-API; no client, no H key path, no JFR");
            result.put("java", System.getProperty("java.runtime.version"));
            for (int x = -2; x <= 4; x++) for (int z = -2; z <= 2; z++) {
                host.setChunkForced(x, z, true);
                host.getChunk(x, z);
            }
            for (int x = -4; x <= 12; x++) for (int z = -4; z <= 12; z++) for (int y = -4; y <= 1; y++) {
                host.setBlock(BASE.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            int offset = result.get("fixture").equals("floating") ? 10 : 0;
            for (String line : Files.readAllLines(Path.of(System.getProperty("academy.structureParity.cells")))) {
                String[] xyz = line.split(",");
                BlockPos pos = BASE.offset(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]) + offset, Integer.parseInt(xyz[2]));
                cells.add(pos);
                host.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
            actor = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
            if (actor == null) throw new IllegalStateException("actor");
            actor.setPos(-3, 200, -3);
            actor.setNoGravity(true);
            if (!host.addFreshEntity(actor)) throw new IllegalStateException("actor_add");
            result.put("blocks", cells.size());
            expectedCenter = new Vec3(cells.stream().mapToDouble(p -> p.getX() + .5).average().orElseThrow(),
                    cells.stream().mapToDouble(p -> p.getY() + .5).average().orElseThrow(),
                    cells.stream().mapToDouble(p -> p.getZ() + .5).average().orElseThrow());
            result.put("expected_source_center", expectedCenter.toString());
            result.put("cell_sha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of(System.getProperty("academy.structureParity.cells"))))));
            AcademyCraft.LOGGER.info("STRUCTURE_PARITY_READY {}", result);
        } catch (Exception failure) { finish(failure); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event) {
        if (MODE.isEmpty() || server != event.getServer() || done) return;
        tickStart = System.nanoTime();
        try {
            if (anchor != null && !launched && !StructureDirectControl.submit(host, reference, actor, control, anchor)) {
                throw new IllegalStateException("control_submit");
            }
        } catch (Exception failure) { finish(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void post(ServerTickEvent.Post event) {
        if (MODE.isEmpty() || server != event.getServer() || done || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        try {
            long beforeProbe = System.nanoTime();
            age++;
            String phase = age <= 60 ? "warmup" : reference == null ? "capture" : launched ? "flight" : "lift";
            var row = new LinkedHashMap<String, Object>();
            row.put("age", age); row.put("phase", phase);
            if (age == 61) {
                captureStart = System.nanoTime();
                var receipt = StructureTransactions.capture(host, new StructureCaptureRequest(cells, 128, 24, StructureCaptureRequest.ALLOW, true));
                result.put("capture_call_ms", (System.nanoTime() - captureStart) / 1e6);
                transaction = receipt.transactionId();
                accept(receipt);
            } else if (transaction != null && !captured) {
                accept(StructureTransactions.poll(server, transaction).orElseThrow());
            }
            if (captured && anchor == null) {
                var motion = StructureDynamics.motion(host, reference);
                if (motion.isPresent() && StructureDirectControl.acquire(host, reference, actor, control) == StructureDirectControl.Status.ACTIVE) {
                    var state = motion.get();
                    baseline = state.pose().localToWorldPoint(state.localCenterOfMass());
                    result.put("mass", state.mass());
                    result.put("baseline", baseline.toString());
                    result.put("alignment_error", baseline.distanceTo(expectedCenter));
                    result.put("alignment_valid", baseline.distanceTo(expectedCenter) <= .01);
                    result.put("initial_pose", state.pose().toString());
                    result.put("local_center", state.localCenterOfMass().toString());
                    // Same acceleration response and mass-normalized force ceiling in both solvers.
                    double stiffness = state.mass() * 30;
                    anchor = new StructurePhysicsControl.Anchor(state.localCenterOfMass(),
                            baseline.add(0, 3 + state.mass() * 9.81 / stiffness, 0), Vec3.ZERO,
                            stiffness, state.mass() * 36 / 2.25, state.mass() * 30);
                }
            }
            if (anchor != null) {
                activeTicks++;
                var state = StructureDynamics.motion(host, reference).orElseThrow();
                var center = state.pose().localToWorldPoint(state.localCenterOfMass());
                double rise = center.y - baseline.y;
                row.put("active_tick", activeTicks); row.put("rise", rise);
                row.put("speed", state.linearVelocity().length());
                if (!launched && Math.abs(rise - 3) <= .1) {
                    if (reachedAt < 0) reachedAt = activeTicks;
                    hoverValidTicks++;
                    longestHover = Math.max(longestHover, ++hoverStreak);
                } else if (!launched) hoverStreak = 0;
                if (activeTicks == 260) {
                    StructureDirectControl.release(reference, control);
                    launched = StructureBackendRouter.launch(host, reference, new Vec3(1, 0, 0), 50);
                    if (!launched) throw new IllegalStateException("launch_refused");
                    launchPosition = center; launchStart = System.nanoTime();
                    result.put("launch_speed", StructureDynamics.motion(host, reference).orElseThrow().linearVelocity().length());
                    result.put("reached_tick", reachedAt);
                    result.put("hover_valid_ticks", hoverValidTicks);
                    result.put("longest_hover_streak", longestHover);
                    result.put("prelaunch_rise", rise);
                }
                if (activeTicks == 280) {
                    result.put("flight_20tick_dx", center.x - launchPosition.x);
                    result.put("flight_20tick_seconds", (System.nanoTime() - launchStart) / 1e9);
                }
            }
            row.put("server_tick_ms", (beforeProbe - tickStart) / 1e6);
            row.put("probe_ms", (System.nanoTime() - beforeProbe) / 1e6);
            samples.add(row);
            if (activeTicks >= 280) finish(null);
            else if (age > 600) finish(new IllegalStateException("deadline"));
        } catch (Exception failure) { finish(failure); }
    }

    private static void accept(StructureCaptureResult receipt) {
        if (receipt.pending()) return;
        if (!receipt.succeeded()) throw new IllegalStateException(receipt.toString());
        captured = true; reference = receipt.structure().orElseThrow();
        result.put("capture_ready_ms", (System.nanoTime() - captureStart) / 1e6);
        result.put("captured_blocks", receipt.affectedBlocks());
    }

    private static void finish(Exception failure) {
        if (done) return;
        done = true;
        result.put("measurement_status", failure == null ? "COMPLETE" : "FAILED");
        result.put("lift_status", reachedAt >= 0 && longestHover >= 200 ? "PASSED" : "FAILED");
        result.put("samples", samples);
        if (failure != null) { result.put("error", failure.toString()); AcademyCraft.LOGGER.error("STRUCTURE_PARITY_FAILED", failure); }
        try { Files.writeString(Path.of("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { AcademyCraft.LOGGER.error("STRUCTURE_PARITY_WRITE_FAILED", error); }
        AcademyCraft.LOGGER.info("STRUCTURE_PARITY_DONE measurement={} lift={}", result.get("measurement_status"), result.get("lift_status"));
        if (server != null) server.halt(false);
    }
}
