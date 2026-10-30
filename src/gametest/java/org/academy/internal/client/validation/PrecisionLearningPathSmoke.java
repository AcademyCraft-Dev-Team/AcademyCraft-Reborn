package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.academy.internal.client.ability.program.ProgramPaletteCatalog;
import org.academy.internal.client.ability.program.ProgramConfigurationOptions;
import org.academy.internal.client.ability.program.ProgramTaskTemplates;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Opt-in real editor checks through production UI handlers; never sends saves or runs graphs. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionLearningPathSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionLearningPathSmoke");
    private static final List<Scene> SCENES = List.of(
            new Scene("common_directory", "accelerator", Mode.COMMON, null),
            new Scene("structure_family", "accelerator", Mode.FAMILY, null),
            new Scene("advanced_search", "accelerator", Mode.SEARCH, null),
            new Scene("compatibility", "accelerator", Mode.COMPATIBILITY, null),
            new Scene("task_choices", "accelerator", Mode.CHOICES, null),
            new Scene("accelerator_task", "accelerator", Mode.TASK, ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_STRUCTURE),
            new Scene("electromaster_task", "electromaster", Mode.TASK, ProgramTaskTemplates.Kind.ELECTROMASTER_GUIDE_STRUCTURE),
            new Scene("aeromanip_task", "aeromanip", Mode.TASK, ProgramTaskTemplates.Kind.AEROMANIP_GUIDE_STRUCTURE),
            new Scene("teleport_task", "teleport", Mode.TASK, ProgramTaskTemplates.Kind.TELEPORT_FORWARD),
            new Scene("basic_starter", "accelerator", Mode.BASIC, null),
            new Scene("compact_drawer_tasks", "accelerator", Mode.COMPACT, null));
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<Map<String, Object>> MODEL_RESULTS = new ArrayList<>();
    private static final Map<String, Integer> INVOCATIONS = new TreeMap<>();
    private static final List<MemorySession> SESSIONS = new ArrayList<>();
    private static final Map<String, Object> TRANSLATIONS = new LinkedHashMap<>();
    private static int index;
    private static int ticks;
    private static int totalTicks;
    private static int originalWindowWidth;
    private static int originalWindowHeight;
    private static boolean initialized;
    private static boolean finished;
    private static volatile boolean screenshotSaved;
    private static volatile Throwable screenshotFailure;
    private static String language;
    private static ModularProgramScreen screen;
    private static MemorySession session;
    private static AbilityProgramDefinition definition;
    private static AbilityProgram expected;
    private static byte[] browseSnapshot;
    private static Map<String, Object> assertions;

    private PrecisionLearningPathSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        try {
            var directory = isolatedDirectory(client);
            check(!Boolean.getBoolean("academy.precisionPresentationSmoke"), "Do not enable both presentation fixtures");
            check(++totalTicks < 2000, "learning-path timeout");
            if (!initialized) initialize(client);
            if (screenshotFailure != null) throw new IllegalStateException("screenshot failed", screenshotFailure);
            if (screen != null) check(client.gui.screen() == screen, "learning-path screen was replaced");
            if (screenshotSaved) {
                assertCurrentProgram();
                RESULTS.add(sceneResult(client));
                screenshotSaved = false;
                ticks = 0;
                if (++index == SCENES.size()) {
                    check(SESSIONS.stream().mapToInt(value -> value.saveAttempts).sum() == 0, "save was attempted");
                    write(client, true, null);
                    finished = true;
                    restoreWindow(client);
                    client.stop();
                    return;
                }
            }
            var scene = SCENES.get(index);
            if (++ticks == 1) open(client, scene);
            if (scene.mode() == Mode.COMPACT && ticks == 12) {
                call("syncLayout");
                check((boolean) field("compactLeft") && (boolean) field("compactRight"), "compact layout was not reached");
                ensurePaletteVisible();
                openFamily("family.structure_move");
                assertBrowseUnchanged();
                clickPalette(paletteX() + paletteWidth() / 2.0, number("canvasY") + 31 + 6);
                check((boolean) field("leftDrawerOpen"), "compact palette drawer was not open");
                openTemplatesThroughPalette();
                check(!(boolean) field("leftDrawerOpen") && !(boolean) field("rightDrawerOpen"),
                        "task picker must close both compact drawers");
                var bounds = call("starterBounds");
                check(rect(bounds, "x") >= number("canvasX") && rect(bounds, "y") >= number("canvasY")
                        && rect(bounds, "x") + rect(bounds, "width") <= number("canvasX") + number("canvasW")
                        && rect(bounds, "y") + rect(bounds, "height") <= number("canvasY") + number("canvasH"),
                        "compact task picker exceeds the canvas");
                assertions.put("compact_drawer_and_family", true);
                assertions.put("compact_task_picker_closes_drawers_and_fits_canvas", true);
            }
            int captureTick = scene.mode() == Mode.COMPACT ? 45 : 30;
            if (ticks == captureTick) {
                assertCurrentProgram();
                check(client.gui.screen() == screen, "capture requires this fixture's editor");
                var name = screenshotName(scene);
                var path = directory.resolve("screenshots").resolve(name);
                check(!Files.exists(path), "fresh screenshot path required: " + path);
                Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {
                    try {
                        check(Files.isRegularFile(path) && Files.size(path) > 0, message.getString());
                        screenshotSaved = true;
                    } catch (Throwable failure) { screenshotFailure = failure; }
                });
            }
            check(ticks < captureTick + 100, "screenshot callback timeout");
        } catch (Throwable failure) {
            AcademyCraft.LOGGER.error("PRECISION_LEARNING_PATH_FAILED", failure);
            try { write(client, false, failure); } catch (Exception ignored) { }
            finished = true;
            restoreWindow(client);
            client.stop();
        }
    }

    private static void initialize(Minecraft client) throws Exception {
        language = client.getLanguageManager().getSelected();
        check(Set.of("zh_cn", "en_us").contains(language), "unexpected language " + language);
        originalWindowWidth = client.getWindow().getWidth();
        originalWindowHeight = client.getWindow().getHeight();
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        checkLearningTranslations(client);
        for (var value : AbilityProgramDefinitions.all()) {
            var capabilities = capabilities(value);
            for (var kind : ProgramTaskTemplates.available(value, capabilities)) {
                var empty = empty(value, "Model " + kind.name());
                var program = ProgramTaskTemplates.create(empty, value, capabilities, kind);
                check(program.id().equals(empty.id()) && program.name().equals(empty.name())
                        && program.category().equals(empty.category()), "template changed slot identity: " + kind);
                assertCompileAndCodec(value, capabilities, program);
                boolean rejected = false;
                try { ProgramTaskTemplates.create(program, value, capabilities, kind); }
                catch (IllegalArgumentException required) { rejected = true; }
                check(rejected, "template factory accepted a nonempty slot: " + kind);
                MODEL_RESULTS.add(Map.of("kind", kind.name(), "category", value.category().toString(),
                        "nodes", program.graph().nodes().size(), "edges", program.graph().edges().size(),
                        "compiled", true, "codec_round_trip", true, "nonempty_rejected", true));
            }
        }
        check(MODEL_RESULTS.size() == 10, "expected all ten built-in task recipes");
        initialized = true;
    }

    private static void open(Minecraft client, Scene scene) throws Exception {
        check(client.getLanguageManager().getSelected().equals(language), "language changed during acceptance");
        definition = AbilityProgramDefinitions.require(Identifier.parse("academy:" + scene.category()));
        var capabilities = capabilities(definition);
        boolean emptyScene = scene.mode() == Mode.CHOICES || scene.mode() == Mode.TASK
                || scene.mode() == Mode.BASIC || scene.mode() == Mode.COMPACT;
        var initial = empty(definition, "Learning path " + scene.name());
        if (!emptyScene) initial = ProgramTaskTemplates.create(initial, definition, capabilities,
                ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_STRUCTURE);
        session = new MemorySession(initial, capabilities);
        SESSIONS.add(session);
        screen = new ModularProgramScreen(session);
        client.gui.setScreen(screen);
        expected = program();
        browseSnapshot = ProgramBookCodec.encodeProgram(expected);
        assertions = new LinkedHashMap<>();
        check(Arrays.equals(browseSnapshot, ProgramBookCodec.encodeProgram(initial)), "opening changed the initial graph");
        check(history("undo") == 0 && history("redo") == 0, "fresh editor contains history");
        ensurePaletteVisible();
        switch (scene.mode()) {
            case COMMON -> {
                check(field("paletteLayer") == ProgramPaletteCatalog.Layer.COMMON, "common must be the default layer");
                check(cards().stream().allMatch(ProgramPaletteCatalog.Card::family), "common root must contain families");
                check(cards().stream().anyMatch(card -> card.id().equals("family.structure_move")), "missing move family");
                checkCardTranslations(cards());
                assertions.put("default_common_families", true);
            }
            case FAMILY -> {
                openFamily("family.structure_move");
                check(cards().stream().map(card -> card.members().getFirst().id().getPath()).toList().equals(List.of(
                        "program/accelerator/action/structure/move_group", "program/accelerator/action/structure/move")),
                        "family must expose the original group and advanced single-control nodes");
                clickPalette(paletteX() + paletteWidth() / 2.0, number("canvasY") + 31 + 6);
                check(field("paletteFamilyId") == null, "family back did not return to root");
                openFamily("family.structure_move");
                assertions.put("family_enter_back_enter", true);
                assertions.put("original_advanced_member_retained", true);
            }
            case SEARCH -> {
                setSearch(CommonProgramNodeIds.SHARED_VARIABLE_GET.toString());
                check(singletonMatches(CommonProgramNodeIds.SHARED_VARIABLE_GET), "common search did not find the advanced id");
                clickLayer(ProgramPaletteCatalog.Layer.ADVANCED);
                check(singletonMatches(CommonProgramNodeIds.SHARED_VARIABLE_GET), "advanced search did not find the original id");
                assertPaletteDefaults(cards().getFirst().members().getFirst());
                assertions.put("common_and_advanced_search", true);
                assertions.put("default_configuration_tooltip_matches_real_entry", true);
            }
            case COMPATIBILITY -> {
                clickLayer(ProgramPaletteCatalog.Layer.COMPATIBILITY);
                setSearch(CommonProgramNodeIds.FLOAT_CONSTANT.toString());
                check(singletonMatches(CommonProgramNodeIds.FLOAT_CONSTANT), "hidden compatibility id is unreachable");
                var staticize = Identifier.parse("academy:program/accelerator/action/structure/staticize");
                setSearch(staticize.toString());
                check(singletonMatches(staticize), "unreleased id is unreachable in compatibility");
                check(cards().getFirst().members().getFirst().presentationStatus()
                        == ProgramEditorNodeCatalog.PresentationStatus.UNRELEASED, "missing unreleased status");
                assertions.put("hidden_and_unreleased_reachable", true);
            }
            case CHOICES -> {
                openTemplatesThroughPalette();
                var tasks = starterTasks();
                check(tasks.size() == 2, "accelerator task choices missing");
                for (var kind : tasks) localized(kind.translationKey());
                localized("screen.academy.program.starter.manual");
                localized("screen.academy.program.starter.chat");
                assertions.put("task_choices_and_both_original_examples", true);
            }
            case TASK, BASIC -> installAndEditTemplate(scene);
            case COMPACT -> {
                int scale = client.getWindow().getGuiScale();
                client.getWindow().setWindowed(520 * scale, 340 * scale);
                assertions.put("compact_mode", "native_window_resize_with_normal_screen_reinitialization");
            }
        }
        if (isBrowseScene(scene)) assertBrowseUnchanged();
        assertCompileAndCodec(definition, session.capabilities, program());
    }

    private static void installAndEditTemplate(Scene scene) throws Exception {
        var empty = program();
        openTemplatesThroughPalette();
        var tasks = starterTasks();
        int row = scene.mode() == Mode.BASIC ? tasks.size() : tasks.indexOf(scene.task());
        check(row >= 0, "requested task is not offered");
        var bounds = call("starterBounds");
        double x = rect(bounds, "x") + rect(bounds, "width") / 2.0;
        double y = rect(bounds, "y") + 25 + row * 19 + 8;
        check((boolean) call("handleStarterClick", new Class<?>[]{double.class, double.class}, x, y), "starter click was not handled");
        var installed = program();
        check(!installed.graph().nodes().isEmpty(), "template did not expand into a graph");
        check(installed.id().equals(empty.id()) && installed.name().equals(empty.name())
                && installed.category().equals(empty.category()), "template changed slot identity");
        check(history("undo") == 1 && history("redo") == 0, "template must be one history operation");
        call("undo");
        check(program().equals(empty) && history("undo") == 0 && history("redo") == 1, "one undo must restore the empty slot");
        call("redo");
        check(program().equals(installed) && history("undo") == 1 && history("redo") == 0, "one redo must restore the template");
        assertCompileAndCodec(definition, session.capabilities, installed);
        check(!(boolean) call("openTaskTemplates"), "nonempty slot offered replacement templates");
        check(!(boolean) call("handleStarterClick", new Class<?>[]{double.class, double.class}, x, y), "stale starter click overwrote a graph");
        check(program().equals(installed) && history("undo") == 1, "nonempty refusal changed graph or history");

        var editable = installed.graph().nodes().stream().filter(node -> scene.mode() == Mode.BASIC
                ? node.type().equals(CommonProgramNodeIds.TEXT_CONSTANT)
                : scene.task() == ProgramTaskTemplates.Kind.TELEPORT_FORWARD
                ? node.type().equals(CommonProgramNodeIds.SCALAR_CONSTANT)
                : node.configuration().isJsonObject() && node.configuration().getAsJsonObject().has("duration_seconds"))
                .findFirst().orElseThrow();
        call("selectNode", new Class<?>[]{int.class}, editable.id());
        ensureInspectorVisible();
        call("syncConfigurationInputs");
        var key = scene.mode() == Mode.BASIC || scene.task() == ProgramTaskTemplates.Kind.TELEPORT_FORWARD
                ? "value" : "duration_seconds";
        var value = scene.mode() == Mode.BASIC ? "Editable learning path"
                : scene.task() == ProgramTaskTemplates.Kind.TELEPORT_FORWARD ? "4.5" : "2.0";
        call("configurationInputChanged", new Class<?>[]{String.class, String.class}, key, value);
        var edited = program();
        var changed = edited.graph().nodes().stream().filter(node -> node.id() == editable.id()).findFirst().orElseThrow();
        check(changed.configuration().getAsJsonObject().get(key).getAsString().equals(value), "template field was not editable");
        check(edited.graph().edges().equals(installed.graph().edges()), "editing a scalar value changed connections");
        assertCompileAndCodec(definition, session.capabilities, edited);
        call("undo");
        check(program().equals(installed), "configuration edit undo did not preserve the template");
        call("redo");
        check(program().equals(edited), "configuration edit redo failed");
        call("selectNode", new Class<?>[]{int.class}, editable.id());
        expected = edited;
        assertions.put("task", scene.mode() == Mode.BASIC ? "ORIGINAL_MANUAL_EXAMPLE" : scene.task().name());
        assertions.put("template_one_undo_and_redo", true);
        assertions.put("nonempty_ui_and_stale_click_rejected", true);
        assertions.put("template_configuration_editable_without_connection_loss", true);
        assertions.put("expanded_graph_compile_and_codec", true);
    }

    private static void openTemplatesThroughPalette() throws Exception {
        var before = program();
        int depth = history("undo");
        clickPalette(paletteX() + paletteWidth() - 20, number("canvasY") + 7);
        check((boolean) call("showStarter"), "task picker did not open through palette control");
        check(program().equals(before) && history("undo") == depth, "opening the task picker edited the graph");
    }

    private static void clickLayer(ProgramPaletteCatalog.Layer layer) throws Exception {
        int width = (paletteWidth() - 8) / ProgramPaletteCatalog.Layer.values().length;
        clickPalette(paletteX() + 4 + layer.ordinal() * width + width / 2.0, number("canvasY") + 16 + 6);
        check(field("paletteLayer") == layer && field("paletteFamilyId") == null, "layer click did not reset family");
    }

    private static void openFamily(String id) throws Exception {
        var available = cards();
        int row = -1;
        for (int i = 0; i < available.size(); i++) if (available.get(i).id().equals(id)) row = i;
        check(row >= 0 && available.get(row).family(), "family card missing: " + id);
        int visibleRows = Math.max(1, (number("canvasH") - 67) / 14);
        int scroll = Math.max(0, row - visibleRows + 1);
        setField("paletteScroll", scroll);
        clickPalette(paletteX() + paletteWidth() / 2.0, number("canvasY") + 64 + (row - scroll) * 14 + 6);
        check(id.equals(field("paletteFamilyId")) && call("selectedPaletteFamily") != null, "family did not open");
        check(cards().stream().noneMatch(ProgramPaletteCatalog.Card::family), "family did not drill into concrete nodes");
        checkCardTranslations(cards());
    }

    private static boolean singletonMatches(Identifier id) throws Exception {
        var entries = cards();
        return entries.size() == 1 && !entries.getFirst().family()
                && entries.getFirst().members().size() == 1 && entries.getFirst().members().getFirst().id().equals(id);
    }

    private static void ensurePaletteVisible() throws Exception {
        if (!(boolean) call("paletteVisible")) call("handleRailClick", new Class<?>[]{double.class, double.class},
                number("leftX") + 8.0, number("canvasY") + 8.0);
        check((boolean) call("paletteVisible"), "palette drawer did not open");
    }

    private static void ensureInspectorVisible() throws Exception {
        if (!(boolean) call("inspectorVisible")) call("handleRailClick", new Class<?>[]{double.class, double.class},
                number("rightX") + 8.0, number("canvasY") + 8.0);
        check((boolean) call("inspectorVisible"), "inspector drawer did not open");
    }

    private static void clickPalette(double x, double y) throws Exception {
        check((boolean) call("handlePaletteClick", new Class<?>[]{double.class, double.class}, x, y), "palette click not handled");
    }

    private static void setSearch(String value) throws Exception {
        var search = field("search");
        search.getClass().getMethod("setText", String.class).invoke(search, value);
        INVOCATIONS.merge("TextInputWidget.setText", 1, Integer::sum);
        setField("paletteScroll", 0);
    }

    private static void checkCardTranslations(List<ProgramPaletteCatalog.Card> values) {
        for (var card : values) {
            localized(card.titleTranslationKey());
            localized(card.descriptionTranslationKey());
        }
    }

    private static void checkLearningTranslations(Minecraft client) throws Exception {
        var keys = new TreeSet<String>();
        for (var layer : ProgramPaletteCatalog.Layer.values()) {
            keys.add(layer.translationKey());
            keys.add(layer.translationKey() + ".description");
        }
        for (var family : List.of("triggers", "actions", "targets", "collections", "values", "flow", "debug",
                "structure_extract", "structure_move", "structure_pose", "structure_launch", "structure_release")) {
            keys.add("screen.academy.program.palette.family." + family);
            keys.add("screen.academy.program.palette.family." + family + ".description");
        }
        for (var suffix : List.of("back", "common_hint", "default_value", "empty", "family_hint")) {
            keys.add("screen.academy.program.palette." + suffix);
        }
        for (var kind : ProgramTaskTemplates.Kind.values()) {
            keys.add(kind.translationKey());
            keys.add(kind.descriptionTranslationKey());
        }
        for (var suffix : List.of("open", "open_hint", "title", "hint")) {
            keys.add("screen.academy.program.task_template." + suffix);
        }
        check(keys.size() == 59, "unexpected learning-path translation key count");
        for (var code : List.of("zh_cn", "en_us")) {
            var resource = client.getResourceManager().getResource(
                    Identifier.fromNamespaceAndPath("academy", "lang/" + code + ".json")).orElseThrow();
            JsonObject values;
            try (var reader = resource.openAsReader()) { values = JsonParser.parseReader(reader).getAsJsonObject(); }
            for (var key : keys) {
                var text = values.get(key);
                check(text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()
                        && !text.getAsString().isBlank(), "missing " + code + " learning-path translation " + key);
            }
            TRANSLATIONS.put(code + "_keys_checked", keys.size());
        }
        keys.forEach(PrecisionLearningPathSmoke::localized);
        TRANSLATIONS.put("active_language_components_resolved", true);
    }

    private static void assertPaletteDefaults(ProgramEditorNodeCatalog.Entry entry) throws Exception {
        var tooltip = (List<?>) call("tooltipLines", new Class<?>[]{ProgramEditorNodeCatalog.Entry.class, boolean.class}, entry, true);
        var text = new ArrayList<String>();
        for (var line : tooltip) {
            var getter = line.getClass().getDeclaredMethod("getText");
            getter.setAccessible(true);
            text.add((String) getter.invoke(line));
        }
        for (var field : entry.defaultConfiguration().getAsJsonObject().entrySet()) {
            if (!field.getValue().isJsonPrimitive()) continue;
            var options = ProgramConfigurationOptions.options(entry, field.getKey(), field.getValue());
            var value = options.isEmpty() ? field.getValue().getAsString()
                    : ProgramConfigurationOptions.selected(options, field.getValue()).label().getString();
            var expectedLine = Component.translatable("screen.academy.program.palette.default_value",
                    Component.translatable("screen.academy.program.configuration.field." + field.getKey()), value).getString();
            check(text.contains(expectedLine), "palette tooltip omitted the actual default " + field.getKey());
        }
        check(text.stream().noneMatch(value -> value.contains("screen.academy.")), "unresolved default tooltip translation");
    }

    private static void localized(String key) {
        var text = Component.translatable(key).getString();
        check(!text.isBlank() && !text.equals(key), "unresolved translation: " + key);
    }

    private static boolean isBrowseScene(Scene scene) {
        return scene.mode() != Mode.TASK && scene.mode() != Mode.BASIC;
    }

    private static void assertBrowseUnchanged() throws Exception {
        check(Arrays.equals(browseSnapshot, ProgramBookCodec.encodeProgram(program())), "directory browsing changed the original graph");
        check(history("undo") == 0 && history("redo") == 0, "directory browsing changed graph history");
        assertions.put("original_graph_and_history_unchanged", true);
    }

    private static void assertCurrentProgram() throws Exception {
        check(clientScreenIsOurs(), "this editor is no longer visible");
        if (isBrowseScene(SCENES.get(index))) assertBrowseUnchanged();
        check(expected.equals(program()) && session.draft.equals(program()), "unexpected program mutation");
        assertCompileAndCodec(definition, session.capabilities, program());
        check(session.saveAttempts == 0, "save was attempted");
    }

    private static void assertCompileAndCodec(AbilityProgramDefinition value, Set<Identifier> capabilities, AbilityProgram program) {
        var compiled = value.compile(program, capabilities);
        // Empty task pickers are valid editor slots, although the runtime compiler rejects an empty program.
        check(program.graph().nodes().isEmpty() || compiled.valid(), "ordinary graph did not compile: " + compiled.diagnostics());
        var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(program));
        check(decoded.valid() && program.equals(decoded.program()), "ordinary graph codec changed the program");
    }

    private static Map<String, Object> sceneResult(Minecraft client) throws Exception {
        var scene = SCENES.get(index);
        var result = new LinkedHashMap<String, Object>();
        result.put("scene", scene.name());
        result.put("language", language);
        result.put("category", definition.category().toString());
        result.put("assertions", assertions);
        result.put("visible_ticks", ticks);
        result.put("palette_layer", field("paletteLayer").toString());
        result.put("palette_family", Objects.toString(field("paletteFamilyId"), ""));
        result.put("palette_card_ids", cards().stream().map(ProgramPaletteCatalog.Card::id).toList());
        result.put("nodes", program().graph().nodes().size());
        result.put("edges", program().graph().edges().size());
        result.put("program_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ProgramBookCodec.encodeProgram(program()))));
        result.put("undo_depth", history("undo"));
        result.put("redo_depth", history("redo"));
        result.put("screen_gui_size", List.of(screen.width, screen.height));
        result.put("window_size", List.of(client.getWindow().getWidth(), client.getWindow().getHeight()));
        result.put("compact_left", field("compactLeft"));
        result.put("compact_right", field("compactRight"));
        result.put("screenshot", "screenshots/" + screenshotName(scene));
        return result;
    }

    private static AbilityProgram empty(AbilityProgramDefinition value, String name) {
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), name,
                value.category(), ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY);
    }

    private static Set<Identifier> capabilities(AbilityProgramDefinition value) {
        var result = new HashSet<Identifier>();
        value.editorCatalog().entries().forEach(entry -> result.addAll(entry.type().scope().requiredCapabilities()));
        ProgramTaskTemplates.forCategory(value).forEach(kind -> result.addAll(ProgramTaskTemplates.requiredCapabilities(value, kind)));
        return Set.copyOf(result);
    }

    private static Path isolatedDirectory(Minecraft client) {
        var result = client.gameDirectory.toPath().toAbsolutePath().normalize();
        check(result.getParent() != null && result.getParent().getFileName().toString().equals("run")
                && result.getFileName().toString().startsWith("native-sublevel-direct-smoke"), "isolated directory required");
        return result;
    }

    private static boolean clientScreenIsOurs() { return Minecraft.getInstance().gui.screen() == screen; }
    private static String screenshotName(Scene scene) { return "precision-learning-" + language + "-" + scene.name() + ".png"; }
    private static int paletteX() throws Exception { return (int) call("paletteX"); }
    private static int paletteWidth() throws Exception { return (int) call("paletteWidth"); }
    private static int number(String name) throws Exception { return ((Number) field(name)).intValue(); }
    private static int history(String name) throws Exception { return ((Deque<?>) field(name)).size(); }
    private static AbilityProgram program() throws Exception { return ((ProgramEditorDocument) field("document")).program(); }

    @SuppressWarnings("unchecked")
    private static List<ProgramPaletteCatalog.Card> cards() throws Exception { return (List<ProgramPaletteCatalog.Card>) call("paletteCards"); }
    @SuppressWarnings("unchecked")
    private static List<ProgramTaskTemplates.Kind> starterTasks() throws Exception { return (List<ProgramTaskTemplates.Kind>) call("starterTasks"); }

    private static Object field(String name) throws Exception {
        var field = ModularProgramScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void setField(String name, Object value) throws Exception {
        var field = ModularProgramScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private static int rect(Object bounds, String name) throws Exception {
        var method = bounds.getClass().getDeclaredMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        method.setAccessible(true);
        return (int) method.invoke(bounds);
    }

    private static Object call(String name) throws Exception { return call(name, new Class<?>[0]); }

    private static Object call(String name, Class<?>[] types, Object... values) throws Exception {
        Method method = ModularProgramScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        INVOCATIONS.merge(name, 1, Integer::sum);
        try { return method.invoke(screen, values); }
        catch (InvocationTargetException wrapper) {
            var cause = wrapper.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw wrapper;
        }
    }

    private static void restoreWindow(Minecraft client) {
        if (originalWindowWidth > 0 && originalWindowHeight > 0) {
            client.getWindow().setWindowed(originalWindowWidth, originalWindowHeight);
        }
    }

    private static void write(Minecraft client, boolean passed, Throwable failure) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", passed);
        report.put("language", language);
        report.put("interaction_mode", "production screen click handlers and widget callbacks invoked by reflection; not manual input");
        report.put("synthetic_capabilities", "all required capabilities supplied only to the in-memory editor session");
        report.put("cases", RESULTS);
        report.put("model_only_recipe_checks", MODEL_RESULTS);
        report.put("learning_path_translation_checks", TRANSLATIONS);
        report.put("screen_method_invocations", INVOCATIONS);
        report.put("save_attempts", SESSIONS.stream().mapToInt(value -> value.saveAttempts).sum());
        report.put("save_packets_or_world_mutation_commands", false);
        report.put("runtime_skill_execution", false);
        report.put("visual_review_required", true);
        report.put("limitations", List.of("No learned-player admission or world-execution acceptance",
                "No performance percentage or old ten-node long-stay rendering acceptance",
                "Screenshots and assertions do not certify absence of glyph loss or startup resource warnings"));
        report.put("failure", failure == null ? "" : failure.toString());
        Files.writeString(isolatedDirectory(client).resolve("precision-learning-path-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        AcademyCraft.LOGGER.info("PRECISION_LEARNING_PATH_RESULT passed={} language={} cases={} model_recipes={}",
                passed, language, RESULTS.size(), MODEL_RESULTS.size());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Mode { COMMON, FAMILY, SEARCH, COMPATIBILITY, CHOICES, TASK, BASIC, COMPACT }
    private record Scene(String name, String category, Mode mode, ProgramTaskTemplates.Kind task) {}

    private static final class MemorySession implements ModularProgramEditorSession {
        private AbilityProgram draft;
        private final Set<Identifier> capabilities;
        private int saveAttempts;

        private MemorySession(AbilityProgram program, Set<Identifier> capabilities) {
            draft = program;
            this.capabilities = capabilities;
        }

        @Override public Component title() { return Component.literal("Precision B / " + SCENES.get(index).name()); }
        @Override public int slotCount() { return 1; }
        @Override public int selectedSlot() { return 0; }
        @Override public long revision() { return 0; }
        @Override public AbilityProgram editableProgram(int slot) { return draft; }
        @Override public AbilityProgram emptyProgram(int slot) { return draft; }
        @Override public AbilityProgram restoredProgram(int slot) { return null; }
        @Override public Set<Identifier> capabilities() { return capabilities; }
        @Override public void updateLocalProgram(int slot, AbilityProgram value) { draft = value; }
        @Override public void selectSlot(int slot) { check(slot == 0, "unexpected slot selection"); }
        @Override public void saveProgram(int slot, AbilityProgram value, long revision) {
            saveAttempts++;
            throw new IllegalStateException("save forbidden in learning-path fixture");
        }
        @Override public void closed(ModularProgramScreen value) {}
        @Override public boolean showStarter() { return false; }
        @Override public void dismissStarterForWorld() { throw new IllegalStateException("preference packets forbidden in fixture"); }
    }
}
