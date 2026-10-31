package org.academy.internal.common.ability.program.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.client.ability.program.ProgramConfigurationOptions;
import org.academy.internal.common.ability.program.CommonProgramExecutors;
import org.academy.internal.common.ability.program.ProgramSamplingNodes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Freezes the new contract without replacing the original 342-node inventory. */
class ProgramSamplingNodeContractTest {
    private static final Path CONTRACT = Path.of("validation/precision_nodes/node_additions_d_2026_10_30.json");

    @Test
    void explicitAdditionIsSeparateFromTheUnchangedLegacyInventory() throws Exception {
        var additions = read(CONTRACT);
        var baseline = Path.of(additions.get("legacy_inventory").getAsString());
        assertEquals(additions.get("legacy_inventory_sha256").getAsString(),
                HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(baseline))));
        var legacy = read(baseline);
        assertEquals(342, additions.get("legacy_unique_registered_ids").getAsInt());
        assertEquals(342, legacy.get("unique_registered_ids").getAsInt());
        assertEquals(1, additions.getAsJsonArray("nodes").size());
        var expected = nodeContract();
        assertEquals("academy:program/core/state/sample_value", expected.get("id").getAsString());
        assertEquals(expected.get("id").getAsString(), CommonProgramNodeIds.SAMPLE_VALUE.toString());
        for (var row : legacy.getAsJsonArray("nodes")) {
            assertNotEquals(CommonProgramNodeIds.SAMPLE_VALUE.toString(), row.getAsJsonObject().get("id").getAsString());
        }
        var actual = AbilityProgramDefinitions.all().stream()
                .flatMap(definition -> definition.editorCatalog().entries().stream())
                .map(entry -> entry.id().toString()).collect(Collectors.toSet());
        var allExpected = new HashSet<String>();
        legacy.getAsJsonArray("nodes").forEach(row -> allExpected.add(row.getAsJsonObject().get("id").getAsString()));
        allExpected.add(CommonProgramNodeIds.SAMPLE_VALUE.toString());
        assertEquals(allExpected, actual, "Every new ID requires its own explicit contract");
        assertEquals(343, actual.size());
    }

    @Test
    void samplingIsVisibleAndBoundToTheSameCommonTypeAndExecutorInAllSevenCategories() throws IOException {
        var expected = nodeContract();
        var categories = new HashSet<String>();
        for (var definition : AbilityProgramDefinitions.all()) {
            categories.add(definition.category().toString());
            var entry = definition.editorCatalog().entry(CommonProgramNodeIds.SAMPLE_VALUE);
            assertNotNull(entry);
            assertEquals(expected.get("visible").getAsBoolean(), entry.visible());
            assertSame(ProgramSamplingNodes.type(), entry.type());
            assertSame(entry.type(), definition.nodeLookup().find(entry.id()));
            assertSame(CommonProgramExecutors.INSTANCE.find(entry.id()), definition.executors().find(entry.id()));
            assertNotNull(definition.executors().find(entry.id()));
            assertEquals(expected.get("default_configuration"), entry.defaultConfiguration());
            assertEquals(expected.get("role").getAsString(), entry.type().role().name());
            assertEquals(expected.get("purity").getAsString(), entry.type().purity().name());
            assertEquals(expected.get("schema_version").getAsInt(), entry.type().schemaVersion());
            assertEquals(strings(expected.getAsJsonArray("allowed_categories")), entry.type().scope().allowedCategories()
                    .stream().map(Object::toString).collect(Collectors.toSet()));
            assertEquals(strings(expected.getAsJsonArray("required_capabilities")), entry.type().scope().requiredCapabilities()
                    .stream().map(Object::toString).collect(Collectors.toSet()));
        }
        assertEquals(strings(expected.getAsJsonArray("categories")), categories);
    }

    @Test
    void allTwentyTwoModesKeepFixedStrongPortsAndHaveNoRuntimeTypeConfigurationPort() throws IOException {
        var expected = nodeContract();
        var choices = expected.getAsJsonArray("type_options").asList().stream().map(value -> value.getAsString()).toList();
        assertEquals(22, choices.size());
        assertEquals(choices, ProgramValueSnapshots.supportedTypes().stream().map(type -> type.id().toString()).toList());
        for (var definition : AbilityProgramDefinitions.all()) {
            var catalog = definition.editorCatalog();
            var entry = catalog.entry(CommonProgramNodeIds.SAMPLE_VALUE);
            var options = ProgramConfigurationOptions.options(entry, "type", entry.defaultConfiguration().getAsJsonObject().get("type"));
            assertEquals(choices, options.stream().map(option -> option.value().getAsString()).toList());
            for (var typeId : choices) {
                var configuration = new JsonObject();
                configuration.addProperty("type", typeId);
                var decoded = ProgramSamplingNodes.Configuration.CODEC.parse(JsonOps.INSTANCE, configuration).getOrThrow();
                assertEquals(configuration, ProgramSamplingNodes.Configuration.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
                var schema = catalog.schema(entry.id(), configuration);
                assertNotNull(schema);
                assertPorts(expected.getAsJsonArray("inputs"), schema.inputs(), typeId);
                assertPorts(expected.getAsJsonArray("outputs"), schema.outputs(), typeId);
                assertEquals(List.of(), ProgramSamplingNodes.type().configurationPorts(decoded));
                assertEquals(schema, ProgramSamplingNodes.type().resolvedSchema(decoded));
                var connected = ProgramConfigurationOptions.defaultsForConnection(catalog, entry,
                        new ProgramValueType(Identifier.parse(typeId)), false);
                assertEquals(configuration, connected, "Dragging a typed source chooses the exact sample mode");
            }
        }
    }

    @Test
    void unsupportedTypesAndMalformedConfigurationAreRejectedWithoutAnImplicitFallback() {
        for (var typeId : List.of("academy:program_type/flow", "academy:program_type/duration",
                "academy:program_type/structure_control", "academy:program_type/action_result",
                "academy:program_type/structure_operation_result", "academy:program_type/control_destination",
                "example:unknown", "NOT AN IDENTIFIER")) {
            var configuration = new JsonObject();
            configuration.addProperty("type", typeId);
            var decoded = assertDoesNotThrow(() -> ProgramSamplingNodes.Configuration.CODEC.parse(JsonOps.INSTANCE, configuration));
            assertTrue(decoded.error().isPresent(), typeId);
        }
        assertTrue(ProgramSamplingNodes.Configuration.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).error().isPresent());
        assertTrue(ProgramSamplingNodes.Configuration.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("entity_set")).error().isPresent());
    }

    private static void assertPorts(JsonArray expected, List<ProgramPortDefinition> actual, String type) {
        assertEquals(expected.size(), actual.size());
        for (var index = 0; index < expected.size(); index++) {
            var row = expected.get(index).getAsJsonObject();
            var port = actual.get(index);
            assertEquals(row.get("name").getAsString(), port.name());
            var expectedType = row.get("type").getAsString();
            assertEquals(expectedType.equals("$configured_type") ? type : expectedType, port.type().id().toString());
            assertEquals(row.get("required").getAsBoolean(), port.required());
            assertEquals(row.get("max_connections").getAsInt(), port.maxConnections());
        }
    }

    private static Set<String> strings(JsonArray values) {
        return values.asList().stream().map(value -> value.getAsString()).collect(Collectors.toSet());
    }

    private static JsonObject nodeContract() throws IOException {
        return read(CONTRACT).getAsJsonArray("nodes").get(0).getAsJsonObject();
    }

    private static JsonObject read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
