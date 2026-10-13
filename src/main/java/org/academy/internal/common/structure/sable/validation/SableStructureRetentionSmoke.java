package org.academy.internal.common.structure.sable.validation;

import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.space.NativeStructureManifest;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only preservation check on a complete disposable copy of a passed native world. */
public final class SableStructureRetentionSmoke {
    private static SableStructureRetentionSmoke active;
    private static boolean initialized;
    private final MinecraftServer server;
    private final Path directory, metadata;
    private final Map<String, byte[]> before;
    private final List<NativeStructureManifest.Entry> entries;
    private final StringBuilder report = new StringBuilder("actions=server_fixture\nactual_keyboard_actions=false\n");
    private int ticks;
    private boolean legacyInvoked, completedTicks, orderlyStop;
    private volatile boolean resultWritten;
    private volatile String failure;

    private SableStructureRetentionSmoke(MinecraftServer server) throws IOException {
        this.server = server;
        directory = SableStructureSmoke.verifyDirectory(server, "server");
        require(!Files.exists(directory.resolve("sable-retention-result.txt")), "Retention result already exists");
        metadata = server.getWorldPath(LevelResource.ROOT).resolve("data/academy/native_structures").toRealPath();
        require(metadata.startsWith(directory), "Retention metadata escaped its isolated world");
        before = snapshot();
        var baseline = JsonParser.parseString(Files.readString(directory.resolve("sable-retention-baseline.json"))).getAsJsonObject();
        require(baseline.get("format").getAsString().equals("academy-sable-retention-v1")
                && baseline.get("completeWorldCopyVerified").getAsBoolean(), "Missing complete-world copy evidence");
        var records = baseline.getAsJsonArray("metadata");
        require(records.size() == before.size() && !before.isEmpty(), "Metadata file set differs from external baseline");
        for (var record : records) {
            var item = record.getAsJsonObject(); var bytes = before.get(item.get("path").getAsString());
            require(bytes != null && bytes.length == item.get("bytes").getAsLong()
                    && sha(bytes).equalsIgnoreCase(item.get("sha256").getAsString()), "Metadata changed before fixture startup");
        }
        var manifest = new NativeStructureManifest(metadata); // Existing directory; never persist or mutate this read-only view.
        require(manifest.healthy() && !manifest.entries().isEmpty(), "Source needs real healthy native structure entries");
        entries = manifest.entries();
        require(NativeStructureRuntime.existing(server).isEmpty(), "Mixed startup activated the native runtime before legacy access");
        report.append("complete_world_copy_baseline_verified=true\ninitial_native_runtime_absent=true\n")
                .append("stored_structures=").append(entries.size()).append("\nmetadata_files=").append(before.size()).append('\n');
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("sable-retention-fallback").unstarted(() -> {
            if (!resultWritten) writeResult(false, "process_exited_before_retention_completion");
        }));
    }

    public static synchronized void initialize() {
        if (initialized) return;
        require(Boolean.getBoolean("academy.sable.retention") && !Boolean.getBoolean("academy.sable.smoke"), "Retention must be exclusive opt-in");
        NeoForge.EVENT_BUS.register(SableStructureRetentionSmoke.class); initialized = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        try {
            if (active == null) active = new SableStructureRetentionSmoke(event.getServer());
            if (active.server == event.getServer() && !active.completedTicks && active.failure == null) active.advance();
        } catch (Throwable error) {
            if (active == null) throw new IllegalStateException("Cannot initialize retention fixture", error);
            active.failure = error.toString();
            AcademyCraft.LOGGER.error("SABLE_RETENTION_FAILED ticks={}", active.ticks, error);
            active.server.halt(false);
        }
    }

    private void advance() throws IOException {
        ticks++;
        assertBlocked(); verifyUnchanged();
        if (ticks == 20) {
            require(NativeStructureRuntime.existing(server).isEmpty(), "Public mixed queries created a native runtime");
            var disabled = NativeStructureRuntime.backend(server); // Same internal entry used by legacy handlers; no packet is forged.
            legacyInvoked = true;
            require(disabled.createEmpty(server.overworld(), StructurePose.identity(Vec3.ZERO)).isEmpty(), "Legacy create escaped migration gate");
            for (var entry : entries) require(disabled.load(entry.reference()).isEmpty()
                    && disabled.resolve(entry.reference()).isEmpty(), "Legacy load activated a retained native structure");
            boolean rejected = false;
            try { StructurePhysics.openRegion(server.overworld(), new AABB(0, 160, 0, 8, 168, 8)); }
            catch (IllegalStateException error) { rejected = error.getMessage().contains("disabled while Sable"); }
            require(rejected, "Opening a native region was not rejected by the Sable gate");
            verifyUnchanged();
            report.append("legacy_internal_entry_invoked=true\nlegacy_create_load_resolve_rejected=true\nnative_region_open_rejected=true\n");
        }
        if (ticks == 120) {
            require(legacyInvoked, "Legacy gate case was skipped"); completedTicks = true;
            report.append("consecutive_ticks=120\nstorage_simulation_remained_disabled=true\nold_projection_absent=true\n")
                    .append("metadata_unchanged_during_ticks=true\n");
            server.halt(false);
        }
    }

    private void assertBlocked() {
        var status = StructureSpaces.status(server);
        require(status.mode() == StructureBackendStatus.Mode.MIGRATION_REQUIRED
                && status.storedNativeStructures() == entries.size(), "Existing world lost its migration-required status: " + status);
        var backend = StructureSpaces.backend(server);
        require(backend.createEmpty(server.overworld(), StructurePose.identity(Vec3.ZERO)).isEmpty(), "Mixed backend created a native structure");
        for (var entry : entries) {
            require(backend.load(entry.reference()).isEmpty() && StructureSpaces.resolve(server, entry.reference()).isEmpty(), "Old structure activated");
            require(StructurePhysics.activeRegion(server.overworld(), entry.reference()).isEmpty(), "Old structure acquired a physics region");
        }
        for (var level : server.getAllLevels()) {
            if (NativeStructureDimensions.isStorageLevel(level.dimension())) require(!NativeStructureRuntime.canTickStorageLevel(level), "Native storage started ticking");
            for (var entity : level.getAllEntities()) require(!(entity instanceof NativeStructureProjectionEntity), "Old native projection activated");
        }
    }

    private Map<String, byte[]> snapshot() throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        try (var paths = Files.walk(metadata)) {
            for (var path : paths.sorted().toList()) {
                require(path.toRealPath().equals(path.toAbsolutePath().normalize()) && !Files.isSymbolicLink(path), "Metadata contains a link");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= NativeStructureManifest.MAX_PAYLOAD_BYTES,
                        "Unexpected retention metadata entry: " + path);
                result.put(metadata.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return result;
    }

    private void verifyUnchanged() throws IOException {
        var current = snapshot();
        require(current.keySet().equals(before.keySet()), "Metadata files were added or removed");
        for (var entry : before.entrySet()) require(Arrays.equals(entry.getValue(), current.get(entry.getKey())), "Metadata bytes changed: " + entry.getKey());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        var probe = active;
        if (probe == null || probe.server != event.getServer()) return;
        probe.orderlyStop = true;
        try { probe.verifyUnchanged(); probe.report.append("metadata_unchanged_at_stopping=true\n"); }
        catch (Throwable error) { probe.failure = error.toString(); }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var probe = active;
        if (probe == null || probe.server != event.getServer()) return;
        Thread.ofPlatform().daemon().name("sable-retention-exit").start(() -> {
            try { probe.server.getRunningThread().join(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            boolean passed = probe.completedTicks && probe.orderlyStop && probe.failure == null && !probe.server.isCurrentlySaving();
            try { probe.verifyUnchanged(); probe.report.append("metadata_unchanged_after_server_thread=true\n"); }
            catch (Throwable error) { probe.failure = error.toString(); passed = false; }
            probe.writeResult(passed, probe.failure);
            System.out.println("SABLE_RETENTION_SERVER_THREAD_TERMINATED exit_status=" + (passed ? 0 : 1));
            ServerLifecycleHooks.handleExit(passed ? 0 : 1);
        });
    }

    private synchronized void writeResult(boolean passed, String error) {
        if (resultWritten) return;
        try {
            Files.writeString(directory.resolve("sable-retention-result.txt"), (passed ? "PASSED\n" : "FAILED\nfailure=" + error + "\n") + report,
                    StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("sable-retention-exit.txt"), "exit_status=" + (passed ? 0 : 1)
                    + "\nserver_thread_terminated=" + !server.getRunningThread().isAlive() + "\nnormal_stopping_event=" + orderlyStop + "\n",
                    StandardOpenOption.CREATE_NEW);
            resultWritten = true;
        } catch (IOException errorWriting) { System.err.println("SABLE_RETENTION_RESULT_FAILED " + errorWriting); }
    }

    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
