package org.academy.internal.common.ability.program.editor;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeIds;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeIds;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeIds;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.PrecisionProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.PrecisionProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramEditorPresentationTest {
    @Test
    void onlyTheThreeExactBuiltInRestorationIdsAreMarkedUnreleased() {
        var experimental = Set.of(AcceleratorProgramNodeIds.STRUCTURE_STATICIZE,
                ElectromasterProgramNodeIds.STRUCTURE_STATICIZE, AeromanipProgramNodeIds.STRUCTURE_STATICIZE);
        var observed = new java.util.HashSet<Identifier>();
        for (var definition : AbilityProgramDefinitions.all()) {
            for (var entry : definition.editorCatalog().entries()) {
                if (entry.presentationStatus() == ProgramEditorNodeCatalog.PresentationStatus.UNRELEASED) {
                    observed.add(entry.id());
                    assertTrue(entry.visible(), "The status must not hide an existing entry");
                    assertNotNull(definition.nodeLookup().find(entry.id()));
                    assertNotNull(definition.executors().find(entry.id()));
                    assertNotNull(definition.editorCatalog().schema(entry.id(), entry.defaultConfiguration()));
                }
            }
        }
        assertEquals(experimental, observed);
        var original = AbilityProgramDefinitions.mentalout().editorCatalog().entry(CommonProgramNodeIds.INTEGER_ADD);
        assertNotNull(original);
        for (var extensionId : List.of("thirdparty:program/accelerator/action/structure/staticize",
                "academy:program/thirdparty/action/structure/staticize")) {
            var extension = copyWithId(original, Identifier.parse(extensionId), true);
            assertEquals(ProgramEditorNodeCatalog.PresentationStatus.AVAILABLE, extension.presentationStatus());
        }
    }

    @Test
    void hiddenCompatibilityNodesStillLoadEditRoundTripAndCompile() {
        var definition = AbilityProgramDefinitions.mentalout();
        var document = new ProgramEditorDocument(new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), "Compatibility presentation",
                PrecisionProgramNodeCatalog.MENTALOUT, ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY),
                definition, Set.of());
        for (var type : List.of(PrecisionProgramNodeIds.ON_CAST, CommonProgramNodeIds.INTEGER_CONSTANT,
                CommonProgramNodeIds.INTEGER_LESS, CommonProgramNodeIds.BRANCH)) {
            document = document.addNode(type, 0, 0).orElseThrow();
        }
        document = document.connect(endpoint(0, "flow"), endpoint(3, "flow")).orElseThrow();
        document = document.connect(endpoint(1, "value"), endpoint(2, "left")).orElseThrow();
        document = document.connect(endpoint(1, "value"), endpoint(2, "right")).orElseThrow();
        document = document.connect(endpoint(2, "result"), endpoint(3, "condition")).orElseThrow();
        var edited = new JsonObject();
        edited.addProperty("value", 4);
        document = document.configureNode(1, edited).orElseThrow();
        assertTrue(document.validation().valid(), document.validation().diagnostics().toString());
        for (var type : List.of(CommonProgramNodeIds.INTEGER_CONSTANT, CommonProgramNodeIds.INTEGER_LESS)) {
            var entry = definition.editorCatalog().entry(type);
            assertNotNull(entry);
            assertFalse(entry.visible());
            assertEquals(ProgramEditorNodeCatalog.PresentationStatus.COMPATIBILITY, entry.presentationStatus());
        }
        var bytes = ProgramBookCodec.encodeProgram(document.program());
        var result = ProgramBookCodec.decodeProgram(bytes);
        assertTrue(result.valid(), result.diagnostic().toString());
        var decoded = result.program();
        assertNotNull(decoded);
        assertTrue(decoded.graph().nodes().stream().anyMatch(node -> node.type().equals(CommonProgramNodeIds.INTEGER_LESS)));
        assertTrue(definition.compile(decoded, Set.of()).valid());
        var restored = new ProgramEditorDocument(decoded, definition, Set.of());
        assertEquals(4, restored.program().graph().nodes().get(1).configuration().getAsJsonObject().get("value").getAsInt());
        assertTrue(restored.validation().valid());
    }

    @Test
    void presentationKeysAreIndependentOfVisibilityAndLookup() {
        var entry = AbilityProgramDefinitions.mentalout().editorCatalog().entry(CommonProgramNodeIds.INTEGER_ADD);
        assertNotNull(entry);
        assertNotNull(entry.presentationStatus().titleTranslationKey());
        assertNotNull(entry.presentationStatus().descriptionTranslationKey());
        var visible = copyWithId(entry, entry.id(), true);
        assertEquals(ProgramEditorNodeCatalog.PresentationStatus.AVAILABLE, visible.presentationStatus());
        assertNull(visible.presentationStatus().titleTranslationKey());
        assertNull(visible.presentationStatus().descriptionTranslationKey());
        assertSame(entry.type(), visible.type());
        assertEquals(entry.defaultConfiguration(), visible.defaultConfiguration());
        assertEquals(entry.defaultSchema(), visible.defaultSchema());
    }

    private static ProgramEditorNodeCatalog.Entry copyWithId(ProgramEditorNodeCatalog.Entry entry,
                                                            Identifier id, boolean visible) {
        return new ProgramEditorNodeCatalog.Entry(id, entry.type(), entry.defaultConfiguration(), entry.defaultSchema(),
                entry.group(), entry.displayName(), entry.translationKey(), entry.portTranslationPrefix(), visible, entry.metadata());
    }

    private static ProgramGraph.Endpoint endpoint(int nodeId, String port) {
        return new ProgramGraph.Endpoint(nodeId, port);
    }
}
