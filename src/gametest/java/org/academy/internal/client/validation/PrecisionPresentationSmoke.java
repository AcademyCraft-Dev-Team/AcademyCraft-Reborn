package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.client.ability.mentalout.ModularProgramEditorSession;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.client.ability.mentalout.ProgramUiGraphics;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;

import java.nio.file.Files;
import java.util.*;

/** Opt-in presentation check: real editor/resources, no save or execution packets or world edits. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionPresentationSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionPresentationSmoke");
    private static final List<Scene> SCENES = List.of(
            new Scene("extraction", "accelerator", "action/structure/extract", "spatial/block_position", "position", "host", 30),
            new Scene("charge", "aeromanip", "action/laminar_cut", "spatial/direction", "direction", "direction", 30),
            new Scene("unreleased", "accelerator", "action/structure/staticize", "query/look_structure", "structure", "structure", 30),
            new Scene("legacy", "accelerator", null, "value/float", null, null, 140),
            new Scene("two_node_move", "accelerator", "action/structure/move_group", null, null, null, 30));
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static int index;
    private static int ticks;
    private static int totalTicks;
    private static volatile boolean saved;
    private static volatile Throwable screenshotFailure;
    private static boolean finished;
    private static String language;
    private static ModularProgramScreen screen;
    private static AbilityProgram original;
    private static int selectedId;

    private PrecisionPresentationSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        try {
            var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
            check(directory.getParent() != null && directory.getParent().getFileName().toString().equals("run")
                    && directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"), "isolated directory required");
            check(++totalTicks < 1200, "presentation timeout");
            if (screenshotFailure != null) throw new IllegalStateException("screenshot failed", screenshotFailure);
            if (screen != null) check(client.gui.screen() == screen, "presentation screen was replaced");
            if (saved) {
                assertEditorUnchanged();
                RESULTS.add(Map.of("scene", SCENES.get(index).name(), "language", language,
                        "visible_ticks", ticks, "graph_compiled", true, "codec_round_trip", true,
                        "editor_graph_unchanged", true, "screenshot", "screenshots/precision-" + language + "-" + SCENES.get(index).name() + ".png"));
                saved = false;
                ticks = 0;
                index++;
                if (index == SCENES.size()) {
                    write(client, true, null);
                    finished = true;
                    client.stop();
                    return;
                }
            }
            var scene = SCENES.get(index);
            if (++ticks == 1) open(client, scene);
            if (ticks == scene.captureTick()) {
                var name = "precision-" + language + "-" + scene.name() + ".png";
                check(!Files.exists(directory.resolve("screenshots").resolve(name)), "fresh screenshot path required");
                Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {
                    try {
                        var path = directory.resolve("screenshots").resolve(name);
                        check(Files.isRegularFile(path) && Files.size(path) > 0, message.getString());
                        saved = true;
                    } catch (Throwable failure) { screenshotFailure = failure; }
                });
            }
            check(ticks < scene.captureTick() + 100, "screenshot callback timeout");
        } catch (Throwable failure) {
            AcademyCraft.LOGGER.error("PRECISION_PRESENTATION_FAILED", failure);
            try { write(client, false, failure); } catch (Exception ignored) { }
            finished = true;
            client.stop();
        }
    }

    private static void open(Minecraft client, Scene scene) throws Exception {
        language = client.getLanguageManager().getSelected();
        check(Set.of("zh_cn", "en_us").contains(language), "unexpected language " + language);
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        var category = Identifier.parse("academy:" + scene.category());
        var definition = AbilityProgramDefinitions.require(category);
        var catalog = definition.editorCatalog();
        var ids = new ArrayList<Identifier>();
        ids.add(BaseAbilityProgramDefinition.entryId(category));
        if (scene.source() != null) ids.add(Identifier.parse("academy:program/core/" + scene.source()));
        if (scene.action() != null) ids.add(Identifier.parse("academy:program/" + scene.category() + "/" + scene.action()));
        selectedId = ids.size() - 1;
        var nodes = new ArrayList<ProgramGraph.Node>();
        var positions = new HashMap<Integer, ProgramEditorLayout.NodePosition>();
        var capabilities = new HashSet<Identifier>();
        for (int i = 0; i < ids.size(); i++) {
            var entry = Objects.requireNonNull(catalog.entry(ids.get(i)));
            nodes.add(new ProgramGraph.Node(i, entry.id(), entry.type().schemaVersion(), entry.defaultConfiguration()));
            positions.put(i, new ProgramEditorLayout.NodePosition(i == selectedId ? 145 : 0, i == 1 && i != selectedId ? 90 : 0));
            capabilities.addAll(entry.type().scope().requiredCapabilities());
            check(!Component.translatable(entry.translationKey()).getString().equals(entry.translationKey()), "missing label " + entry.id());
        }
        var edges = new ArrayList<ProgramGraph.Edge>();
        if (scene.action() != null) edges.add(edge(0, "flow", selectedId, "flow"));
        if (scene.sourcePort() != null) edges.add(edge(1, scene.sourcePort(), selectedId, scene.targetPort()));
        original = new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(),
                "Presentation " + scene.name(), category, new ProgramGraph(nodes, edges), new ProgramEditorLayout(positions));
        var compiled = definition.compile(original, capabilities);
        check(compiled.valid(), "invalid example " + scene.name() + ": " + compiled.diagnostics());
        var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(original));
        check(decoded.valid() && original.equals(decoded.program()), "codec changed example");
        screen = new ModularProgramScreen(new ModularProgramEditorSession() {
            @Override public Component title() { return Component.literal("Precision A / " + scene.name()); }
            @Override public int slotCount() { return 1; }
            @Override public int selectedSlot() { return 0; }
            @Override public long revision() { return 0; }
            @Override public AbilityProgram editableProgram(int slot) { return original; }
            @Override public AbilityProgram emptyProgram(int slot) { return original; }
            @Override public AbilityProgram restoredProgram(int slot) { return null; }
            @Override public Set<Identifier> capabilities() { return Set.copyOf(capabilities); }
            @Override public void updateLocalProgram(int slot, AbilityProgram value) { check(original.equals(value), "unexpected edit"); }
            @Override public void selectSlot(int slot) {}
            @Override public void saveProgram(int slot, AbilityProgram value, long revision) { throw new IllegalStateException("save forbidden in presentation fixture"); }
            @Override public void closed(ModularProgramScreen value) {}
            @Override public boolean showStarter() { return false; }
        });
        client.gui.setScreen(screen);
        // Select through the same private editor operation used by a click; no production test hook.
        var select = ModularProgramScreen.class.getDeclaredMethod("selectNode", int.class);
        select.setAccessible(true);
        select.invoke(screen, selectedId);
        var description = ModularProgramScreen.class.getDeclaredMethod("nodeDescription", catalog.entry(ids.get(selectedId)).getClass());
        description.setAccessible(true);
        var text = ((Component) description.invoke(screen, catalog.entry(ids.get(selectedId)))).getString();
        check(!text.contains("screen.academy."), "unresolved description key");
        var widthMethod = ModularProgramScreen.class.getDeclaredMethod("inspectorWidth");
        widthMethod.setAccessible(true);
        float textWidth = (int) widthMethod.invoke(screen) - 10;
        var lines = ProgramUiGraphics.wrap(text, textWidth, ProgramUiGraphics.BODY_FONT_SIZE);
        check(String.join("", lines).equals(text.replace("\n", "")), "wrapped description lost characters or spaces");
        for (var line : lines) check(ProgramUiGraphics.fit(line, textWidth, ProgramUiGraphics.BODY_FONT_SIZE).equals(line),
                "wrapped description would be truncated: " + line);
        if (scene.name().equals("legacy") || scene.name().equals("unreleased")) {
            var status = catalog.entry(ids.get(selectedId)).presentationStatus();
            check(status.name().equals(scene.name().equals("legacy") ? "COMPATIBILITY" : "UNRELEASED"), "incorrect presentation state");
            check(text.contains(Component.translatable(status.titleTranslationKey()).getString())
                    && text.contains(Component.translatable(status.descriptionTranslationKey()).getString()), "missing presentation notice");
        }
        if (scene.name().equals("extraction") || scene.name().equals("charge")) {
            var field = scene.name().equals("extraction") ? "tier" : "charge_tier";
            var key = "screen.academy.program.configuration.field." + field;
            check(!Component.translatable(key).getString().equals(key), "unresolved configuration label " + field);
        }
    }

    private static void assertEditorUnchanged() throws Exception {
        var field = ModularProgramScreen.class.getDeclaredField("document");
        field.setAccessible(true);
        var edited = ((ProgramEditorDocument) field.get(screen)).program();
        check(original.equals(edited), "opening/presenting changed graph");
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static void write(Minecraft client, boolean passed, Throwable failure) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", passed);
        report.put("language", language);
        report.put("cases", RESULTS);
        report.put("world_mutation_or_save_packets", false);
        report.put("runtime_skill_execution", false);
        report.put("visual_review_required", true);
        report.put("failure", failure == null ? "" : failure.toString());
        Files.writeString(client.gameDirectory.toPath().resolve("precision-presentation-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        AcademyCraft.LOGGER.info("PRECISION_PRESENTATION_RESULT passed={} language={} cases={}", passed, language, RESULTS.size());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Scene(String name, String category, String action, String source,
                         String sourcePort, String targetPort, int captureTick) {}
}
