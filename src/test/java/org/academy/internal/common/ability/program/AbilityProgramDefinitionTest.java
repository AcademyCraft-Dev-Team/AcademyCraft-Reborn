package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityProgramDefinitionTest {
    @Test
    void mentaloutDefinitionComposesCategoryAndCommonRuntimeLayers() {
        var definition = AbilityProgramDefinitions.mentalout();

        assertEquals(PrecisionProgramNodeCatalog.MENTALOUT, definition.category());
        assertEquals(org.academy.api.common.ability.program.ProgramLimits.DEFAULT, definition.limits());
        assertSame(
                PrecisionProgramNodeCatalog.INSTANCE.find(PrecisionProgramNodeIds.ON_CAST),
                definition.nodeLookup().find(PrecisionProgramNodeIds.ON_CAST)
        );
        assertSame(
                CommonProgramNodeCatalog.INSTANCE.find(CommonProgramNodeIds.BOOLEAN_CONSTANT),
                definition.nodeLookup().find(CommonProgramNodeIds.BOOLEAN_CONSTANT)
        );
        assertNotNull(definition.executors().find(CommonProgramNodeIds.BOOLEAN_CONSTANT));
        assertNotNull(definition.executors().find(
                PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.CASTER)));
        assertEquals(
                CommonProgramNodeCatalog.INSTANCE.types().size()
                        + PrecisionProgramNodeCatalog.INSTANCE.types().size(),
                definition.editorCatalog().entries().size()
        );
    }

    @Test
    void definitionRejectsProgramFromAnotherAbilityCategoryBeforeCompilingGraph() {
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("76559468-8612-4471-92ed-9e6ed9f2baf7"),
                "Foreign category",
                AcademyCraft.academy("accelerator"),
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );

        var result = AbilityProgramDefinitions.mentalout().compile(program, Set.of());

        assertFalse(result.valid());
        assertEquals(ProgramDiagnosticCode.CATEGORY_MISMATCH,
                result.diagnostics().getFirst().code());
    }

    @Test
    void definitionAppliesItsOwnStructuralLimits() {
        var definition = AbilityProgramDefinitions.mentalout();
        var nodes = new ArrayList<ProgramGraph.Node>();
        for (var id = 0; id <= definition.limits().maxNodes(); id++) {
            var configuration = new JsonObject();
            configuration.addProperty("value", false);
            nodes.add(new ProgramGraph.Node(
                    id,
                    CommonProgramNodeIds.BOOLEAN_CONSTANT,
                    1,
                    configuration
            ));
        }

        var result = AbilityProgramDefinitions.mentalout().compile(
                new ProgramGraph(nodes, List.of()), Set.of());

        assertFalse(result.valid());
        assertEquals(ProgramDiagnosticCode.TOO_MANY_NODES,
                result.diagnostics().getFirst().code());
    }

    @Test
    void definitionIndexRejectsDuplicateAbilityCategories() {
        var definition = AbilityProgramDefinitions.mentalout();

        assertThrows(IllegalStateException.class, () ->
                AbilityProgramDefinitions.index(List.of(definition, definition)));
    }

    @Test
    void mentaloutEntryNodeCannotLeakIntoAnotherAbilityCategory() {
        var entry = PrecisionProgramNodeCatalog.INSTANCE.find(PrecisionProgramNodeIds.ON_CAST);

        assertNotNull(entry);
        assertTrue(entry.scope().allowsCategory(PrecisionProgramNodeCatalog.MENTALOUT));
        assertFalse(entry.scope().allowsCategory(AcademyCraft.academy("accelerator")));
    }

    @Test
    void everyBuiltInAbilityCategoryHasAnIsolatedCompilableDefinition() {
        var expected = Set.of(
                AcademyCraft.academy("electromaster"),
                AcademyCraft.academy("teleport"),
                AcademyCraft.academy("accelerator"),
                AcademyCraft.academy("meltdowner"),
                AcademyCraft.academy("aeromanip"),
                AcademyCraft.academy("darkmatter"),
                AcademyCraft.academy("mentalout")
        );
        assertEquals(expected, AbilityProgramDefinitions.all().stream()
                .map(AbilityProgramDefinition::category)
                .collect(java.util.stream.Collectors.toSet()));

        var entryIds = new java.util.HashSet<net.minecraft.resources.Identifier>();
        for (var category : expected) {
            var definition = AbilityProgramDefinitions.require(category);
            var entries = definition.categoryNodeTypes().entrySet().stream()
                    .filter(entry -> entry.getValue().role() == ProgramNodeRole.ENTRY)
                    .toList();
            assertEquals(1, entries.size(), category.toString());
            var entry = entries.getFirst();
            assertTrue(entryIds.add(entry.getKey()), entry.getKey().toString());
            assertEquals(Set.of(category), entry.getValue().scope().allowedCategories());
            assertTrue(definition.compile(new ProgramGraph(
                    List.of(new ProgramGraph.Node(
                            0,
                            entry.getKey(),
                            entry.getValue().schemaVersion(),
                            new JsonObject()
                    )),
                    List.of()
            ), Set.of()).valid(), category.toString());
        }
    }

    @Test
    void mentaloutEditorMergesSpatialNodesIntoTargetsAndHidesCanonicalAliases() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        for (var kind : List.of(
                PrecisionGraph.NodeKind.SIGHT_POSITION,
                PrecisionGraph.NodeKind.ENTITY_POSITION,
                PrecisionGraph.NodeKind.DIRECTION_BETWEEN,
                PrecisionGraph.NodeKind.POSITION_OFFSET
        )) {
            assertEquals(ProgramEditorNodeCatalog.Group.TARGET,
                    catalog.entry(PrecisionProgramNodeIds.id(kind)).group(), kind.name());
        }
        for (var kind : List.of(
                PrecisionGraph.NodeKind.ENTITY_TO_SET,
                PrecisionGraph.NodeKind.UNION,
                PrecisionGraph.NodeKind.INTERSECTION,
                PrecisionGraph.NodeKind.SUBTRACT_SET,
                PrecisionGraph.NodeKind.ENTITY_POSITION
        )) {
            assertFalse(catalog.entry(PrecisionProgramNodeIds.id(kind)).visible(), kind.name());
            assertNotNull(PrecisionProgramAliases.legacy(kind));
        }
        assertFalse(catalog.entry(PrecisionProgramNodeIds.id(
                PrecisionGraph.NodeKind.CASTER)).visible());
        assertTrue(catalog.entry(CommonProgramNodeIds.CASTER).visible());
        assertTrue(catalog.entry(CommonProgramNodeIds.LOOK_TARGET).visible());
        var controlledRoster = catalog.entry(PrecisionProgramNodeIds.id(
                PrecisionGraph.NodeKind.ROSTER));
        assertTrue(controlledRoster.categoryRestricted());
        assertEquals(PrecisionProgramNodeCatalog.MENTALOUT,
                controlledRoster.exclusiveCategory().orElseThrow());
        var commonTargetQuery = catalog.entry(CommonProgramNodeIds.ENTITIES_AROUND);
        assertFalse(commonTargetQuery.categoryRestricted());
        assertTrue(commonTargetQuery.exclusiveCategory().isEmpty());
    }
}
