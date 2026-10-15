package org.academy.internal.common.ability.aeromanip.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.migrate.ProgramGraphMigrator;
import org.academy.internal.common.ability.program.registry.ProgramNodeLookup;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Explicit Aeromanip graph migrations. The legacy {@code action/launch_block_structure} node is
 * rewritten into the unified {@code action/structure/extract → control → launch} subgraph while
 * keeping every original port (flow, block, direction) and configuration field
 * (power, structure radius, duration, restore_when_settled) readable.
 */
public final class AeromanipProgramGraphMigrations {
    public static final String LEGACY_UNPARSEABLE_CONFIGURATION =
            "legacy_launch_block_structure_configuration_unparsed";

    private AeromanipProgramGraphMigrations() {
    }

    public static ProgramGraphMigrator create() {
        return ProgramGraphMigrator.builder()
                .rewrite(ProgramGraphMigrator.rewrite(
                        AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                        AeromanipProgramGraphMigrations::rewriteLegacyLaunch))
                .audit(ProgramGraphMigrator.audit(
                        AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                        _ -> LEGACY_UNPARSEABLE_CONFIGURATION))
                .build();
    }

    private static ProgramGraphMigrator.@Nullable Rewrite rewriteLegacyLaunch(
            ProgramGraph.Node node,
            ProgramNodeLookup lookup
    ) {
        if (!node.configuration().isJsonObject()) return null;
        var legacy = node.configuration().getAsJsonObject();
        var parsed = AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration.CODEC
                .parse(JsonOps.INSTANCE, legacy)
                .result()
                .orElse(null);
        if (parsed == null) return null;
        var launchId = AeromanipProgramNodeCatalog.unifiedIdForLegacy(node.type());
        var extractType = lookup.find(AeromanipProgramNodeIds.STRUCTURE_EXTRACT);
        var controlType = lookup.find(AeromanipProgramNodeIds.STRUCTURE_CONTROL);
        var launchType = lookup.find(launchId);
        if (extractType == null || controlType == null || launchType == null) return null;
        var extract = new ProgramGraph.Node(
                0,
                AeromanipProgramNodeIds.STRUCTURE_EXTRACT,
                extractType.schemaVersion(),
                AeromanipProgramNodeCatalog.legacyExtractConfiguration(legacy)
        );
        var control = new ProgramGraph.Node(
                1,
                AeromanipProgramNodeIds.STRUCTURE_CONTROL,
                controlType.schemaVersion(),
                new JsonObject()
        );
        var launch = new ProgramGraph.Node(
                2,
                launchId,
                launchType.schemaVersion(),
                AeromanipProgramNodeCatalog.legacyLaunchConfiguration(legacy)
        );
        return new ProgramGraphMigrator.Rewrite(
                List.of(extract, control, launch),
                List.of(
                        edge(0, "flow", 1, "flow"),
                        edge(1, "flow", 2, "flow"),
                        edge(0, "structure", 1, "structure"),
                        edge(1, "control", 2, "control")
                ),
                Map.of(
                        "flow", new ProgramGraph.Endpoint(0, "flow"),
                        "block", new ProgramGraph.Endpoint(0, "host"),
                        "direction", new ProgramGraph.Endpoint(2, "direction")
                ),
                Map.of("flow", new ProgramGraph.Endpoint(2, "flow")),
                "legacy_launch_block_structure"
        );
    }

    private static ProgramGraph.Edge edge(
            int fromNode,
            String fromPort,
            int toNode,
            String toPort
    ) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(fromNode, fromPort),
                new ProgramGraph.Endpoint(toNode, toPort)
        );
    }
}
