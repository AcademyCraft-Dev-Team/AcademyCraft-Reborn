package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureGridRestoration;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.control.StructureRestSupport;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

/** Bounded due queue for marked creations. No all-cell scan and no chunk ticket on idle ticks. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillStructureRestoration {
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private static final int DISCOVERY_BUDGET = 8;
    private static final int MAX_TRACKED = 65_536;
    private static final StructureGridRestoration.Options OPTIONS = new StructureGridRestoration.Options(
            StructureGridRestoration.Alignment.UPRIGHT_NEAREST_YAW);

    private SkillStructureRestoration() {}

    /** Controls can begin and end between low-frequency samples; either event resets known rest. */
    public static void activity(MinecraftServer server, StructureRef reference) {
        var state = SERVERS.get(server);
        if (state == null) return;
        var candidate = state.candidates.get(stable(reference));
        if (candidate != null) candidate.rest.reset();
    }

    public record Statistics(int tracked, long observations, long attempts, long restored) {}

    public static Statistics statistics(MinecraftServer server) {
        var state = SERVERS.get(server);
        return state == null ? new Statistics(0, 0, 0, 0)
                : new Statistics(state.candidates.size(), state.observations, state.attempts, state.restored);
    }

    public static String detail(MinecraftServer server, StructureRef reference) {
        var state = SERVERS.get(server);
        var candidate = state == null ? null : state.candidates.get(stable(reference));
        return candidate == null ? "not_tracked" : candidate.detail;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var state = SERVERS.computeIfAbsent(server, ignored -> new State());
        if (state.stopping) return;
        long tick = server.getTickCount();
        var settings = StructureRestSettings.get(server);
        // A disabled candidate policy cannot abandon an already committed ownership transfer.
        if (tick >= state.nextRecovery) {
            if (state.recoveryLevels == null || !state.recoveryLevels.hasNext()) {
                state.recoveryLevels = server.getAllLevels().iterator();
            }
            if (state.recoveryLevels.hasNext()) MarkedGridRestoration.INSTANCE.recover(state.recoveryLevels.next());
            state.nextRecovery = tick + 20;
        }
        if (!settings.enabled()) {
            state.candidates.clear();
            state.due.clear();
            state.discovery = null;
            return;
        }
        if (!LiveStructureProvenance.healthy(server)) return;
        discover(server, state, tick);
        boolean requested = false;
        for (int i = 0; i < settings.candidatesPerTick(); i++) {
            var candidate = state.due.peek();
            if (candidate == null || candidate.nextTick > tick) break;
            state.due.remove();
            if (state.candidates.get(candidate.reference) != candidate) continue;
            try {
                requested |= inspect(server, state, candidate, settings, tick, !requested);
            } catch (RuntimeException failure) {
                candidate.rest.reset();
                candidate.detail = "observation_failed";
                candidate.nextTick = tick + Math.max(600, settings.retryTicks());
                AcademyCraft.LOGGER.warn("Deferred marked structure restoration for {}", candidate.reference, failure);
            }
            if (state.candidates.get(candidate.reference) == candidate) state.due.add(candidate);
        }
    }

    private static void discover(MinecraftServer server, State state, long tick) {
        if (state.discovery == null) {
            if (tick < state.nextDiscovery) return;
            state.discovery = LiveStructureProvenance.entries(server).iterator();
        }
        try {
            for (int i = 0; i < DISCOVERY_BUDGET && state.discovery.hasNext(); i++) {
                var entry = state.discovery.next();
                var key = stable(entry.reference());
                if (state.candidates.size() < MAX_TRACKED && !state.candidates.containsKey(key)) {
                    var candidate = new Candidate(key, tick);
                    state.candidates.put(key, candidate);
                    state.due.add(candidate);
                }
            }
            if (!state.discovery.hasNext()) { state.discovery = null; state.nextDiscovery = tick + 20; }
        } catch (ConcurrentModificationException changed) {
            // The live ledger was updated by a capture/restore. Never copy or rescan all entries here.
            state.discovery = null;
        }
    }

    private static boolean inspect(MinecraftServer server, State state, Candidate candidate,
                                   StructureRestSettings settings, long tick, boolean mayRequest) {
        candidate.nextTick = tick + settings.checkIntervalTicks();
        var metadata = LiveStructureProvenance.metadata(server, candidate.reference).orElse(null);
        if (metadata == null) { state.candidates.remove(candidate.reference); return false; }
        var policy = metadata.restorePolicy();
        if (!policy.id().equals(MarkedGridRestoration.POLICY_ID) || policy.version() != 1
                || !policy.parameters().isEmpty()) {
            defer(candidate, tick + 12_000, "unknown_restore_policy");
            return false;
        }
        var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, candidate.reference.dimension()));
        var reference = host == null ? null : LiveStructureProvenance.liveReference(server, candidate.reference).orElse(null);
        if (reference == null) { defer(candidate, tick + 600, "activation_unavailable"); return false; }
        var access = StructureBackendRouter.orientationAccess(host, reference).orElse(null);
        var rest = access == null ? null : access.observeRest(host, reference).orElse(null);
        if (rest == null) { defer(candidate, tick + settings.retryTicks(), "body_unavailable"); return false; }
        state.observations++;
        boolean controlled = StructureRestorationActivity.controlBusy(host, reference, rest.runtimeId())
                || MarkedGridRestoration.INSTANCE.busy(host, reference);
        var observation = new StructureRestTracker.Observation(rest.runtimeId(), rest.contentRevision(),
                rest.pose(), rest.linearVelocity(), rest.angularVelocity(), rest.terrainReady(), controlled);
        int maximumGap = Math.max(settings.checkIntervalTicks() * 2,
                settings.checkIntervalTicks() + (state.candidates.size() + settings.candidatesPerTick() - 1)
                        / settings.candidatesPerTick());
        if (!candidate.rest.observe(observation, tick, settings.restTicks(), maximumGap)) {
            candidate.detail = controlled ? "controlled_or_busy" : "observing_rest";
            return false;
        }
        if (!mayRequest) { candidate.detail = "waiting_budget"; candidate.nextTick = tick + 1; return false; }
        // Full geometry, nearby interaction and support are paid only by the one attempt budget.
        var body = access.observe(host, reference).orElse(null);
        if (body == null || !body.runtimeId().equals(rest.runtimeId()) || body.contentRevision() != rest.contentRevision()
                || StructureRestorationActivity.isBusy(host, body)) {
            defer(candidate, tick + settings.retryTicks(), "final_body_or_interaction_unavailable");
            return true;
        }
        if (!StructureRestSupport.grounded(host, body)) {
            defer(candidate, tick + settings.retryTicks(), "support_unconfirmed");
            return true;
        }
        var authority = new StructureGridRestoration.Access(host, reference,
                new StructureGridRestoration.RestorationActor(metadata.ownerId(), policy.id()));
        var preview = MarkedGridRestoration.INSTANCE.preview(authority, OPTIONS);
        if (preview.status() != StructureOperation.Status.READY || preview.value().isEmpty()) {
            defer(candidate, tick + settings.retryTicks(), preview.detail());
            return true;
        }
        state.attempts++;
        var result = MarkedGridRestoration.INSTANCE.request(authority, new StructureGridRestoration.Request(
                preview.value().orElseThrow().previewId(), StructureGridRestoration.Cause.MARKED_REST));
        if (result.status() == StructureOperation.Status.COMPLETED && result.value().isPresent()
                && result.value().orElseThrow().phase() == StructureGridRestoration.Phase.RESTORED) {
            state.restored++;
            state.candidates.remove(candidate.reference);
        } else {
            defer(candidate, tick + settings.retryTicks(), result.detail());
        }
        return true;
    }

    private static void defer(Candidate candidate, long nextTick, String detail) {
        candidate.rest.reset();
        candidate.nextTick = nextTick;
        candidate.detail = detail;
    }

    private static StructureRef stable(StructureRef reference) {
        return new StructureRef(reference.id(), reference.dimension(), reference.backend(), 0);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        SERVERS.computeIfAbsent(event.getServer(), ignored -> new State()).stopping = true;
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }

    private static final class State {
        final Map<StructureRef, Candidate> candidates = new HashMap<>();
        final PriorityQueue<Candidate> due = new PriorityQueue<>(Comparator.comparingLong((Candidate c) -> c.nextTick)
                .thenComparing(c -> c.reference.id()));
        @Nullable Iterator<StructureProvenanceLedger.Entry> discovery;
        @Nullable Iterator<net.minecraft.server.level.ServerLevel> recoveryLevels;
        long nextDiscovery, nextRecovery, observations, attempts, restored;
        boolean stopping;
    }

    private static final class Candidate {
        final StructureRef reference;
        final StructureRestTracker rest = new StructureRestTracker();
        long nextTick;
        String detail = "observing_rest";
        Candidate(StructureRef reference, long nextTick) { this.reference = reference; this.nextTick = nextTick; }
    }
}
