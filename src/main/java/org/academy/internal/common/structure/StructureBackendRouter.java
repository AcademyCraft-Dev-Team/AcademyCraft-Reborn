package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.physics.NativeStructureTraction;
import org.academy.internal.common.structure.space.NativeStructureManifest;
import org.academy.internal.common.structure.space.NativeStructureQueries;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Optional linkage is confined to a bridge constructed after the installed artifact is verified. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class StructureBackendRouter {
    public static final Identifier SABLE_ID = AcademyCraft.academy("sable");
    public static final String SABLE_VERSION = "2.0.6-port.26.2.0.70";
    public static final String SABLE_SHA256 = "21a22bcd6b0dd5bc742472e332f7e8a17dd83cbbd471ebe97087e153acc206ca";
    private static final Map<MinecraftServer, Selection> SERVERS = new IdentityHashMap<>();

    private StructureBackendRouter() {}

    /** This neutral boundary contains no optional mod types, including in generic signatures. */
    public interface Bridge {
        Optional<StructureHandle> resolve(MinecraftServer server, StructureRef reference);
        Optional<StructureDynamics.State> motion(ServerLevel host, StructureRef reference);
        Optional<StructureHit> raycast(ServerLevel host, Vec3 from, Vec3 to);
        boolean clearSightline(ServerLevel host, Vec3 from, Vec3 to);
        StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed);
        StructurePresentationStatus presentationStatus(MinecraftServer server, StructureRef reference);
        StructureTraction.Acquisition acquire(Entity actor, UUID owner, StructureHit proposed,
                StructureTraction.Settings settings, BiPredicate<Entity, StructureHandle> permission);
        void onBlockChanged(ServerLevel host, BlockPos position);
        void shutdown(MinecraftServer server);
    }

    public static StructureBackendStatus status(MinecraftServer server) { return selection(server).status; }

    public static boolean nativeEnabled(MinecraftServer server) {
        return status(server).mode() == StructureBackendStatus.Mode.NATIVE;
    }

    public static StructureBackend backend(MinecraftServer server) {
        var selected = selection(server);
        return selected.status.mode() == StructureBackendStatus.Mode.NATIVE
                ? NativeStructureRuntime.backend(server) : selected.backend;
    }

    public static Optional<StructureHandle> resolve(MinecraftServer server, StructureRef reference) {
        Objects.requireNonNull(reference, "reference");
        return backend(server).resolve(reference);
    }

    public static Optional<StructureDynamics.State> motion(ServerLevel host, StructureRef reference) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        requireThread(host.getServer());
        if (host.getServer().getLevel(host.dimension()) != host
                || !host.dimension().identifier().equals(reference.dimension())) return Optional.empty();
        var selected = selection(host.getServer());
        if (selected.bridge != null && SABLE_ID.equals(reference.backend())) {
            return selected.bridge.motion(host, reference);
        }
        if (selected.status.mode() != StructureBackendStatus.Mode.NATIVE) return Optional.empty();
        return StructurePhysics.activeRegion(host, reference)
                .filter(region -> region.status() == StructurePhysicsRegion.Status.READY)
                .flatMap(region -> region.motion(reference)).map(StructureDynamics.State::from);
    }

    public static StructurePresentationStatus presentationStatus(MinecraftServer server, StructureRef reference) {
        Objects.requireNonNull(reference, "reference");
        var selected = selection(server);
        if (selected.bridge != null && SABLE_ID.equals(reference.backend())) {
            return selected.bridge.presentationStatus(server, reference);
        }
        return selected.status.mode() == StructureBackendStatus.Mode.NATIVE
                ? NativeStructureRuntime.backend(server).presentationStatus(reference)
                : StructurePresentationStatus.NOT_ACTIVE;
    }

    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 from, Vec3 to) {
        validateRay(host, from, to);
        var selected = selection(host.getServer());
        if (selected.bridge != null) return selected.bridge.raycast(host, from, to);
        return selected.status.mode() == StructureBackendStatus.Mode.NATIVE
                ? NativeStructureQueries.raycast(host, from, to) : Optional.empty();
    }

    public static StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed) {
        validateRay(host, from, to);
        Objects.requireNonNull(proposed, "proposed");
        var selected = selection(host.getServer());
        if (selected.bridge != null && SABLE_ID.equals(proposed.block().structure().backend())) {
            return selected.bridge.validate(host, from, to, proposed);
        }
        return selected.status.mode() == StructureBackendStatus.Mode.NATIVE
                ? NativeStructureQueries.validate(host, from, to, proposed)
                : StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
    }

    public static boolean clearSightline(ServerLevel host, Vec3 from, Vec3 to) {
        validateRay(host, from, to);
        var selected = selection(host.getServer());
        if (selected.bridge != null) return selected.bridge.clearSightline(host, from, to);
        return selected.status.mode() == StructureBackendStatus.Mode.NATIVE
                && NativeStructureQueries.clearSightline(host, from, to);
    }

    public static StructureTraction.Acquisition acquire(Entity actor, UUID owner, StructureHit proposed,
            StructureTraction.Settings settings, BiPredicate<Entity, StructureHandle> permission) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(owner, "controllerId");
        Objects.requireNonNull(proposed, "hit");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(permission, "permission");
        if (!(actor.level() instanceof ServerLevel host)) return unavailable();
        var selected = selection(host.getServer());
        if (selected.bridge != null && SABLE_ID.equals(proposed.block().structure().backend())) {
            return selected.bridge.acquire(actor, owner, proposed, settings, permission);
        }
        return selected.status.mode() == StructureBackendStatus.Mode.NATIVE
                ? NativeStructureTraction.acquire(actor, owner, proposed, settings, permission) : unavailable();
    }

    /** Called after ordinary chunk mutations. Never initializes a backend from world generation. */
    public static void onBlockChanged(ServerLevel host, BlockPos position) {
        if (!host.getServer().isSameThread()) return;
        Selection selected;
        synchronized (SERVERS) { selected = SERVERS.get(host.getServer()); }
        if (selected != null && selected.bridge != null) selected.bridge.onBlockChanged(host, position);
    }

    private static StructureTraction.Acquisition unavailable() {
        return new StructureTraction.Acquisition(StructureTraction.Status.NOT_AVAILABLE, Optional.empty());
    }

    private static Selection selection(MinecraftServer server) {
        requireThread(server);
        synchronized (SERVERS) {
            var existing = SERVERS.get(server);
            if (existing != null) return existing;
            var created = create(server);
            SERVERS.put(server, created);
            if (created.status.mode() != StructureBackendStatus.Mode.NATIVE) {
                AcademyCraft.LOGGER.info("Structure backend: {}", created.status);
            }
            return created;
        }
    }

    private static Selection create(MinecraftServer server) {
        var mods = ModList.get();
        var file = mods == null ? null : mods.getModFileById("sable");
        if (file == null) return new Selection(server,
                new StructureBackendStatus(StructureBackendStatus.Mode.NATIVE, false, 0, "native"), null);
        var preflight = NativeStructureManifest.inspect(server.getWorldPath(LevelResource.ROOT)
                .resolve("data/academy/native_structures"));
        Bridge bridge = null;
        String reason;
        try {
            var version = file.getMods().stream().filter(info -> info.getModId().equals("sable"))
                    .findFirst().orElseThrow().getVersion().toString();
            if (!SABLE_VERSION.equals(version)) throw new IllegalStateException("untested_sable_version:" + version);
            if (!matchesArtifact(file.getFile().getFilePath(), SABLE_SHA256)) {
                throw new IllegalStateException("untested_sable_artifact");
            }
            requireDependency(mods, "sablecompanion", "1.6.0-port.26.2.0.70",
                    "91e4fe9ef30994904bfa18bf140e804714700cd89d272a337b3ecaea9df5cad4");
            requireDependency(mods, "veil", "4.3.2-port.26.2.0.70",
                    "b666125341e774dd13c57ace5b6a0b7a4a164d7249bb1d5413f98b383680c71d");
            bridge = (Bridge) Class.forName("org.academy.internal.common.structure.sable.SableStructureBridge")
                    .getConstructor().newInstance();
            reason = preflight.requiresMigration() ? "native_content_retained_migration_required"
                    : "sable_loaded_queries_and_traction;capture_and_materialization_unavailable";
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError unavailable) {
            reason = "sable_bridge_unavailable:" + unavailable.getClass().getSimpleName() + ":" + unavailable.getMessage();
            AcademyCraft.LOGGER.warn("Sable bridge disabled; native structures remain inactive", unavailable);
        }
        return new Selection(server, decide(bridge != null, preflight, reason), bridge);
    }

    static StructureBackendStatus decide(boolean compatible, NativeStructureManifest.Preflight preflight, String reason) {
        return new StructureBackendStatus(!compatible ? StructureBackendStatus.Mode.INCOMPATIBLE
                : preflight.requiresMigration() ? StructureBackendStatus.Mode.MIGRATION_REQUIRED
                : StructureBackendStatus.Mode.SABLE, compatible, preflight.structures(), reason);
    }

    private static void requireDependency(ModList mods, String id, String version, String hash) throws IOException {
        var file = mods.getModFileById(id);
        if (file == null || file.getMods().stream().noneMatch(info -> info.getModId().equals(id)
                && info.getVersion().toString().equals(version))
                || !matchesArtifact(file.getFile().getFilePath(), hash)) {
            throw new IllegalStateException("untested_sable_dependency:" + id);
        }
    }

    static boolean matchesArtifact(Path path, String expected) throws IOException {
        if (!Files.isRegularFile(path)) return false;
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[65_536];
            for (int count; (count = input.read(buffer)) >= 0;) if (count != 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void requireThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) throw new IllegalStateException("Structure access requires the server thread");
    }

    private static void validateRay(ServerLevel host, Vec3 from, Vec3 to) {
        Objects.requireNonNull(host, "host");
        requireThread(host.getServer());
        Objects.requireNonNull(from, "worldFrom");
        Objects.requireNonNull(to, "worldTo");
        double length = from.distanceTo(to);
        if (!Double.isFinite(from.x) || !Double.isFinite(from.y) || !Double.isFinite(from.z)
                || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)
                || !Double.isFinite(length) || length < 1.0E-6 || length > StructureQueries.MAX_RAY_LENGTH) {
            throw new IllegalArgumentException("Structure ray must be finite, nonzero and at most 256 blocks");
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        Selection selected;
        synchronized (SERVERS) { selected = SERVERS.remove(event.getServer()); }
        if (selected != null && selected.bridge != null) selected.bridge.shutdown(event.getServer());
    }

    private static final class Selection {
        private final StructureBackendStatus status;
        private final @Nullable Bridge bridge;
        private final StructureBackend backend;

        private Selection(MinecraftServer server, StructureBackendStatus status, @Nullable Bridge bridge) {
            this.status = status;
            this.bridge = bridge;
            // Sable owns loading, persistence and gameplay block interaction. The currently
            // supported bridge must not imitate these contracts with unsafe assembly/removal.
            backend = new StructureBackend() {
                @Override public Identifier id() { return SABLE_ID; }
                @Override public Optional<StructureHandle> createEmpty(ServerLevel host, StructurePose pose) {
                    requireThread(server);
                    Objects.requireNonNull(host, "hostLevel");
                    Objects.requireNonNull(pose, "pose");
                    if (host.getServer() != server || server.getLevel(host.dimension()) != host) {
                        throw new IllegalArgumentException("Structure requires a live host");
                    }
                    return Optional.empty();
                }
                @Override public Optional<StructureHandle> resolve(StructureRef reference) {
                    requireThread(server);
                    Objects.requireNonNull(reference, "reference");
                    return bridge == null ? Optional.empty() : bridge.resolve(server, reference);
                }
                @Override public Optional<StructureHandle> load(StructureRef reference) { return resolve(reference); }
                @Override public boolean unload(StructureRef reference) {
                    requireThread(server);
                    Objects.requireNonNull(reference, "reference");
                    return false;
                }
            };
        }
    }
}
