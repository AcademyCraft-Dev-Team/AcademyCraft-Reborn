package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTerrainPublisherTest {
    private static final StructureRef REF = new StructureRef(UUID.randomUUID(),
            Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            Identifier.fromNamespaceAndPath("academy", "sublevel"), 1);
    private static final StructurePipeline.Epoch EPOCH =
            new StructurePipeline.Epoch(REF, UUID.randomUUID(), 1);

    private static StructurePipeline.Geometry geometry(long revision, boolean complete) {
        return new StructurePipeline.Geometry(
                new StructurePipeline.Epoch(REF, EPOCH.runtimeId(), revision), List.of(), 0, complete);
    }

    private static StructurePipeline.TerrainSection section(long key, long revision, boolean complete) {
        return new StructurePipeline.TerrainSection(key, revision, geometry(EPOCH.revision(), complete));
    }

    private static StructurePipeline.TerrainPublication publication(long revision, long coverage,
                                                                   List<StructurePipeline.TerrainSection> sections,
                                                                   List<Long> required) {
        return new StructurePipeline.TerrainPublication(
                new StructurePipeline.Epoch(REF, EPOCH.runtimeId(), revision), coverage, sections, required);
    }

    private static PipelineTerrainPublisher.PublicationState previous(long revision, long coverage,
                                                                     java.util.Map<Long, Long> sections,
                                                                     Set<Long> required) {
        return new PipelineTerrainPublisher.PublicationState(REF, revision, coverage, sections, required);
    }

    @Test
    void completeRequiredCoverageIsAppliedAtomically() {
        var receipt = PipelineTerrainPublisher.evaluate(
                publication(1, 4, List.of(section(1, 1, true), section(2, 1, true)), List.of(1L, 2L)), null);
        assertEquals(StructurePipeline.PublicationStatus.APPLIED, receipt.status());
        assertEquals(4, receipt.appliedGeneration());
    }

    @Test
    void missingRequiredSectionIsIncomplete() {
        var receipt = PipelineTerrainPublisher.evaluate(
                publication(1, 4, List.of(section(1, 1, true)), List.of(1L, 2L)), null);
        assertEquals(StructurePipeline.PublicationStatus.INCOMPLETE, receipt.status());
        assertEquals("missing_required_section", receipt.detail());
    }

    @Test
    void olderRevisionOrCoverageIsStale() {
        var staleRevision = PipelineTerrainPublisher.evaluate(
                publication(1, 4, List.of(section(1, 1, true)), List.of(1L)),
                previous(2, 3, java.util.Map.of(1L, 1L), Set.of(1L)));
        assertEquals(StructurePipeline.PublicationStatus.STALE, staleRevision.status());
        assertEquals("older_revision", staleRevision.detail());

        var staleCoverage = PipelineTerrainPublisher.evaluate(
                publication(1, 3, List.of(section(1, 1, true)), List.of(1L)),
                previous(1, 4, java.util.Map.of(1L, 1L), Set.of(1L)));
        assertEquals(StructurePipeline.PublicationStatus.STALE, staleCoverage.status());
        assertEquals("older_coverage", staleCoverage.detail());
    }

    @Test
    void olderSectionRevisionAndDifferentReferenceAreStale() {
        var matchingEpochSection = new StructurePipeline.TerrainSection(1, 4, geometry(2, true));
        var olderSection = PipelineTerrainPublisher.evaluate(
                publication(2, 5, List.of(matchingEpochSection), List.of(1L)),
                previous(2, 4, java.util.Map.of(1L, 5L), Set.of(1L)));
        assertEquals(StructurePipeline.PublicationStatus.STALE, olderSection.status());
        assertEquals("older_section_revision", olderSection.detail());

        var otherRef = new StructureRef(UUID.randomUUID(), REF.dimension(), REF.backend(), 1);
        var otherEpoch = new StructurePipeline.Epoch(otherRef, EPOCH.runtimeId(), 3);
        var mismatch = PipelineTerrainPublisher.evaluate(
                new StructurePipeline.TerrainPublication(otherEpoch, 5, List.of(section(1, 1, true)), List.of(1L)),
                previous(3, 4, java.util.Map.of(1L, 1L), Set.of(1L)));
        assertEquals(StructurePipeline.PublicationStatus.STALE, mismatch.status());
        assertEquals("reference_mismatch", mismatch.detail());
    }

    @Test
    void incompleteGeometryAndMismatchedEpochAreRejected() {
        var incomplete = PipelineTerrainPublisher.evaluate(
                publication(1, 4, List.of(section(1, 1, false)), List.of(1L)), null);
        assertEquals(StructurePipeline.PublicationStatus.INCOMPLETE, incomplete.status());

        var mismatch = new StructurePipeline.TerrainPublication(EPOCH, 4,
                List.of(new StructurePipeline.TerrainSection(1, 1, geometry(2, true))), List.of(1L));
        var stale = PipelineTerrainPublisher.evaluate(mismatch, null);
        assertEquals(StructurePipeline.PublicationStatus.STALE, stale.status());
        assertEquals("section_epoch_mismatch", stale.detail());
    }

    @Test
    void duplicateSectionsAndSectionBudgetAreIncomplete() {
        var duplicate = PipelineTerrainPublisher.evaluate(
                publication(1, 4, List.of(section(1, 1, true), section(1, 1, true)), List.of(1L)), null);
        assertEquals(StructurePipeline.PublicationStatus.INCOMPLETE, duplicate.status());

        var sections = new ArrayList<StructurePipeline.TerrainSection>();
        for (long key = 0; key <= PipelineTerrainPublisher.MAX_PUBLICATION_SECTIONS; key++) {
            sections.add(section(key, 1, true));
        }
        var oversized = PipelineTerrainPublisher.evaluate(publication(1, 4, sections, List.of(1L)), null);
        assertEquals(StructurePipeline.PublicationStatus.INCOMPLETE, oversized.status());
        assertEquals("section_budget", oversized.detail());
    }

    @Test
    void nullPublicationIsUnavailable() {
        assertEquals(StructurePipeline.PublicationStatus.UNAVAILABLE,
                PipelineTerrainPublisher.evaluate(null, null).status());
    }

    @Test
    void invalidPublicationStateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> previous(-1, 0, java.util.Map.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineTerrainPublisher.PublicationState(null, 0, 0, java.util.Map.of(), Set.of()));
    }
}
