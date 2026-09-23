package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.client.gui.screen.ScreenDispatcher;
import org.academy.internal.client.ability.mentalout.ModularProgramEditorSession;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.client.ability.program.ProgramStarterTemplates;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.PrecisionProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in rendering probe for the real program editor; excluded from the shipped mod. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ProgramEditorClientSmoke {
    private static int ticks;
    private static int loadingTicks;
    private static volatile int captures;
    private static SmokeSession session;

    private ProgramEditorClientSmoke() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.programEditorSmoke")) return;
        var minecraft = Minecraft.getInstance();
        if (session == null) {
            if (++loadingTicks > 600) {
                throw new IllegalStateException("Program editor startup timed out: " + minecraft.gui.screen());
            }
            if (minecraft.gui.screen() instanceof LoadingErrorScreen warnings) {
                var continueLabel = FMLTranslations.parseMessage("fml.button.continue.launch");
                for (var widget : warnings.children()) {
                    if (widget instanceof Button button && button.getMessage().getString().equals(continueLabel)) {
                        button.onPress(new MouseButtonEvent(0, 0, new MouseButtonInfo(0, 0)));
                        break;
                    }
                }
            }
            if (!(minecraft.gui.screen() instanceof TitleScreen)) return;
            minecraft.options.pauseOnLostFocus = false;
            session = new SmokeSession();
            minecraft.gui.setScreen(new ModularProgramScreen(session));
        }
        ticks++;
        if (ticks == 100) capture("empty");
        if (ticks == 110) {
            requireStarter(true);
            clickStarter(35, 95);
            requireStarter(false);
            if (session.playerData.isProgramStarterDismissed()) {
                throw new IllegalStateException("Temporary close changed the saved preference");
            }
            // The old template hit target must no longer create nodes after closing.
            clickStarter(30, 30);
            if (!session.program.graph().nodes().isEmpty()) {
                throw new IllegalStateException("Hidden quick start accepted a click");
            }
        }
        if (ticks == 120) capture("closed");
        if (ticks == 130) minecraft.gui.setScreen(new ModularProgramScreen(session));
        if (ticks == 140) {
            requireStarter(true);
            clickStarter(140, 95);
            requireStarter(false);
            if (!session.playerData.isProgramStarterDismissed()) {
                throw new IllegalStateException("Permanent close did not save the preference");
            }
        }
        if (ticks == 150) capture("dismissed-world");
        if (ticks == 160) {
            var gson = WorldData.createGson();
            session.playerData = gson.fromJson(gson.toJson(session.playerData), Player.class);
            minecraft.gui.setScreen(new ModularProgramScreen(session));
        }
        if (ticks == 170) requireStarter(false);
        if (ticks == 180) {
            session = new SmokeSession();
            minecraft.gui.setScreen(new ModularProgramScreen(session));
        }
        if (ticks == 190) requireStarter(true);
        if (ticks == 210) {
            session.program = Boolean.getBoolean("academy.programTextNodesSmoke") ? textProgram()
                    : ProgramStarterTemplates.create(session.emptyProgram(0),
                    AbilityProgramDefinitions.mentalout(), Set.of(), ProgramStarterTemplates.Kind.CHAT,
                    Component.translatable("screen.academy.program.starter.chat_keyword").getString(),
                    Component.translatable("screen.academy.program.starter.output").getString());
            minecraft.gui.setScreen(new ModularProgramScreen(session));
        }
        if (ticks == 250) capture("nodes");
        if (Boolean.getBoolean("academy.programTextNodesSmoke")) {
            if (ticks == 260) inspectNode(2);
            if (ticks == 275) capture("text-split-inspector");
            if (ticks == 277) {
                var current = (ModularProgramScreen) minecraft.gui.screen();
                var zoom = doubleField(current, "zoom");
                for (var index = 0; index < 10; index++) {
                    current.mouseScrolled(canvasField(current, "canvasX") + canvasField(current, "canvasW") - 15,
                            canvasField(current, "canvasY") + canvasField(current, "canvasH") - 15, 0, -1);
                }
                if (doubleField(current, "zoom") != zoom) {
                    throw new IllegalStateException("Inspector scrolling changed canvas zoom");
                }
                if (canvasField(current, "canvasH") < 300 && canvasField(current, "inspectorPortScroll") == 0) {
                    throw new IllegalStateException("Overflowing inspector ports did not scroll");
                }
            }
            if (ticks == 280) capture("text-split-ports-scrolled");
            if (ticks == 285) inspectNode(3);
            if (ticks == 300) capture("text-vec3-inspector");
        }
        var textNodes = Boolean.getBoolean("academy.programTextNodesSmoke");
        if (ticks >= (textNodes ? 320 : 280) && captures == (textNodes ? 7 : 4)) {
            System.out.println("[program-editor-smoke] PASSED: close, reopen, hidden hit targets, world dismissal, reload, other world, populated editor");
            minecraft.stop();
        }
        if (ticks > 400) throw new IllegalStateException("Program editor captures timed out");
    }

    private static AbilityProgram textProgram() {
        var definition = AbilityProgramDefinitions.mentalout();
        var ids = List.of(CommonProgramNodeIds.TRIGGER_CHAT, CommonProgramNodeIds.CHAT_TRIGGER_MESSAGE,
                CommonProgramNodeIds.TEXT_SPLIT, CommonProgramNodeIds.TEXT_TO_VEC3, CommonProgramNodeIds.BRANCH);
        var nodes = new ArrayList<ProgramGraph.Node>();
        for (var id : ids) {
            var entry = definition.editorCatalog().entry(id);
            nodes.add(new ProgramGraph.Node(nodes.size(), id, entry.type().schemaVersion(), entry.defaultConfiguration()));
        }
        var graph = new ProgramGraph(nodes, List.of(textEdge(0, "flow", 4, "flow"),
                textEdge(1, "text", 2, "text"), textEdge(2, "text", 3, "text"),
                textEdge(3, "success", 4, "condition")));
        if (!definition.compile(graph, Set.of()).valid()) throw new IllegalStateException("Text graph did not compile");
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), "Chat text processing",
                definition.category(), graph, new ProgramEditorLayout(Map.of(
                0, new ProgramEditorLayout.NodePosition(0, -120),
                1, new ProgramEditorLayout.NodePosition(0, 0),
                2, new ProgramEditorLayout.NodePosition(135, 0),
                3, new ProgramEditorLayout.NodePosition(270, 0),
                4, new ProgramEditorLayout.NodePosition(405, 0))));
    }

    private static ProgramGraph.Edge textEdge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static void inspectNode(int id) {
        try {
            var screen = Minecraft.getInstance().gui.screen();
            var select = ModularProgramScreen.class.getDeclaredMethod("selectNode", int.class);
            select.setAccessible(true);
            select.invoke(screen, id);
            var drawer = ModularProgramScreen.class.getDeclaredField("rightDrawerOpen");
            drawer.setAccessible(true);
            drawer.setBoolean(screen, true);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect text node", failure);
        }
    }

    private static void requireStarter(boolean expected) {
        try {
            var screen = Minecraft.getInstance().gui.screen();
            var method = ModularProgramScreen.class.getDeclaredMethod("showStarter");
            method.setAccessible(true);
            if (!Boolean.valueOf(expected).equals(method.invoke(screen))) {
                throw new IllegalStateException("Unexpected quick-start visibility at tick " + ticks);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect quick-start visibility", failure);
        }
    }

    private static void clickStarter(int offsetX, int offsetY) {
        var screen = (ModularProgramScreen) Minecraft.getInstance().gui.screen();
        var x = canvasField(screen, "canvasX");
        var y = canvasField(screen, "canvasY");
        var width = canvasField(screen, "canvasW");
        var height = canvasField(screen, "canvasH");
        screen.mouseClicked(new MouseButtonEvent(x + (width - Math.min(220, width - 24)) / 2 + offsetX,
                y + (height - 110) / 2 + offsetY, new MouseButtonInfo(0, 0)), false);
    }

    private static int canvasField(ModularProgramScreen screen, String name) {
        try {
            var field = ModularProgramScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect editor canvas", failure);
        }
    }

    private static double doubleField(ModularProgramScreen screen, String name) {
        try {
            var field = ModularProgramScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getDouble(screen);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect editor state", failure);
        }
    }

    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.programEditorSmokeOutput"), name + ".png");
        var target = ScreenDispatcher.Companion.getRenderTarget();
        if (target.width <= 1 || target.height <= 1) {
            throw new IllegalStateException("Program editor render target is not ready");
        }
        Screenshot.takeScreenshot(target, image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
                captures++;
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot capture program editor", failure);
            }
        });
    }

    private static final class SmokeSession implements ModularProgramEditorSession {
        private final AbilityProgram empty = new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), "UI regression", PrecisionProgramNodeCatalog.MENTALOUT,
                ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY);
        private AbilityProgram program = empty;
        private Player playerData = new Player();

        @Override
        public boolean showStarter() { return !playerData.isProgramStarterDismissed(); }

        @Override
        public void dismissStarterForWorld() { playerData.dismissProgramStarter(); }

        @Override
        public Component title() {
            return Component.translatable("screen.academy.program.editor.title", "Mental Out");
        }

        @Override
        public int slotCount() { return 10; }

        @Override
        public int selectedSlot() { return 0; }

        @Override
        public long revision() { return 0; }

        @Override
        public AbilityProgram editableProgram(int slot) { return program; }

        @Override
        public AbilityProgram emptyProgram(int slot) { return empty; }

        @Override
        public AbilityProgram restoredProgram(int slot) { return program; }

        @Override
        public Set<Identifier> capabilities() { return Set.of(); }

        @Override
        public void updateLocalProgram(int slot, AbilityProgram updated) { program = updated; }

        @Override
        public void selectSlot(int slot) {
        }

        @Override
        public void saveProgram(int slot, AbilityProgram updated, long expectedRevision) {
        }

        @Override
        public void closed(ModularProgramScreen screen) {
        }
    }
}
