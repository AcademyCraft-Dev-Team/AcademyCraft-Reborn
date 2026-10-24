package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Process interruption exists only in the opt-in game-test source set and an isolated copied save. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureRestorationCrashSmoke {
    private static final String MODE = System.getProperty("academy.structureRestorationSmokeMode", "functional");
    private static final boolean VERIFY = Boolean.getBoolean("academy.structureRestorationSmoke") && MODE.equals("recover_verify");
    private static JsonObject evidence;
    private static StructureRef stable;
    private static BlockPos target;
    private static String failure;
    private static int preTickObservations, rawSourceObservations, postTickIsolationObservations, blockedNewAttempts, stableTicks;

    private StructureRestorationCrashSmoke() {}

    static boolean crashMode() { return MODE.equals("crash_targets_saved") || MODE.equals("crash_commit_saved")
            || MODE.equals("admin_crash_targets_saved") || MODE.equals("admin_crash_commit_saved"); }

    static void arm(ServerPlayer player, StructureRef reference, List<StructureGridRestoration.Placement> placements) {
        check(Boolean.getBoolean("academy.structureRestorationSmoke") && crashMode(), "crash mode is not enabled");
        var directory = isolatedDirectory(player.level().getServer());
        check(placements.size() == 1, "fault fixture requires a single owned cell");
        var destination = placements.getFirst().targetPosition();
        var metadata = LiveStructureProvenance.metadata(player.level().getServer(), reference).orElse(null);
        var runtime = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().runtimeId();
        var originalPayload = StructurePayloadFixtureSupport.ENABLED
                ? StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().world().read(BlockPos.ZERO) : null;
        var expected = MODE.endsWith("crash_targets_saved") ? MarkedGridRestoration.CheckpointPhase.TARGET_SAVED
                : MarkedGridRestoration.CheckpointPhase.COMMIT_SAVED;
        int exitCode = expected == MarkedGridRestoration.CheckpointPhase.TARGET_SAVED ? 81 : 82;
        MarkedGridRestoration.observeCheckpoints(player.level().getServer(), checkpoint -> {
            if (checkpoint.host() != player.level() || !checkpoint.reference().equals(reference) || checkpoint.phase() != expected) return;
            check(isolatedDirectory(player.level().getServer()).equals(directory), "fault directory changed");
            var saved = new LinkedHashMap<String, Object>();
            saved.put("phase_hit", checkpoint.phase().name()); saved.put("expected_exit_code", exitCode);
            saved.put("id", reference.id().toString()); saved.put("backend", reference.backend().toString());
            saved.put("dimension", reference.dimension().toString()); saved.put("operation", checkpoint.operationId().toString());
            saved.put("marked", metadata != null);
            if (StructurePayloadFixtureSupport.ENABLED) {
                saved.put("payload_digest", org.academy.internal.common.structure.transaction.StructureCellPayload.digest(originalPayload));
                saved.put("payload_state", originalPayload.state().toString());
            }
            if (metadata != null) saved.put("creation", metadata.creationOperationId().toString());
            saved.put("owner", player.getUUID().toString());
            saved.put("runtime", runtime.toString()); saved.put("target", List.of(destination.getX(), destination.getY(), destination.getZ()));
            saved.put("world", player.level().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
            var encoded = new GsonBuilder().setPrettyPrinting().create().toJson(saved).getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(directory.resolve("structure-restoration-crash.json"), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot force crash evidence", error); }
            AcademyCraft.LOGGER.warn("STRUCTURE_RESTORATION_INTENTIONAL_HALT phase={} operation={} exit_code={} copied_save={}",
                    checkpoint.phase(), checkpoint.operationId(), exitCode, directory);
            Runtime.getRuntime().halt(exitCode);
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void started(ServerStartedEvent event) {
        if (!VERIFY) return;
        try {
            var directory = isolatedDirectory(event.getServer());
            evidence = JsonParser.parseString(Files.readString(directory.resolve("structure-restoration-crash.json"))).getAsJsonObject();
            check(evidence.get("world").getAsString().equals(event.getServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize().toString()), "recovery verifier is not using the interrupted save");
            stable = new StructureRef(UUID.fromString(evidence.get("id").getAsString()),
                    Identifier.parse(evidence.get("dimension").getAsString()), Identifier.parse(evidence.get("backend").getAsString()), 0);
            var position = evidence.getAsJsonArray("target");
            target = new BlockPos(position.get(0).getAsInt(), position.get(1).getAsInt(), position.get(2).getAsInt());
            check(evidence.get("phase_hit").getAsString().equals("TARGET_SAVED") || committed(), "unknown fault checkpoint");
        } catch (Exception error) { failure = error.toString(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preTick(ServerTickEvent.Pre event) {
        if (!VERIFY || stable == null || failure != null) return;
        try {
            preTickObservations++;
            var server = event.getServer();
            for (var host : server.getAllLevels()) {
                if (!host.dimension().identifier().equals(stable.dimension())) continue;
                var live = LiveStructureProvenance.liveReference(server, stable);
                if (committed()) {
                    check(live.isEmpty(), "committed source became publicly selectable during recovery");
                    if (SubLevelStructureRefs.isSubLevel(stable)) {
                        var id = SubLevelStructureRefs.id(stable).orElseThrow();
                        var runtime = SubLevelServer.runtime(host).orElse(null);
                        if (runtime != null && runtime.get(id).isPresent()) {
                            rawSourceObservations++;
                            check(runtime.universe().motion(id).isEmpty(), "committed source acquired a native solver body");
                            check(runtime.collisionIndex().entry(id).isEmpty(), "committed source acquired native collision");
                        }
                    } else if (Sable.rawPresent(host, stable)) rawSourceObservations++;
                }
            }
        } catch (RuntimeException error) { failure = error.toString(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void postTick(ServerTickEvent.Post event) {
        if (!VERIFY || stable == null || !committed() || SubLevelStructureRefs.isSubLevel(stable) || failure != null) return;
        try {
            for (var host : event.getServer().getAllLevels()) {
                if (host.dimension().identifier().equals(stable.dimension()) && Sable.rawPresent(host, stable)) {
                    Sable.checkDetached(host, stable); postTickIsolationObservations++;
                }
            }
        } catch (RuntimeException error) { failure = error.toString(); }
    }

    /** Prevent a fresh automatic attempt from disguising rollback as committed recovery. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectRecoveryObservation(StructureRestorationEvent event) {
        if (VERIFY && stable != null && event.reference().id().equals(stable.id())
                && event.reference().backend().equals(stable.backend()) && event.reference().dimension().equals(stable.dimension())) {
            blockedNewAttempts++;
            event.setCanceled(true);
        }
    }

    static void prepareVerifier(ServerPlayer player) {
        check(failure == null && stable != null && evidence != null, "recovery setup failed: " + failure);
        check(player.level().dimension().identifier().equals(stable.dimension()), "recovery host mismatch");
        check(player.getUUID().toString().equals(evidence.get("owner").getAsString()), "recovery owner identity changed");
        player.level().getChunkAt(target);
        player.connection.teleport(target.getX() - 3, target.getY() + 3, target.getZ() - 3, 0, 0);
    }

    private static boolean owned(StructureBlockData data) {
        if (!evidence.has("payload_digest")) return data.state().is(Blocks.IRON_BLOCK);
        return data.state().toString().equals(evidence.get("payload_state").getAsString())
                && org.academy.internal.common.structure.transaction.StructureCellPayload.digest(data).equals(evidence.get("payload_digest").getAsString());
    }

    static Optional<Map<String, Object>> verify(ServerPlayer player) {
        check(failure == null, "recovery visibility failed: " + failure);
        var server = player.level().getServer();
        check(LiveStructureProvenance.healthy(server), "recovery provenance ledger unavailable");
        var live = LiveStructureProvenance.liveReference(server, stable);
        var metadata = LiveStructureProvenance.metadata(server, stable);
        boolean targetOwned = owned(org.academy.internal.common.structure.transaction.StructureCellPayload.read(player.level(), target));
        boolean sourceOwned = live.flatMap(ref -> StructureSpaces.resolve(server, ref))
                .map(handle -> owned(handle.world().read(BlockPos.ZERO))).orElse(false);
        var status = MarkedGridRestoration.INSTANCE.status(player.level(), new StructureGridRestoration.RestorationActor(
                Optional.of(player.getUUID()), MarkedGridRestoration.POLICY_ID), UUID.fromString(evidence.get("operation").getAsString()));
        boolean done;
        if (committed()) {
            done = live.isEmpty() && metadata.isEmpty() && targetOwned && status.value()
                    .filter(progress -> progress.phase() == StructureGridRestoration.Phase.RESTORED).isPresent();
        } else {
            boolean marked = !evidence.has("marked") || evidence.get("marked").getAsBoolean();
            done = sourceOwned && player.level().getBlockState(target).isAir()
                    && (marked ? metadata.isPresent() && metadata.get().creationOperationId().toString().equals(evidence.get("creation").getAsString()) : metadata.isEmpty())
                    && status.value().isEmpty();
            if (done) {
                var reference = live.orElseThrow();
                var handle = StructureSpaces.resolve(server, reference).orElseThrow();
                done = !handle.runtimeId().toString().equals(evidence.get("runtime").getAsString())
                        && StructureDynamics.motion(player.level(), reference).isPresent();
                if (SubLevelStructureRefs.isSubLevel(reference)) done &= SubLevelCollisionRuntime.index(player.level())
                        .flatMap(index -> index.entry(SubLevelStructureRefs.id(reference).orElseThrow())).isPresent();
            }
        }
        stableTicks = done ? stableTicks + 1 : 0;
        if (stableTicks < 40) return Optional.empty();
        var report = new LinkedHashMap<String, Object>();
        report.put("case", "interrupted_restoration_" + evidence.get("phase_hit").getAsString());
        report.put("passed", true); report.put("phase_hit", evidence.get("phase_hit").getAsString());
        report.put("source_copies", sourceOwned ? 1 : 0); report.put("target_copies", targetOwned ? 1 : 0);
        report.put("pre_tick_visibility_observations", preTickObservations); report.put("quarantined_raw_source_observations", rawSourceObservations);
        report.put("sable_post_tick_detachment_observations", postTickIsolationObservations);
        report.put("new_automatic_attempts_blocked", blockedNewAttempts); report.put("verified_stable_ticks", stableTicks);
        report.put("payload_verified", evidence.has("payload_digest"));
        report.put("receipt", status.toString()); report.put("ordinary_shutdown_used", false);
        return Optional.of(report);
    }

    static String backend() { return stable.backend().toString(); }
    private static boolean committed() { return evidence != null && evidence.get("phase_hit").getAsString().equals("COMMIT_SAVED"); }
    private static Path isolatedDirectory(MinecraftServer server) {
        var configured = System.getProperty("academy.structureRestorationSmokeDirectory");
        check(configured != null, "explicit isolated directory is required for interruption tests");
        var path = Path.of(configured).toAbsolutePath().normalize();
        check(path.getFileName().toString().startsWith("native-sublevel-direct-smoke"), "refusing interruption outside isolated fixture");
        check(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().startsWith(path.resolve("saves")),
                "refusing interruption of a save outside the isolated fixture");
        return path;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class Sable {
        static boolean rawPresent(net.minecraft.server.level.ServerLevel host, StructureRef reference) {
            var container = org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(host);
            return container != null && container.getSubLevel(reference.id()) != null;
        }
        static void checkDetached(net.minecraft.server.level.ServerLevel host, StructureRef reference) {
            var container = org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(host);
            var body = container.getSubLevel(reference.id());
            check(org.academy.internal.common.structure.sable.SableRestorationIsolation.detached(body),
                    "committed source was not detached before the Sable simulation tick");
            check(container.getAllSubLevels().stream().noneMatch(value -> value.getUniqueId().equals(reference.id())),
                    "committed source remained in Sable public enumeration");
        }
    }
}
