package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructurePipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * PIPE-03 draft publication gate. It validates body epoch, per-section source revisions and the
 * coverage generation before atomically replacing a runtime's published terrain. Native body
 * insertion remains the caller's job; the caller must not step a body unless
 * {@link #coverageComplete(ServerLevel, UUID)} is true (the live cache keeps its own gate until
 * this pipeline is wired). Budget exhaustion rejects the publication rather than silently
 * publishing partial terrain.
 */
public final class PipelineTerrainPublisher {
    public static final int MAX_PUBLICATION_SECTIONS = 4_096;
    private final Map<ServerLevel, Map<UUID, PublicationState>> hosts = new WeakHashMap<>();

    public synchronized StructurePipeline.TerrainReceipt publish(ServerLevel host,
                                                                 StructurePipeline.TerrainPublication publication) {
        if (host == null) return new StructurePipeline.TerrainReceipt(
                StructurePipeline.PublicationStatus.UNAVAILABLE, -1, "host_required");
        var server = host.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Terrain publication requires the owning server thread");
        }
        if (server.getLevel(host.dimension()) != host) {
            return new StructurePipeline.TerrainReceipt(
                    StructurePipeline.PublicationStatus.UNAVAILABLE, -1, "host_unregistered");
        }
        validate(publication);
        var runtimeId = publication.epoch().runtimeId();
        var states = hosts.computeIfAbsent(host, ignored -> new HashMap<>());
        var previous = states.get(runtimeId);
        var receipt = evaluate(publication, previous);
        if (receipt.status() == StructurePipeline.PublicationStatus.APPLIED) {
            states.put(runtimeId, new PublicationState(publication.epoch().reference(),
                    publication.epoch().revision(), publication.coverageGeneration(),
                    sourceRevisions(publication), requiredKeys(publication)));
        }
        return receipt;
    }

    public synchronized boolean coverageComplete(ServerLevel host, UUID runtimeId) {
        if (host == null || runtimeId == null) return false;
        var states = hosts.get(host);
        var state = states == null ? null : states.get(runtimeId);
        return state != null && state.sourceRevisions().keySet().containsAll(state.required());
    }

    public synchronized int publishedSectionCount(ServerLevel host, UUID runtimeId) {
        var states = hosts.get(host);
        var state = states == null ? null : states.get(runtimeId);
        return state == null ? 0 : state.sourceRevisions().size();
    }

    public synchronized void clear(ServerLevel host) {
        if (host != null) hosts.remove(host);
    }

    public synchronized void clearAll() {
        hosts.clear();
    }

    /** Pure publication decision, shared by the live gate and unit tests. */
    public static StructurePipeline.TerrainReceipt evaluate(StructurePipeline.TerrainPublication publication,
                                                            PublicationState previous) {
        if (publication == null) {
            return new StructurePipeline.TerrainReceipt(
                    StructurePipeline.PublicationStatus.UNAVAILABLE, -1, "publication_required");
        }
        if (previous != null) {
            if (!publication.epoch().reference().equals(previous.reference())) {
                return new StructurePipeline.TerrainReceipt(
                        StructurePipeline.PublicationStatus.STALE, previous.coverageGeneration(), "reference_mismatch");
            }
            if (publication.epoch().revision() < previous.revision()) {
                return new StructurePipeline.TerrainReceipt(
                        StructurePipeline.PublicationStatus.STALE, previous.coverageGeneration(), "older_revision");
            }
            if (publication.coverageGeneration() < previous.coverageGeneration()) {
                return new StructurePipeline.TerrainReceipt(
                        StructurePipeline.PublicationStatus.STALE, previous.coverageGeneration(), "older_coverage");
            }
        }
        if (publication.sections().size() > MAX_PUBLICATION_SECTIONS) {
            return new StructurePipeline.TerrainReceipt(
                    StructurePipeline.PublicationStatus.INCOMPLETE, -1, "section_budget");
        }
        var sections = sectionKeys(publication);
        if (sections.size() != publication.sections().size()) {
            return new StructurePipeline.TerrainReceipt(
                    StructurePipeline.PublicationStatus.INCOMPLETE, -1, "duplicate_section");
        }
        var required = requiredKeys(publication);
        for (var section : publication.sections()) {
            if (section.geometry() == null || !section.geometry().complete()) {
                return new StructurePipeline.TerrainReceipt(
                        StructurePipeline.PublicationStatus.INCOMPLETE, -1, "incomplete_geometry");
            }
            if (!section.geometry().epoch().equals(publication.epoch())) {
                return new StructurePipeline.TerrainReceipt(
                        StructurePipeline.PublicationStatus.STALE, -1, "section_epoch_mismatch");
            }
            if (previous != null) {
                var known = previous.sourceRevisions().get(section.sectionKey());
                if (known != null && section.sourceRevision() < known) {
                    return new StructurePipeline.TerrainReceipt(
                            StructurePipeline.PublicationStatus.STALE, previous.coverageGeneration(),
                            "older_section_revision");
                }
            }
        }
        if (!sections.containsAll(required)) {
            return new StructurePipeline.TerrainReceipt(
                    StructurePipeline.PublicationStatus.INCOMPLETE, -1, "missing_required_section");
        }
        return new StructurePipeline.TerrainReceipt(StructurePipeline.PublicationStatus.APPLIED,
                publication.coverageGeneration(), "published");
    }

    private static void validate(StructurePipeline.TerrainPublication publication) {
        if (publication == null) throw new IllegalArgumentException("Terrain publication is required");
        var epoch = publication.epoch();
        if (epoch == null || epoch.reference() == null || epoch.runtimeId() == null || epoch.revision() < 0) {
            throw new IllegalArgumentException("Terrain publication epoch is invalid");
        }
        if (publication.coverageGeneration() < 0) {
            throw new IllegalArgumentException("Coverage generation must be nonnegative");
        }
        if (publication.sections() == null || publication.requiredSections() == null) {
            throw new IllegalArgumentException("Terrain sections and required coverage are required");
        }
        for (var section : publication.sections()) {
            if (section == null || section.geometry() == null) {
                throw new IllegalArgumentException("Terrain section is incomplete");
            }
            if (section.sourceRevision() < 0) {
                throw new IllegalArgumentException("Terrain section revision must be nonnegative");
            }
        }
        for (var required : publication.requiredSections()) {
            if (required == null) throw new IllegalArgumentException("Required section key is required");
        }
    }

    private static Set<Long> sectionKeys(StructurePipeline.TerrainPublication publication) {
        var keys = new HashSet<Long>();
        for (var section : publication.sections()) keys.add(section.sectionKey());
        return Set.copyOf(keys);
    }

    private static Set<Long> requiredKeys(StructurePipeline.TerrainPublication publication) {
        return Set.copyOf(new HashSet<>(publication.requiredSections()));
    }

    private static Map<Long, Long> sourceRevisions(StructurePipeline.TerrainPublication publication) {
        var revisions = new HashMap<Long, Long>();
        for (var section : publication.sections()) {
            revisions.put(section.sectionKey(), section.sourceRevision());
        }
        return revisions;
    }

    public record PublicationState(org.academy.api.common.structure.StructureRef reference, long revision,
                                   long coverageGeneration,
                                   Map<Long, Long> sourceRevisions, Set<Long> required) {
        public PublicationState {
            if (reference == null) throw new IllegalArgumentException("Publication reference is required");
            if (revision < 0 || coverageGeneration < 0) {
                throw new IllegalArgumentException("Publication state revisions must be nonnegative");
            }
            sourceRevisions = Map.copyOf(sourceRevisions);
            required = Set.copyOf(required);
        }
    }
}
