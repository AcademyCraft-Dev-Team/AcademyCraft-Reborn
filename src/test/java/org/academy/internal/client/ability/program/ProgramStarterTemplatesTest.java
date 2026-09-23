package org.academy.internal.client.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.ProgramTriggers;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStarterTemplatesTest {
    @Test
    void examplesProduceEditableValidProgramsForAnEmptySlot() {
        var category = Identifier.parse("academy:starter_test");
        var definition = BaseAbilityProgramDefinition.create(category);
        var empty = new AbilityProgram(1, UUID.randomUUID(), "slot 1", category,
                ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY);

        for (var kind : ProgramStarterTemplates.Kind.values()) {
            var example = ProgramStarterTemplates.create(empty, definition, Set.of(), kind,
                    "start", "Ready");
            assertEquals(empty.id(), example.id());
            assertEquals(3, example.graph().nodes().size());
            assertEquals(2, example.graph().edges().size());
            assertTrue(definition.compile(example, Set.of()).valid());
            assertTrue(example.graph().nodes().stream().anyMatch(node ->
                    node.type().equals(CommonProgramNodeIds.DEBUG_OUTPUT)));
            if (kind == ProgramStarterTemplates.Kind.CHAT) {
                assertTrue(ProgramTriggers.matchesChat(example, "start", true));
                assertFalse(ProgramTriggers.matchesChat(example, "start", false));
            }
            assertThrows(IllegalArgumentException.class, () ->
                    ProgramStarterTemplates.create(example, definition, Set.of(), kind,
                            "start", "Ready"));
        }
    }
}
