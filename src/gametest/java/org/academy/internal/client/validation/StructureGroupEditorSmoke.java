package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.client.ability.mentalout.ModularProgramEditorSession;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;

import java.nio.file.Files;
import java.util.Set;

/** Displays the same valid graph that just ran on the real server. Never sends save/execute packets. */
public final class StructureGroupEditorSmoke {
    private static int ticks;
    private static volatile boolean saved;
    private static volatile RuntimeException failure;
    private StructureGroupEditorSmoke() {}

    public static boolean tick(Minecraft client, AbilityProgram program) {
        if (failure != null) throw failure;
        if (saved) return true;
        if (++ticks == 1) {
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().handle());
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var definition = AbilityProgramDefinitions.require(program.category());
            for (var node : program.graph().nodes()) if (node.type().getPath().endsWith("_group")) {
                var entry = definition.editorCatalog().entry(node.type());
                if (!entry.visible() || Component.translatable(entry.translationKey()).getString().equals(entry.translationKey())
                        || Component.translatable(entry.descriptionTranslationKey()).getString().equals(entry.descriptionTranslationKey())) {
                    throw new IllegalStateException("Missing group translation: " + node.type());
                }
            }
            var capability = Identifier.parse("academy:high_speed_jet");
            if (!definition.compile(program.graph(), Set.of(capability)).valid()) throw new IllegalStateException("Invalid editor group example");
            client.gui.setScreen(new ModularProgramScreen(new ModularProgramEditorSession() {
                @Override public Component title() { return Component.literal("群组姿态控制"); }
                @Override public int slotCount() { return 1; }
                @Override public int selectedSlot() { return 0; }
                @Override public long revision() { return 0; }
                @Override public AbilityProgram editableProgram(int slot) { return program; }
                @Override public AbilityProgram emptyProgram(int slot) { return program; }
                @Override public AbilityProgram restoredProgram(int slot) { return null; }
                @Override public Set<Identifier> capabilities() { return Set.of(capability); }
                @Override public void updateLocalProgram(int slot, AbilityProgram value) {}
                @Override public void selectSlot(int slot) {}
                @Override public void saveProgram(int slot, AbilityProgram value, long revision) {}
                @Override public void closed(ModularProgramScreen screen) {}
                @Override public boolean showStarter() { return false; }
            }));
        }
        if (ticks == (Boolean.getBoolean("academy.structureGroupMoveSmoke") ? 140 : 30)) Screenshot.grab(client.gameDirectory, "structure-groups-editor.png", client.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                var file = client.gameDirectory.toPath().resolve("screenshots/structure-groups-editor.png");
                if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IllegalStateException(message.getString());
                saved = true;
            } catch (Exception error) { failure = new IllegalStateException("Group screenshot failed", error); }
        });
        if (ticks > 200) throw new IllegalStateException("Group screenshot timed out");
        return false;
    }
}
