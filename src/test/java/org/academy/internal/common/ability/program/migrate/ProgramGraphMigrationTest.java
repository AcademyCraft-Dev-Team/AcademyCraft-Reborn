package org.academy.internal.common.ability.program.migrate;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodePurity;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeScope;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeIds;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeCatalog;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeIds;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramGraphMigrations;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeCatalog;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeIds;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramNodeLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P33 graph versioner: legacy graphs and binary envelopes are migrated into the unified node
 * family with their ports and configuration preserved; failures are per-node diagnostics and
 * the program and book schema versions are never bumped.
 */
class ProgramGraphMigrationTest {
    @Test
    void aeromanipLegacyLaunchIsRewrittenIntoTheUnifiedSubgraph() {
        var definition = AbilityProgramDefinitions.require(AeromanipProgramNodeCatalog.AEROMANIP);
        var legacyConfiguration = legacyLaunchConfiguration(1.5f, 4, 6, false);
        var entryId = BaseAbilityProgramDefinition.entryId(AeromanipProgramNodeCatalog.AEROMANIP);
        var program = program(
                AeromanipProgramNodeCatalog.AEROMANIP,
                List.of(
                        node(0, entryId, entryId, new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                                definition.nodeLookup(), legacyConfiguration),
                        directionNode(3, 0.0, 1.0, 0.0)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "block"),
                        edge(3, "direction", 2, "direction")
                )
        );

        var result = ProgramGraphMigrations.migrate(program, definition.nodeLookup());

        assertTrue(result.changed());
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertEquals(1, result.program().schemaVersion(),
                "migration must not bump the program schema version");
        var nodes = result.program().graph().nodes();
        assertEquals(6, nodes.size());
        assertNull(find(nodes, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE));
        var extract = require(nodes, AeromanipProgramNodeIds.STRUCTURE_EXTRACT);
        var control = require(nodes, AeromanipProgramNodeIds.STRUCTURE_CONTROL);
        var launch = require(nodes, AeromanipProgramNodeIds.STRUCTURE_LAUNCH);
        var extractConfiguration = extract.configuration().getAsJsonObject();
        assertEquals(4, extractConfiguration.get("radius").getAsInt(),
                "the legacy structure radius becomes the capture radius");
        assertEquals(0, extractConfiguration.get("budget").getAsInt());
        var launchConfiguration = launch.configuration().getAsJsonObject();
        assertEquals(1.5f, launchConfiguration.get("power").getAsFloat());
        assertEquals(6.0f, launchConfiguration.get("duration_seconds").getAsFloat());
        assertFalse(launchConfiguration.get("restore_when_settled").getAsBoolean());

        var edges = result.program().graph().edges();
        assertTrue(hasEdge(edges, 0, "flow", extract.id(), "flow"));
        assertTrue(hasEdge(edges, 1, "position", extract.id(), "host"));
        assertTrue(hasEdge(edges, 3, "direction", launch.id(), "direction"));
        assertTrue(hasEdge(edges, extract.id(), "flow", control.id(), "flow"));
        assertTrue(hasEdge(edges, extract.id(), "structure", control.id(), "structure"));
        assertTrue(hasEdge(edges, control.id(), "control", launch.id(), "control"));
        assertEquals(7, edges.size());

        var compiled = definition.compile(result.program(), Set.of(
                org.academy.internal.common.ability.aeromanip.program
                        .AeromanipProgramCapabilities.STRUCTURE));
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());

        var again = ProgramGraphMigrations.migrate(result.program(), definition.nodeLookup());
        assertFalse(again.changed(), "migration must be idempotent");
    }

    @Test
    void legacyBinaryEnvelopeIsMigratedAfterDecode() {
        var definition = AbilityProgramDefinitions.require(AeromanipProgramNodeCatalog.AEROMANIP);
        var entryId = BaseAbilityProgramDefinition.entryId(AeromanipProgramNodeCatalog.AEROMANIP);
        var legacy = program(
                AeromanipProgramNodeCatalog.AEROMANIP,
                List.of(
                        node(0, entryId, entryId, new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                                definition.nodeLookup(), legacyLaunchConfiguration(1.0f, 2, 8, true)),
                        directionNode(3, 0.0, 1.0, 0.0)
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "block"),
                        edge(3, "direction", 2, "direction")
                )
        );
        var encoded = ProgramBookCodec.encodeProgram(legacy);

        var decoded = ProgramBookCodec.decodeProgram(encoded);
        assertTrue(decoded.valid());
        var migrated = ProgramGraphMigrations.migrate(decoded.program(), definition.nodeLookup());
        assertTrue(migrated.changed());
        assertEquals(ProgramBook.CURRENT_SCHEMA_VERSION, 1,
                "the envelope version must stay compatible");
        assertNotNull(find(migrated.program().graph().nodes(),
                AeromanipProgramNodeIds.STRUCTURE_LAUNCH));
        assertTrue(definition.compile(migrated.program(), Set.of(
                        org.academy.internal.common.ability.aeromanip.program
                                .AeromanipProgramCapabilities.STRUCTURE))
                .valid());
    }

    @Test
    void unmigratableLegacyConfigurationIsReportedPerNodeAndTheNodeIsKept() {
        var definition = AbilityProgramDefinitions.require(AeromanipProgramNodeCatalog.AEROMANIP);
        var entryId = BaseAbilityProgramDefinition.entryId(AeromanipProgramNodeCatalog.AEROMANIP);
        var invalid = legacyLaunchConfiguration(1.0f, 99, 8, true);
        var program = program(
                AeromanipProgramNodeCatalog.AEROMANIP,
                List.of(
                        node(0, entryId, entryId, new JsonObject()),
                        node(2, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                                definition.nodeLookup(), invalid)
                ),
                List.of(edge(0, "flow", 2, "flow"))
        );

        var result = ProgramGraphMigrations.migrate(program, definition.nodeLookup());

        assertFalse(result.changed());
        assertSame(program, result.program());
        assertEquals(1, result.diagnostics().size(), () -> result.diagnostics().toString());
        assertEquals(ProgramGraphMigrator.Diagnostic.LEGACY_ACTION_NOT_MIGRATED,
                result.diagnostics().getFirst().code());
        assertEquals(
                org.academy.internal.common.ability.aeromanip.program
                        .AeromanipProgramGraphMigrations.LEGACY_UNPARSEABLE_CONFIGURATION,
                result.diagnostics().getFirst().detail());
        assertEquals(2, result.diagnostics().getFirst().nodeId());
        var compiled = definition.compile(result.program(), Set.of(
                org.academy.internal.common.ability.aeromanip.program
                        .AeromanipProgramCapabilities.STRUCTURE));
        assertFalse(compiled.valid());
        assertFalse(compiled.diagnostics().isEmpty());
        assertTrue(compiled.diagnostics().stream().allMatch(diagnostic ->
                        diagnostic.nodeId() == 2),
                "only the unparseable node is flagged, not the whole graph");
        assertNotNull(find(result.program().graph().nodes(),
                AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE));
    }

    @Test
    void acceleratorGraphsWithoutLegacyFormatsMigrateAsAVerifiedNoOp() {
        var definition = AbilityProgramDefinitions.require(AcceleratorProgramNodeCatalog.ACCELERATOR);
        var entryId = BaseAbilityProgramDefinition.entryId(
                AcceleratorProgramNodeCatalog.ACCELERATOR);
        var program = program(
                AcceleratorProgramNodeCatalog.ACCELERATOR,
                List.of(
                        node(0, entryId, entryId, new JsonObject()),
                        node(1, CommonProgramNodeIds.TEXT_CONSTANT,
                                definition.nodeLookup(), new JsonObject())
                ),
                List.of()
        );

        var result = ProgramGraphMigrations.migrate(program, definition.nodeLookup());

        assertFalse(result.changed());
        assertSame(program, result.program());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, program.schemaVersion());
        assertTrue(ProgramGraphMigrations.registeredCategories().contains(
                AcceleratorProgramNodeCatalog.ACCELERATOR));
    }

    @Test
    void electromasterBlockTargetedMagneticMoveIsDiagnosedWithoutRejectingTheGraph() {
        var definition = AbilityProgramDefinitions.require(
                ElectromasterProgramNodeCatalog.ELECTROMASTER);
        var entryId = BaseAbilityProgramDefinition.entryId(
                ElectromasterProgramNodeCatalog.ELECTROMASTER);
        var blockTarget = new JsonObject();
        blockTarget.addProperty("power", 1.0f);
        blockTarget.addProperty("target_type", "block");
        blockTarget.addProperty("mode", "pull");
        blockTarget.addProperty("force_magnetize", false);
        var program = program(
                ElectromasterProgramNodeCatalog.ELECTROMASTER,
                List.of(
                        node(0, entryId, entryId, new JsonObject()),
                        node(1, ElectromasterProgramNodeIds.MAGNETIC_MOVE,
                                definition.nodeLookup(), blockTarget)
                ),
                List.of(edge(0, "flow", 1, "flow"))
        );

        var result = ProgramGraphMigrations.migrate(program, definition.nodeLookup());

        assertFalse(result.changed());
        assertEquals(1, result.diagnostics().size());
        assertEquals(ProgramGraphMigrator.Diagnostic.LEGACY_ACTION_NOT_MIGRATED,
                result.diagnostics().getFirst().code());
        assertEquals(ElectromasterProgramGraphMigrations.LEGACY_MAGNETIC_BLOCK_TARGET,
                result.diagnostics().getFirst().detail());
        assertNotNull(find(result.program().graph().nodes(),
                ElectromasterProgramNodeIds.MAGNETIC_MOVE),
                "the legacy node stays available for its explicit runtime rejection");

        var entityTarget = blockTarget.deepCopy();
        entityTarget.addProperty("target_type", "entity");
        var entityProgram = program(
                ElectromasterProgramNodeCatalog.ELECTROMASTER,
                List.of(node(1, ElectromasterProgramNodeIds.MAGNETIC_MOVE,
                        definition.nodeLookup(), entityTarget)),
                List.of()
        );
        var entityResult = ProgramGraphMigrations.migrate(entityProgram, definition.nodeLookup());
        assertTrue(entityResult.diagnostics().isEmpty());
        assertFalse(entityResult.changed());
    }

    @Test
    void registeredSchemaUpgradesRewriteOldNodeVersionsAndKeepUnmappedOnes() {
        var category = Identifier.parse("academy:test_migration");
        var nodeId = Identifier.parse("academy:test_migration/action/sample");
        ProgramNodeLookup lookup = id -> id.equals(nodeId) ? testType(category, 2) : null;
        var migrator = ProgramGraphMigrator.builder()
                .schemaUpgrade(nodeId, 1, configuration -> {
                    configuration.addProperty("migrated", true);
                    return configuration;
                })
                .build();
        var old = program(category, List.of(new ProgramGraph.Node(
                0, nodeId, 1, configuration("value", 3))), List.of());

        var result = migrator.migrate(old, lookup);

        assertTrue(result.changed());
        assertTrue(result.diagnostics().isEmpty());
        var migrated = result.program().graph().nodes().getFirst();
        assertEquals(2, migrated.schemaVersion());
        assertEquals(3, migrated.configuration().getAsJsonObject().get("value").getAsInt());
        assertTrue(migrated.configuration().getAsJsonObject().get("migrated").getAsBoolean());

        var unmapped = ProgramGraphMigrator.EMPTY.migrate(old, lookup);
        assertFalse(unmapped.changed());
        assertEquals(ProgramGraphMigrator.Diagnostic.UNSUPPORTED_NODE_SCHEMA,
                unmapped.diagnostics().getFirst().code());
        assertEquals(0, unmapped.diagnostics().getFirst().nodeId());

        var newer = new ProgramGraph.Node(0, nodeId, 3, configuration("value", 3));
        var newerResult = migrator.migrate(
                program(category, List.of(newer), List.of()), lookup);
        assertEquals(ProgramGraphMigrator.Diagnostic.UNSUPPORTED_NODE_SCHEMA,
                newerResult.diagnostics().getFirst().code());
    }

    @Test
    void mixedLegacyGraphsRewriteWhatTheyCanAndReportEachFailureOnce() {
        var definition = AbilityProgramDefinitions.require(AeromanipProgramNodeCatalog.AEROMANIP);
        var entryId = BaseAbilityProgramDefinition.entryId(AeromanipProgramNodeCatalog.AEROMANIP);
        var program = program(
                AeromanipProgramNodeCatalog.AEROMANIP,
                List.of(
                        node(0, entryId, entryId, new JsonObject()),
                        blockPositionNode(1, 10, 64, 10),
                        node(2, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                                definition.nodeLookup(), legacyLaunchConfiguration(1.5f, 4, 6, false)),
                        directionNode(3, 0.0, 1.0, 0.0),
                        node(4, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                                definition.nodeLookup(), legacyLaunchConfiguration(1.0f, 99, 8, true))
                ),
                List.of(
                        edge(0, "flow", 2, "flow"),
                        edge(1, "position", 2, "block"),
                        edge(3, "direction", 2, "direction"),
                        edge(2, "flow", 4, "flow")
                )
        );

        var result = ProgramGraphMigrations.migrate(program, definition.nodeLookup());

        assertTrue(result.changed());
        assertEquals(1, result.diagnostics().size(), () -> result.diagnostics().toString());
        assertEquals(ProgramGraphMigrator.Diagnostic.LEGACY_ACTION_NOT_MIGRATED,
                result.diagnostics().getFirst().code());
        assertEquals(4, result.diagnostics().getFirst().nodeId());
        var nodes = result.program().graph().nodes();
        assertNotNull(find(nodes, AeromanipProgramNodeIds.STRUCTURE_LAUNCH),
                "the migratable legacy node must be rewritten");
        assertEquals(1, nodes.stream()
                .filter(node -> node.type().equals(AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE))
                .count(), "only the unparseable node stays in its legacy form");
        assertTrue(result.program().graph().edges().stream().anyMatch(edge ->
                        edge.to().nodeId() == 4 && edge.to().port().equals("flow")),
                "the retained legacy node keeps its flow wiring");
    }

    private static ProgramNodeType<JsonObject> testType(Identifier category, int schemaVersion) {
        return new ProgramNodeType<>() {
            @Override
            public Codec<JsonObject> configurationCodec() {
                return com.mojang.serialization.MapCodec.unit(new JsonObject()).codec();
            }

            @Override
            public int schemaVersion() {
                return schemaVersion;
            }

            @Override
            public ProgramNodeSchema schema(JsonObject configuration) {
                return new ProgramNodeSchema(
                        List.of(),
                        List.of(org.academy.api.common.ability.program.ProgramPortDefinition.output(
                                "flow", ProgramValueTypes.FLOW)));
            }

            @Override
            public ProgramNodeRole role() {
                return ProgramNodeRole.ACTION;
            }

            @Override
            public ProgramNodePurity purity() {
                return ProgramNodePurity.PURE;
            }

            @Override
            public ProgramNodeScope scope() {
                return ProgramNodeScope.category(category);
            }
        };
    }

    private static JsonObject legacyLaunchConfiguration(
            float power, int radius, int duration, boolean restoreWhenSettled
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("power", power);
        configuration.addProperty("radius", radius);
        configuration.addProperty("duration", duration);
        configuration.addProperty("restore_when_settled", restoreWhenSettled);
        return configuration;
    }

    private static JsonObject configuration(String key, int value) {
        var configuration = new JsonObject();
        configuration.addProperty(key, value);
        return configuration;
    }

    private static AbilityProgram program(
            Identifier category,
            List<ProgramGraph.Node> nodes,
            List<ProgramGraph.Edge> edges
    ) {
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "migration fixture",
                category,
                new ProgramGraph(nodes, edges),
                ProgramEditorLayout.EMPTY
        );
    }

    private static ProgramGraph.Node node(
            int id,
            Identifier type,
            ProgramNodeLookup lookup,
            JsonObject configuration
    ) {
        var nodeType = lookup.find(type);
        assertNotNull(nodeType, type.toString());
        return new ProgramGraph.Node(id, type, nodeType.schemaVersion(), configuration);
    }

    private static ProgramGraph.Node node(
            int id,
            Identifier type,
            Identifier expected,
            JsonObject configuration
    ) {
        assertEquals(expected, type);
        return new ProgramGraph.Node(id, type, 1, configuration);
    }

    private static ProgramGraph.Node blockPositionNode(int id, int x, int y, int z) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", "minecraft:overworld");
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return new ProgramGraph.Node(
                id, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node directionNode(int id, double x, double y, double z) {
        var configuration = new JsonObject();
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return new ProgramGraph.Node(
                id, CommonProgramNodeIds.DIRECTION_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Edge edge(
            int fromNode, String fromPort, int toNode, String toPort
    ) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(fromNode, fromPort),
                new ProgramGraph.Endpoint(toNode, toPort)
        );
    }

    private static ProgramGraph.Node find(
            List<ProgramGraph.Node> nodes,
            Identifier type
    ) {
        return nodes.stream().filter(node -> node.type().equals(type)).findFirst().orElse(null);
    }

    private static ProgramGraph.Node require(
            List<ProgramGraph.Node> nodes,
            Identifier type
    ) {
        var node = find(nodes, type);
        assertNotNull(node, type.toString());
        return node;
    }

    private static boolean hasEdge(
            List<ProgramGraph.Edge> edges,
            int fromNode, String fromPort, int toNode, String toPort
    ) {
        return edges.stream().anyMatch(edge ->
                edge.from().nodeId() == fromNode && edge.from().port().equals(fromPort)
                        && edge.to().nodeId() == toNode && edge.to().port().equals(toPort));
    }
}
