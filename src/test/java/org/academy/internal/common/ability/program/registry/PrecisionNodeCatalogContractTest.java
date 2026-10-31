package org.academy.internal.common.ability.program.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.academy.api.common.ability.program.ProgramConfigurationPort;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.ability.program.ProgramPortDefinition;
import org.academy.internal.client.ability.program.ProgramConfigurationOptions;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Freezes the gameplay/editor contracts recorded by the 2026-10-30 in-game catalog audit.
 * Translation text is deliberately read from current resources, so copy edits remain possible.
 * Mode coverage is the recorded finite single-field variants with all other fields at defaults;
 * it does not claim a Cartesian configuration matrix or world/executor behavior acceptance.
 */
class PrecisionNodeCatalogContractTest {
    private static final Path BASELINE = Path.of(
            "validation/precision_nodes/node_inventory_2026_10_30.json");
    private static final Set<String> CATEGORIES = Set.of(
            "academy:accelerator", "academy:aeromanip", "academy:darkmatter",
            "academy:electromaster", "academy:meltdowner", "academy:mentalout", "academy:teleport");
    // New runtime contracts are checked separately; this test continues to freeze all original 342 IDs.
    // Unknown additions still fail the exact-set checks below.
    private static final Set<net.minecraft.resources.Identifier> DECLARED_ADDITIONS = Set.of(
            CommonProgramNodeIds.SAMPLE_VALUE);
    private static JsonObject baseline;
    private static Map<String, JsonObject> expectedNodes;
    private static Map<String, JsonObject> expectedCategories;

    @BeforeAll
    static void readCommittedInventoryWithoutCopyingTheFixture() throws IOException {
        baseline = readJson(BASELINE);
        expectedNodes = index(baseline.getAsJsonArray("nodes"));
        expectedCategories = index(baseline.getAsJsonArray("categories"));
        assertTrue(baseline.get("passed").getAsBoolean(), "The frozen inventory must be successful");
        assertEquals(342, baseline.get("unique_registered_ids").getAsInt());
        assertEquals(342, expectedNodes.size());
        assertEquals(7, baseline.get("category_count").getAsInt());
        assertEquals(CATEGORIES, expectedCategories.keySet());
    }

    @Test
    void registeredIdsCategoryMembershipAndHiddenAliasesStayCompatible() {
        var definitions = definitions();
        assertEquals(CATEGORIES, definitions.stream()
                .map(definition -> definition.category().toString()).collect(Collectors.toSet()));
        var memberships = new TreeMap<String, Set<String>>();
        var uniqueEntries = new TreeMap<String, ProgramEditorNodeCatalog.Entry>();
        int occurrences = 0;
        for (var definition : definitions) {
            var category = definition.category().toString();
            var expectedCategory = expectedCategories.get(category);
            var entries = legacyEntries(definition);
            var ids = new HashSet<String>();
            for (var entry : entries) {
                var id = entry.id().toString();
                assertTrue(ids.add(id), category + " duplicate catalog id " + id);
                assertNotNull(expectedNodes.get(id), category + " unexpected catalog id " + id);
                assertEquals(expectedFor(category, id).get("visible").getAsBoolean(),
                        entry.visible(), category + " visibility of " + id);
                memberships.computeIfAbsent(id, _ -> new TreeSet<>()).add(category);
                uniqueEntries.putIfAbsent(id, entry);
            }
            assertEquals(strings(expectedCategory.getAsJsonArray("node_ids")), ids,
                    category + " registered ids");
            assertEquals(expectedCategory.get("registered_occurrences").getAsInt(), entries.size(), category);
            assertEquals(expectedCategory.get("visible_occurrences").getAsLong(),
                    entries.stream().filter(ProgramEditorNodeCatalog.Entry::visible).count(), category);
            assertEquals(expectedCategory.get("hidden_occurrences").getAsLong(),
                    entries.stream().filter(entry -> !entry.visible()).count(), category);
            occurrences += entries.size();
        }
        assertEquals(expectedNodes.keySet(), memberships.keySet(), "Complete registered id set");
        expectedNodes.forEach((id, expected) -> assertEquals(
                strings(expected.getAsJsonArray("categories")), memberships.get(id), id + " category membership"));
        assertEquals(1476, occurrences);
        assertEquals(baseline.get("registered_occurrences").getAsInt(), occurrences);
        assertEquals(277, uniqueEntries.values().stream().filter(ProgramEditorNodeCatalog.Entry::visible).count());
        assertEquals(65, uniqueEntries.values().stream().filter(entry -> !entry.visible()).count());
        var expectedCommon = expectedNodes.entrySet().stream()
                .filter(entry -> entry.getValue().get("origin").getAsString().equals("common"))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        assertEquals(189, expectedCommon.size());
        assertEquals(expectedCommon, CommonProgramNodeCatalog.INSTANCE.types().keySet().stream()
                .filter(id -> !DECLARED_ADDITIONS.contains(id))
                .map(Object::toString).collect(Collectors.toSet()));
    }

    @Test
    void defaultsCodecsScopesAndRuntimeBindingsStayCompatible() {
        for (var definition : definitions()) {
            var category = definition.category().toString();
            for (var entry : legacyEntries(definition)) {
                var expected = expectedFor(category, entry.id().toString());
                var context = category + " / " + entry.id();
                var type = entry.type();
                assertEquals(expected.get("schema_version").getAsInt(), type.schemaVersion(), context);
                assertEquals(expected.get("role").getAsString(), type.role().name(), context);
                assertEquals(expected.get("purity").getAsString(), type.purity().name(), context);
                assertEquals(strings(expected.getAsJsonArray("required_capabilities")),
                        type.scope().requiredCapabilities().stream().map(Object::toString).collect(Collectors.toSet()),
                        context + " required capabilities");
                assertEquals(strings(expected.getAsJsonArray("allowed_categories")),
                        type.scope().allowedCategories().stream().map(Object::toString).collect(Collectors.toSet()),
                        context + " allowed categories");
                assertEquals(expected.get("default_configuration"), entry.defaultConfiguration(), context + " defaults");
                assertSame(type, definition.nodeLookup().find(entry.id()), context + " editor/runtime type binding");
                assertEquals(expected.get("executor_present").getAsBoolean(),
                        definition.executors().find(entry.id()) != null, context + " executor registration");
                assertSchema(expected, "default_inputs", "default_outputs", entry.defaultSchema(), context);
                assertDefaultCodec(type, entry, expected, context);
            }
        }
    }

    @Test
    void finiteSingleFieldOptionsPreserveValidityAndResolvedPorts() {
        var uniqueIds = new HashSet<String>();
        int uniqueOptionCases = 0;
        for (var definition : definitions()) {
            var category = definition.category().toString();
            var catalog = definition.editorCatalog();
            for (var entry : legacyEntries(definition)) {
                var firstOccurrence = uniqueIds.add(entry.id().toString());
                var expected = expectedFor(category, entry.id().toString());
                var fields = expected.getAsJsonObject("configuration_fields");
                var defaults = entry.defaultConfiguration().getAsJsonObject();
                var context = category + " / " + entry.id();
                assertEquals(fields.keySet(), defaults.keySet(), context + " configuration fields");
                for (var field : defaults.entrySet()) {
                    var choices = rows(fields.getAsJsonObject(field.getKey()).getAsJsonArray("finite_options"));
                    var options = ProgramConfigurationOptions.options(entry, field.getKey(), field.getValue());
                    assertEquals(choices.stream().map(choice -> choice.get("value")).toList(),
                            options.stream().map(ProgramConfigurationOptions.Option::value).toList(),
                            context + " finite options for " + field.getKey());
                    for (var choice : choices) {
                        var candidate = defaults.deepCopy();
                        candidate.add(field.getKey(), choice.get("value").deepCopy());
                        var mode = context + " / " + field.getKey() + "=" + choice.get("value");
                        var schema = catalog.schema(entry.id(), candidate);
                        assertEquals(choice.get("valid_with_other_defaults").getAsBoolean(), schema != null,
                                mode + " validity with other defaults");
                        if (schema != null) assertSchema(choice, "inputs", "outputs", schema, mode);
                        if (firstOccurrence) uniqueOptionCases++;
                    }
                }
            }
        }
        assertEquals(342, uniqueIds.size());
        assertEquals(353, uniqueOptionCases, "Includes the invalid scalar boolean option with other defaults unchanged");
    }

    @Test
    void currentBilingualNamesDescriptionsPortsAndConfigurationFieldsAreComplete() throws IOException {
        var languages = Map.of(
                "zh_cn", readJson(Path.of("src/main/resources/assets/academy/lang/zh_cn.json")),
                "en_us", readJson(Path.of("src/main/resources/assets/academy/lang/en_us.json")));
        for (var definition : definitions()) {
            var catalog = definition.editorCatalog();
            for (var entry : catalog.entries()) {
                var context = definition.category() + " / " + entry.id();
                assertLocalized(languages, entry.translationKey(), context + " name");
                assertLocalized(languages, entry.descriptionTranslationKey(), context + " description");
                assertPortLabels(languages, entry, entry.defaultSchema(), context);
                var defaults = entry.defaultConfiguration().getAsJsonObject();
                for (var field : defaults.entrySet()) {
                    assertLocalized(languages, "screen.academy.program.configuration.field." + field.getKey(), context);
                    for (var option : ProgramConfigurationOptions.options(entry, field.getKey(), field.getValue())) {
                        var candidate = defaults.deepCopy();
                        candidate.add(field.getKey(), option.value());
                        var schema = catalog.schema(entry.id(), candidate);
                        if (schema != null) assertPortLabels(languages, entry, schema,
                                context + " / " + field.getKey() + "=" + option.value());
                    }
                }
            }
        }
    }

    private static <C> void assertDefaultCodec(
            ProgramNodeType<C> type, ProgramEditorNodeCatalog.Entry entry, JsonObject expected, String context
    ) {
        var codec = type.configurationCodec();
        var decoded = assertDoesNotThrow(() -> codec.parse(
                JsonOps.INSTANCE, expected.get("default_configuration").deepCopy()).getOrThrow(), context + " codec decode");
        var encoded = assertDoesNotThrow(() -> codec.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow(),
                context + " codec encode");
        var restored = assertDoesNotThrow(() -> codec.parse(JsonOps.INSTANCE, encoded).getOrThrow(),
                context + " codec round trip");
        assertEquals(encoded, codec.encodeStart(JsonOps.INSTANCE, restored).getOrThrow(), context + " canonical codec form");
        assertSchema(expected, "default_inputs", "default_outputs", type.resolvedSchema(decoded), context + " decoded schema");
        assertEquals(entry.defaultSchema(), type.resolvedSchema(restored), context + " round-trip schema");
        assertEquals(configurationPorts(expected.getAsJsonArray("configuration_ports")),
                type.configurationPorts(decoded).stream().map(ConfigurationPortContract::of).toList(),
                context + " configuration ports");
    }

    private static void assertSchema(
            JsonObject expected, String inputs, String outputs, ProgramNodeSchema actual, String context
    ) {
        assertEquals(ports(expected.getAsJsonArray(inputs)),
                actual.inputs().stream().map(PortContract::of).toList(), context + " input schema");
        assertEquals(ports(expected.getAsJsonArray(outputs)),
                actual.outputs().stream().map(PortContract::of).toList(), context + " output schema");
    }

    private static void assertPortLabels(
            Map<String, JsonObject> languages, ProgramEditorNodeCatalog.Entry entry, ProgramNodeSchema schema, String context
    ) {
        var ports = new ArrayList<>(schema.inputs());
        ports.addAll(schema.outputs());
        for (var port : ports) {
            var key = port.name().matches("value_[0-9]+")
                    ? "screen.academy.program.port.collection_builder_value" : entry.portTranslationKey(port.name());
            assertLocalized(languages, key, context + " port " + port.name());
        }
    }

    private static void assertLocalized(Map<String, JsonObject> languages, String key, String context) {
        assertFalse(key.isBlank(), context + " translation key");
        languages.forEach((language, values) -> {
            var value = values.get(key);
            assertNotNull(value, context + " missing " + language + " translation " + key);
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && !value.getAsString().isBlank(), context + " empty/non-text " + language + " translation " + key);
        });
    }

    private static List<AbilityProgramDefinition> definitions() {
        return AbilityProgramDefinitions.all().stream()
                .sorted(Comparator.comparing(definition -> definition.category().toString())).toList();
    }

    private static List<ProgramEditorNodeCatalog.Entry> legacyEntries(AbilityProgramDefinition definition) {
        return definition.editorCatalog().entries().stream()
                .filter(entry -> !DECLARED_ADDITIONS.contains(entry.id())).toList();
    }

    private static JsonObject expectedFor(String category, String id) {
        var expected = expectedNodes.get(id);
        assertNotNull(expected, category + " unexpected node " + id);
        if (expected.has("category_variants")) {
            for (var variant : rows(expected.getAsJsonArray("category_variants"))) {
                if (variant.get("variant_category").getAsString().equals(category)) return variant;
            }
        }
        return expected;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, JsonObject> index(JsonArray values) {
        var result = new HashMap<String, JsonObject>();
        for (var row : rows(values)) {
            var id = row.get("id").getAsString();
            assertNull(result.putIfAbsent(id, row), "Duplicate frozen inventory id " + id);
        }
        return Map.copyOf(result);
    }

    private static List<JsonObject> rows(JsonArray values) {
        var result = new ArrayList<JsonObject>();
        for (var value : values) result.add(value.getAsJsonObject());
        return result;
    }

    private static Set<String> strings(JsonArray values) {
        var result = new TreeSet<String>();
        for (var value : values) result.add(value.getAsString());
        return result;
    }

    private static List<PortContract> ports(JsonArray values) {
        return rows(values).stream().map(value -> new PortContract(
                value.get("name").getAsString(), value.get("type").getAsString(),
                value.get("required").getAsBoolean(), value.get("max_connections").getAsInt())).toList();
    }

    private static List<ConfigurationPortContract> configurationPorts(JsonArray values) {
        return rows(values).stream().map(value -> new ConfigurationPortContract(
                value.get("field").getAsString(), value.get("type").getAsString())).toList();
    }

    private record PortContract(String name, String type, boolean required, int maxConnections) {
        static PortContract of(ProgramPortDefinition port) {
            return new PortContract(port.name(), port.type().id().toString(), port.required(), port.maxConnections());
        }
    }

    private record ConfigurationPortContract(String field, String type) {
        static ConfigurationPortContract of(ProgramConfigurationPort port) {
            return new ConfigurationPortContract(port.field(), port.type().id().toString());
        }
    }
}
