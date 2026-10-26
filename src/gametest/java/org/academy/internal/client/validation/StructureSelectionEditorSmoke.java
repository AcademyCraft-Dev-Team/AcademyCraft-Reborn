package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.client.ability.mentalout.ModularProgramEditorSession;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.common.ability.program.registry.*;

import java.nio.file.Files;
import java.util.*;

/** Actual editor render and language-resource verification in an isolated client; no save packets. */
final class StructureSelectionEditorSmoke {
    private static int ticks;
    private static volatile boolean saved;
    private static volatile RuntimeException failure;

    static boolean tick(Minecraft client) {
        if (failure != null) throw failure;
        if (saved) return true;
        if (++ticks == 1) {
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().handle());
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var category = Identifier.parse("academy:accelerator");
            var catalog = AbilityProgramDefinitions.require(category).editorCatalog();
            var types = List.of(CommonProgramNodeIds.NEAREST_STRUCTURE, CommonProgramNodeIds.STRUCTURES_AROUND,
                    CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION, CommonProgramNodeIds.FILTER_STRUCTURE_DISTANCE,
                    CommonProgramNodeCatalog.CollectionDomain.STRUCTURE.id("foreach"),
                    Identifier.parse("academy:program/accelerator/action/structure/control"));
            for (int index = 0; index < types.size(); index++) {
                var entry = catalog.entry(types.get(index));
                if (!entry.visible() || Component.translatable(entry.translationKey()).getString().equals(entry.translationKey())
                        || Component.translatable(entry.descriptionTranslationKey()).getString().equals(entry.descriptionTranslationKey())) {
                    throw new IllegalStateException("Missing editor node or translation: " + entry.id());
                }
            }
            var graphTypes = List.of(BaseAbilityProgramDefinition.entryId(category), CommonProgramNodeIds.WORLD_POSITION_CONSTANT,
                    CommonProgramNodeIds.FLOAT_CONSTANT, CommonProgramNodeIds.STRUCTURES_AROUND,
                    CommonProgramNodeCatalog.CollectionDomain.STRUCTURE.id("foreach"),
                    Identifier.parse("academy:program/accelerator/action/structure/control"),
                    Identifier.parse("academy:program/accelerator/action/structure/release"));
            var nodes = new ArrayList<ProgramGraph.Node>();
            for (int index = 0; index < graphTypes.size(); index++) {
                var entry = catalog.entry(graphTypes.get(index));
                var config = entry.defaultConfiguration();
                if (index == 2) config.getAsJsonObject().addProperty("value", 8.0);
                nodes.add(new ProgramGraph.Node(index, entry.id(), 1, config));
            }
            var positions = Map.of(0, new ProgramEditorLayout.NodePosition(0, 0),
                    1, new ProgramEditorLayout.NodePosition(0, 100), 2, new ProgramEditorLayout.NodePosition(0, 200),
                    3, new ProgramEditorLayout.NodePosition(220, 100), 4, new ProgramEditorLayout.NodePosition(220, 200),
                    5, new ProgramEditorLayout.NodePosition(440, 100), 6, new ProgramEditorLayout.NodePosition(440, 200));
            var edges = List.of(edge(0, "flow", 4, "flow"), edge(1, "position", 3, "center"), edge(2, "value", 3, "radius"),
                    edge(3, "structures", 4, "values"), edge(4, "value", 5, "structure"), edge(4, "body", 5, "flow"),
                    edge(5, "flow", 6, "flow"), edge(5, "control", 6, "control"));
            var program = new AbilityProgram(1, UUID.randomUUID(), "结构选取节点", category, new ProgramGraph(nodes, edges), new ProgramEditorLayout(positions));
            var compiled = AbilityProgramDefinitions.require(category).compile(program.graph(), Set.of(Identifier.parse("academy:kinetic_throw")));
            if (!compiled.valid()) throw new IllegalStateException("Editor example is invalid: " + compiled.diagnostics());
            client.gui.setScreen(new ModularProgramScreen(new ModularProgramEditorSession() {
                @Override public Component title() { return Component.literal("结构选取节点"); }
                @Override public int slotCount() { return 1; }
                @Override public int selectedSlot() { return 0; }
                @Override public long revision() { return 0; }
                @Override public AbilityProgram editableProgram(int slot) { return program; }
                @Override public AbilityProgram emptyProgram(int slot) { return program; }
                @Override public AbilityProgram restoredProgram(int slot) { return null; }
                @Override public Set<Identifier> capabilities() { return Set.of(Identifier.parse("academy:kinetic_throw")); }
                @Override public void updateLocalProgram(int slot, AbilityProgram value) {}
                @Override public void selectSlot(int slot) {}
                @Override public void saveProgram(int slot, AbilityProgram value, long revision) {}
                @Override public void closed(ModularProgramScreen screen) {}
                @Override public boolean showStarter() { return false; }
            }));
        }
        if (ticks == 30) {
            var name = "structure-selection-editor.png";
            Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {
                try {
                    var file = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
                    if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IllegalStateException(message.getString());
                    saved = true;
                } catch (Exception error) { failure = new IllegalStateException("Editor screenshot failed", error); }
            });
        }
        if (ticks > 200) throw new IllegalStateException("Editor screenshot timed out");
        return false;
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
}
