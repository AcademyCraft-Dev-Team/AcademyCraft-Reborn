package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelActivationRegistry;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-scoped provenance service. Only the owning capture transaction may commit metadata. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class LiveStructureProvenance implements StructureProvenance {
    public static final LiveStructureProvenance INSTANCE = new LiveStructureProvenance();
    private static final Map<MinecraftServer, Optional<StructureProvenanceLedger>> SERVERS = new IdentityHashMap<>();

    private LiveStructureProvenance() {}

    public static boolean healthy(MinecraftServer server) {
        return ledger(server).filter(StructureProvenanceLedger::healthy).isPresent();
    }

    public static Collection<StructureProvenanceLedger.Entry> entries(MinecraftServer server) {
        return ledger(server).map(StructureProvenanceLedger::entries).orElseGet(java.util.List::of);
    }

    /** Stable creation identity survives unload/reload; this lookup does not grant runtime authority. */
    public static Optional<Metadata> metadata(MinecraftServer server, StructureRef reference) {
        return ledger(server).flatMap(value -> value.metadata(reference));
    }

    /** Resolves only a loaded activation in this server, never loading chunks or retaining tickets. */
    public static Optional<StructureRef> liveReference(MinecraftServer server, StructureRef stableReference) {
        requireThread(server);
        var reference = stableReference;
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            var activation = SubLevelActivationRegistry.find(new SubLevelId(reference.id())).orElse(null);
            if (activation == null) return Optional.empty();
            reference = new StructureRef(reference.id(), reference.dimension(), reference.backend(), activation.generation());
        }
        return StructureBackendRouter.resolve(server, reference).map(value -> value.reference());
    }

    /** Called after target save, before the capture journal may publish/recycle. Failure retains that journal. */
    public static boolean commitCapture(MinecraftServer server, StructureRef reference, Metadata metadata) {
        var ledger = ledger(server).orElse(null);
        if (ledger == null || !ledger.healthy()) return false;
        try {
            ledger.commit(reference, metadata);
            return true;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure creation metadata could not be committed: {}", reference, failure);
            return false;
        }
    }

    /** Exact-creation compare/remove for the transaction that has durably restored or destroyed the body. */
    public static boolean remove(MinecraftServer server, StructureRef reference, UUID creationOperationId) {
        var ledger = ledger(server).orElse(null);
        if (ledger == null || !ledger.healthy()) return false;
        try {
            return ledger.remove(reference, creationOperationId);
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure creation metadata could not be retired: {}", reference, failure);
            return false;
        }
    }

    @Override
    public StructureOperation.Result<Metadata> query(StructureOperation.Context context) {
        var validity = validity(context);
        if (validity != null) return result(StructureOperation.Status.STALE, validity, Optional.empty());
        if (!healthy(context.host().getServer())) {
            return result(StructureOperation.Status.BLOCKED, "provenance_ledger_unavailable", Optional.empty());
        }
        var metadata = metadata(context.host().getServer(), context.reference());
        return result(StructureOperation.Status.READY, metadata.isPresent() ? "marked_creation" : "unmarked_creation", metadata);
    }

    @Override
    public StructureOperation.Result<Preparation> prepare(StructureOperation.Context context, Metadata metadata) {
        var validity = validity(context);
        if (validity != null) return result(StructureOperation.Status.STALE, validity, Optional.empty());
        if (!healthy(context.host().getServer())) {
            return result(StructureOperation.Status.BLOCKED, "provenance_ledger_unavailable", Optional.empty());
        }
        var existing = metadata(context.host().getServer(), context.reference());
        if (existing.isPresent() && !existing.get().equals(metadata)) {
            return result(StructureOperation.Status.CONFLICT, "creation_metadata_immutable", Optional.empty());
        }
        return result(StructureOperation.Status.READY, "creation_image_prepared", Optional.of(new Preparation(
                context.reference(), context.runtimeId(), context.expectedContentRevision(), metadata)));
    }

    private static @Nullable String validity(StructureOperation.Context context) {
        var host = context.host();
        requireThread(host.getServer());
        if (host.getServer().getLevel(host.dimension()) != host || context.actor().level() != host
                || !context.actor().isAlive() || context.actor().isRemoved()) return "actor_or_host_unavailable";
        var body = StructureBackendRouter.orientationAccess(host, context.reference())
                .flatMap(access -> access.observe(host, context.reference())).orElse(null);
        return body != null && body.runtimeId().equals(context.runtimeId())
                && body.contentRevision() == context.expectedContentRevision() ? null : "stale_structure_activation";
    }

    private static <T> StructureOperation.Result<T> result(StructureOperation.Status status, String detail, Optional<T> value) {
        return new StructureOperation.Result<>(status, detail, Optional.empty(), value);
    }

    private static Optional<StructureProvenanceLedger> ledger(MinecraftServer server) {
        requireThread(server);
        synchronized (SERVERS) {
            return SERVERS.computeIfAbsent(server, value -> {
                try {
                    var ledger = new StructureProvenanceLedger(value.getWorldPath(LevelResource.ROOT)
                            .resolve("data/academy").resolve(StructureProvenanceLedger.DIRECTORY_NAME));
                    if (!ledger.healthy()) {
                        AcademyCraft.LOGGER.error("Structure provenance contains corrupt/unknown records; marked captures and restoration blocked");
                    }
                    return Optional.of(ledger);
                } catch (IOException | RuntimeException failure) {
                    AcademyCraft.LOGGER.error("Structure provenance ledger unavailable; marked captures and restoration blocked", failure);
                    return Optional.empty();
                }
            });
        }
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Structure provenance requires the server thread");
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        synchronized (SERVERS) { SERVERS.remove(event.getServer()); }
    }
}
