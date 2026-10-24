package org.academy.internal.client.validation;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;

/** Read-only assertions shared by the real default-skill and program VM fixtures. */
final class StructureProvenanceSmokeAssertions {
    private StructureProvenanceSmokeAssertions() {}

    static StructureProvenance.Metadata requireSkill(ServerPlayer player, StructureRef reference, Identifier skill) {
        return requireSkill(player, reference, skill, player.getUUID());
    }

    static StructureProvenance.Metadata requireSkill(ServerPlayer player, StructureRef reference, Identifier skill,
                                                      java.util.UUID attributedOwner) {
        if (!LiveStructureProvenance.healthy(player.level().getServer())) {
            throw new IllegalStateException("Provenance ledger is unavailable");
        }
        var metadata = LiveStructureProvenance.metadata(player.level().getServer(), reference)
                .orElseThrow(() -> new IllegalStateException("Skill capture is not marked: " + reference));
        if (metadata.source() != StructureProvenance.CreationKind.SKILL_CAPTURE
                || !metadata.skillId().equals(skill)
                || !metadata.ownerId().filter(attributedOwner::equals).isPresent()
                || !metadata.restorePolicy().id().equals(StructureProvenance.SKILL_REST_POLICY)
                || metadata.restorePolicy().version() != 1 || metadata.parent().isPresent()
                || !metadata.creationOperationId().equals(metadata.rootLineageId())) {
            throw new IllegalStateException("Incorrect skill provenance: " + metadata);
        }
        AcademyCraft.LOGGER.info("STRUCTURE_SKILL_PROVENANCE_VERIFIED backend={} skill={} reference={} operation={} owner={}",
                reference.backend(), skill, reference.id(), metadata.creationOperationId(), attributedOwner);
        return metadata;
    }
}
