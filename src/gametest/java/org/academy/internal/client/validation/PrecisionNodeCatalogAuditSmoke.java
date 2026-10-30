package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.program.ProgramNodePalette;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.ability.program.ProgramPortDefinition;
import org.academy.internal.client.ability.program.ProgramConfigurationOptions;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Explicit read-only inventory of the actual editor catalog after registry and resource loading. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionNodeCatalogAuditSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionNodeCatalogAudit");
    private static boolean finished;

    private PrecisionNodeCatalogAuditSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!ENABLED || finished || client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        finished = true;
        var output = client.gameDirectory.toPath().resolve("precision-node-catalog-audit.json");
        try {
            var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
            if (directory.getParent() == null || !directory.getParent().getFileName().toString().equals("run")
                    || !directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
                throw new IllegalStateException("Catalog audit requires an isolated native-sublevel-direct-smoke directory");
            }
            var zh = language(client, "zh_cn");
            var en = language(client, "en_us");
            var unique = new TreeMap<String, JsonObject>();
            var categories = new JsonArray();
            int occurrences = 0;
            for (var definition : AbilityProgramDefinitions.all().stream()
                    .sorted(Comparator.comparing(value -> value.category().toString())).toList()) {
                var category = new JsonObject();
                var entries = definition.editorCatalog().entries();
                category.addProperty("id", definition.category().toString());
                category.addProperty("registered_occurrences", entries.size());
                category.addProperty("visible_occurrences", entries.stream().filter(ProgramEditorNodeCatalog.Entry::visible).count());
                category.addProperty("hidden_occurrences", entries.stream().filter(entry -> !entry.visible()).count());
                category.addProperty("category_runtime_types", definition.categoryNodeTypes().size());
                category.addProperty("extension_fingerprint", definition.extensionFingerprint());
                var ids = new JsonArray();
                var roleCounts = new TreeMap<String, Integer>();
                var groupCounts = new TreeMap<String, Integer>();
                for (var entry : entries) {
                    occurrences++;
                    String id = entry.id().toString();
                    ids.add(id);
                    roleCounts.merge(entry.type().role().name(), 1, Integer::sum);
                    groupCounts.merge(entry.group().name(), 1, Integer::sum);
                    var row = entry(entry, zh, en);
                    row.addProperty("origin", CommonProgramNodeCatalog.INSTANCE.find(entry.id()) != null ? "common"
                            : definition.categoryNodeTypes().containsKey(entry.id()) ? "category" : "extension");
                    row.addProperty("executor_present", definition.executors().find(entry.id()) != null);
                    row.addProperty("editor_type_matches_runtime", definition.nodeLookup().find(entry.id()) == entry.type());
                    if (unique.containsKey(id)) {
                        var existing = unique.get(id);
                        existing.getAsJsonArray("categories").add(definition.category().toString());
                        if (!existing.get("default_configuration").equals(row.get("default_configuration"))
                                || !existing.get("default_inputs").equals(row.get("default_inputs"))
                                || !existing.get("default_outputs").equals(row.get("default_outputs"))
                                || !existing.get("visible").equals(row.get("visible"))) {
                            if (!existing.has("category_variants")) existing.add("category_variants", new JsonArray());
                            row.addProperty("variant_category", definition.category().toString());
                            existing.getAsJsonArray("category_variants").add(row);
                        }
                    } else {
                        var membership = new JsonArray(); membership.add(definition.category().toString());
                        row.add("categories", membership);
                        unique.put(id, row);
                    }
                }
                category.add("node_ids", ids);
                category.add("roles", counts(roleCounts));
                category.add("groups", counts(groupCounts));
                categories.add(category);
            }
            var nodes = new JsonArray(); unique.values().forEach(nodes::add);
            var report = new JsonObject();
            report.addProperty("passed", true);
            report.addProperty("source", "AbilityProgramDefinitions.all/editorCatalog.entries after client resource load");
            report.addProperty("scope", "all registered built-in and extension node ids; visibility is catalog visibility, not learned-skill filtering");
            report.addProperty("schema_scope", "default configuration plus finite one-field option variants; not a Cartesian enumeration or runtime-world acceptance proof");
            report.addProperty("category_count", categories.size());
            report.addProperty("registered_occurrences", occurrences);
            report.addProperty("unique_registered_ids", unique.size());
            report.addProperty("unique_visible_ids", unique.values().stream().filter(row -> row.get("visible").getAsBoolean()).count());
            report.addProperty("unique_hidden_ids", unique.values().stream().filter(row -> !row.get("visible").getAsBoolean()).count());
            report.addProperty("common_registered_ids", unique.values().stream().filter(row -> row.get("origin").getAsString().equals("common")).count());
            report.addProperty("zh_cn_resource_keys", zh.size());
            report.addProperty("en_us_resource_keys", en.size());
            report.add("categories", categories);
            report.add("nodes", nodes);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report) + "\n");
            AcademyCraft.LOGGER.info("PRECISION_NODE_CATALOG_AUDIT_PASSED categories={} unique={} occurrences={} output={}",
                    categories.size(), unique.size(), occurrences, output);
        } catch (Throwable failure) {
            AcademyCraft.LOGGER.error("PRECISION_NODE_CATALOG_AUDIT_FAILED", failure);
            try {
                var report = new JsonObject(); report.addProperty("passed", false); report.addProperty("failure", failure.toString());
                Files.writeString(output, report.toString());
            } catch (Exception ignored) { }
        }
    }

    private static JsonObject language(Minecraft client, String language) throws Exception {
        var resource = client.getResourceManager().getResource(Identifier.fromNamespaceAndPath("academy", "lang/" + language + ".json")).orElseThrow();
        try (var reader = resource.openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }

    private static JsonObject entry(ProgramEditorNodeCatalog.Entry entry, JsonObject zh, JsonObject en) {
        var row = new JsonObject();
        row.addProperty("id", entry.id().toString());
        row.addProperty("role", entry.type().role().name());
        row.addProperty("purity", entry.type().purity().name());
        row.addProperty("group", entry.group().name());
        row.addProperty("visible", entry.visible());
        row.addProperty("presentation_status", entry.presentationStatus().name());
        row.addProperty("schema_version", entry.type().schemaVersion());
        row.addProperty("type_class", entry.type().getClass().getName());
        row.addProperty("fallback_display_name", entry.displayName());
        row.addProperty("translation_key", entry.translationKey());
        row.addProperty("description_translation_key", entry.descriptionTranslationKey());
        row.add("name", localized(entry.translationKey(), zh, en));
        row.add("description", localized(entry.descriptionTranslationKey(), zh, en));
        row.add("required_capabilities", strings(entry.type().scope().requiredCapabilities().stream().map(Identifier::toString).sorted().toList()));
        row.add("allowed_categories", strings(entry.type().scope().allowedCategories().stream().map(Identifier::toString).sorted().toList()));
        row.add("default_configuration", entry.defaultConfiguration());
        row.add("default_inputs", ports(entry, entry.defaultSchema().inputs(), zh, en));
        row.add("default_outputs", ports(entry, entry.defaultSchema().outputs(), zh, en));
        row.add("configuration_ports", configurationPorts(entry.type(), entry.defaultConfiguration()));
        row.addProperty("palette_provider", ProgramNodePalette.hasProvider(entry.id()));
        row.addProperty("observed_player_preset_count", ProgramNodePalette.presets(entry.id()).size());
        var options = new JsonObject();
        if (entry.defaultConfiguration().isJsonObject()) {
            for (var field : entry.defaultConfiguration().getAsJsonObject().entrySet()) {
                var fieldRecord = new JsonObject();
                fieldRecord.add("label", localized("screen.academy.program.configuration.field." + field.getKey(), zh, en));
                var choices = new JsonArray();
                for (var option : ProgramConfigurationOptions.options(entry, field.getKey(), field.getValue())) {
                    var choice = new JsonObject();
                    choice.add("value", option.value());
                    var candidate = entry.defaultConfiguration().getAsJsonObject().deepCopy();
                    candidate.add(field.getKey(), option.value());
                    var schema = resolved(entry.type(), candidate);
                    choice.addProperty("valid_with_other_defaults", schema != null);
                    if (schema != null) {
                        choice.add("inputs", ports(entry, schema.inputs(), zh, en));
                        choice.add("outputs", ports(entry, schema.outputs(), zh, en));
                    }
                    choices.add(choice);
                }
                fieldRecord.add("finite_options", choices);
                options.add(field.getKey(), fieldRecord);
            }
        }
        row.add("configuration_fields", options);
        return row;
    }

    private static <C> JsonArray configurationPorts(ProgramNodeType<C> type, JsonElement encoded) {
        var decoded = type.configurationCodec().parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        var result = new JsonArray();
        for (var port : type.configurationPorts(decoded)) {
            var item = new JsonObject(); item.addProperty("field", port.field()); item.addProperty("type", port.type().id().toString()); result.add(item);
        }
        return result;
    }

    private static <C> org.academy.api.common.ability.program.ProgramNodeSchema resolved(ProgramNodeType<C> type, JsonElement encoded) {
        try {
            var decoded = type.configurationCodec().parse(JsonOps.INSTANCE, encoded).result().orElse(null);
            return decoded == null ? null : type.resolvedSchema(decoded);
        } catch (RuntimeException invalid) { return null; }
    }

    private static JsonArray ports(ProgramEditorNodeCatalog.Entry entry, List<ProgramPortDefinition> ports, JsonObject zh, JsonObject en) {
        var result = new JsonArray();
        for (var port : ports) {
            var row = new JsonObject();
            row.addProperty("name", port.name()); row.addProperty("type", port.type().id().toString());
            row.addProperty("required", port.required()); row.addProperty("max_connections", port.maxConnections());
            var key = port.name().matches("value_[0-9]+") ? "screen.academy.program.port.collection_builder_value" : entry.portTranslationKey(port.name());
            row.addProperty("translation_key", key); row.add("label", localized(key, zh, en));
            result.add(row);
        }
        return result;
    }

    private static JsonObject localized(String key, JsonObject zh, JsonObject en) {
        var result = new JsonObject();
        result.addProperty("key", key);
        result.addProperty("zh_cn_present", zh.has(key)); result.addProperty("en_us_present", en.has(key));
        if (zh.has(key)) result.add("zh_cn", zh.get(key));
        if (en.has(key)) result.add("en_us", en.get(key));
        return result;
    }

    private static JsonArray strings(List<String> values) { var array = new JsonArray(); values.forEach(array::add); return array; }
    private static JsonObject counts(Map<String, Integer> values) { var result = new JsonObject(); values.forEach(result::addProperty); return result; }
}
