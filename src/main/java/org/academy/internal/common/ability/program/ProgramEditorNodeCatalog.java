package org.academy.internal.common.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Editor-facing metadata for the shared algebra and one ability category.
 *
 * <p>Execution types remain the source of truth for schemas. This catalog only adds deterministic
 * defaults and presentation groups, then verifies every default through the node's real codec.</p>
 */
public final class ProgramEditorNodeCatalog implements ProgramNodeLookup {
    private final Identifier category;
    private final Map<Identifier, Entry> entries;
    private final List<Entry> orderedEntries;

    private ProgramEditorNodeCatalog(Identifier category, Map<Identifier, Entry> entries) {
        this.category = category;
        this.entries = Map.copyOf(entries);
        orderedEntries = entries.values().stream()
                .sorted(Comparator.comparing(Entry::group)
                        .thenComparingInt(ProgramEditorNodeCatalog::displayPriority)
                        .thenComparing(entry -> entry.id().toString()))
                .toList();
    }

    public Identifier category() {
        return category;
    }

    public List<Entry> entries() {
        return orderedEntries;
    }

    public @Nullable Entry entry(Identifier id) {
        return entries.get(id);
    }

    @Override
    public @Nullable ProgramNodeType<?> find(Identifier id) {
        var entry = entries.get(id);
        return entry == null ? null : entry.type();
    }

    public @Nullable ProgramNodeSchema schema(Identifier id, JsonElement configuration) {
        var entry = entries.get(id);
        return entry == null ? null : decodeSchema(entry.type(), configuration);
    }

    /** Decodes and re-encodes configuration so codecs can migrate and discard legacy fields. */
    public @Nullable JsonElement normalizeConfiguration(
            Identifier id,
            JsonElement configuration
    ) {
        var entry = entries.get(id);
        return entry == null ? null : normalize(entry.type(), configuration);
    }

    public static Builder builder(Identifier category) {
        return new Builder(category);
    }

    private static void put(
            Map<Identifier, Entry> entries,
            Identifier id,
            ProgramNodeType<?> type,
            JsonElement defaultConfiguration,
            Group group,
            String translationKey,
            String portTranslationPrefix,
            boolean visible,
            @Nullable Object metadata
    ) {
        visible = visible && !replacedByCommonTarget(id) && !replacedByCommonFilter(id);
        var schema = decodeSchema(type, defaultConfiguration);
        if (schema == null) {
            throw new IllegalStateException("Invalid editor default for program node " + id);
        }
        var entry = new Entry(
                id,
                type,
                defaultConfiguration,
                schema,
                group,
                displayName(id),
                translationKey,
                portTranslationPrefix,
                visible,
                metadata
        );
        if (entries.putIfAbsent(id, entry) != null) {
            throw new IllegalStateException("Duplicate editor node " + id);
        }
    }

    private static JsonElement commonDefault(Identifier id) {
        var configuration = new JsonObject();
        if (id.equals(CommonProgramNodeIds.SCALAR_CONSTANT)) {
            configuration.addProperty("type", "integer");
            configuration.addProperty("value", "0");
        } else if (id.equals(CommonProgramNodeIds.NUMERIC_ARITHMETIC)) {
            configuration.addProperty("type", "integer");
            configuration.addProperty("operator", "add");
        } else if (id.equals(CommonProgramNodeIds.NUMERIC_COMPARE)) {
            configuration.addProperty("type", "integer");
            configuration.addProperty("operator", "equal");
        } else if (id.equals(CommonProgramNodeIds.BOOLEAN_CONSTANT)) {
            configuration.addProperty("value", false);
        } else if (id.equals(CommonProgramNodeIds.INTEGER_CONSTANT)
                || id.equals(CommonProgramNodeIds.FLOAT_CONSTANT)) {
            configuration.addProperty("value", 0);
        } else if (id.equals(CommonProgramNodeIds.BIG_INTEGER_CONSTANT)) {
            configuration.addProperty("value", "0");
        } else if (id.equals(CommonProgramNodeIds.VARIABLE_GET)
                || id.equals(CommonProgramNodeIds.VARIABLE_SET)) {
            configuration.addProperty("name", "value");
            configuration.addProperty("type", ProgramValueTypes.BOOLEAN.id().toString());
        } else if (id.equals(CommonProgramNodeIds.FILTER_ENTITY_TYPE)) {
            configuration.addProperty("type", "living");
        } else if (id.equals(CommonProgramNodeIds.LOOK_TARGET)) {
            configuration.addProperty("target_type", "entity");
        } else if (id.equals(CommonProgramNodeIds.BLOCK_NORMAL)) {
            configuration.addProperty("mode", "view");
        } else if (id.equals(CommonProgramNodeIds.TRIGGER_LOOP)) {
            configuration.addProperty("enabled", true);
            configuration.addProperty("interval", 20);
        } else if (id.equals(CommonProgramNodeIds.TRIGGER_MOVEMENT)) {
            configuration.addProperty("condition", "jump");
        } else if (id.equals(CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD)) {
            configuration.addProperty("mode", "below");
            configuration.addProperty("threshold", 10.0f);
        } else if (id.equals(CommonProgramNodeIds.WORLD_POSITION_CONSTANT)) {
            configuration.addProperty("dimension", "minecraft:overworld");
            configuration.addProperty("x", 0.0);
            configuration.addProperty("y", 0.0);
            configuration.addProperty("z", 0.0);
        } else if (id.equals(CommonProgramNodeIds.BLOCK_POSITION_CONSTANT)) {
            configuration.addProperty("dimension", "minecraft:overworld");
            configuration.addProperty("x", 0);
            configuration.addProperty("y", 0);
            configuration.addProperty("z", 0);
        } else if (id.equals(CommonProgramNodeIds.WORLD_POSITION_CONSTRUCT)
                || id.equals(CommonProgramNodeIds.BLOCK_POSITION_CONSTRUCT)) {
            configuration.addProperty("dimension", "minecraft:overworld");
        } else if (id.equals(CommonProgramNodeIds.DIRECTION_CONSTANT)) {
            configuration.addProperty("x", 0.0);
            configuration.addProperty("y", 0.0);
            configuration.addProperty("z", 1.0);
        }
        return configuration;
    }

    private static int displayPriority(Entry entry) {
        var id = entry.id();
        var path = id.getPath();
        if (entry.group() == Group.TARGET) {
            if (id.equals(CommonProgramNodeIds.CASTER)) return -1000;
            if (id.equals(CommonProgramNodeIds.LOOK_TARGET)) return -990;
        }
        if (entry.group() == Group.COLLECTION) {
            if (path.contains("/collection/entity/")) return -1000;
            if (path.contains("/collection/block_position/")) return -700;
            if (path.contains("/collection/world_position/")) return -600;
            if (path.contains("/collection/direction/")) return -500;
        }
        if (entry.group() == Group.FLOW) {
            if (id.equals(CommonProgramNodeIds.TRIGGER_HURT)) return -1000;
            if (id.equals(CommonProgramNodeIds.TRIGGER_LOOP)) return -990;
            if (id.equals(CommonProgramNodeIds.TRIGGER_MELEE)) return -980;
            if (id.equals(CommonProgramNodeIds.TRIGGER_MOVEMENT)) return -970;
            if (path.endsWith("/entry/on_cast")) return -960;
            if (id.equals(CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD)) return -950;
        }
        return 0;
    }

    private static Group commonGroup(Identifier id, ProgramNodeRole role) {
        var path = id.getPath();
        if (id.equals(CommonProgramNodeIds.CASTER)
                || id.equals(CommonProgramNodeIds.LOOK_TARGET)
                || path.contains("/spatial/")
                || path.contains("/query/")) return Group.TARGET;
        if (role == ProgramNodeRole.CONTROL || role == ProgramNodeRole.ENTRY
                || path.contains("/flow/")) return Group.FLOW;
        if (path.contains("/collection/")) return Group.COLLECTION;
        if (path.contains("/filter/")) return Group.FILTER;
        if (path.contains("/logic/") || path.contains("/state/")) return Group.LOGIC;
        return Group.VALUE;
    }

    private static boolean commonVisible(Identifier id) {
        return !List.of(
                CommonProgramNodeIds.BOOLEAN_CONSTANT,
                CommonProgramNodeIds.INTEGER_CONSTANT,
                CommonProgramNodeIds.BIG_INTEGER_CONSTANT,
                CommonProgramNodeIds.FLOAT_CONSTANT,
                CommonProgramNodeIds.INTEGER_ADD,
                CommonProgramNodeIds.INTEGER_SUBTRACT,
                CommonProgramNodeIds.INTEGER_MULTIPLY,
                CommonProgramNodeIds.INTEGER_DIVIDE,
                CommonProgramNodeIds.INTEGER_MODULO,
                CommonProgramNodeIds.INTEGER_EQUAL,
                CommonProgramNodeIds.INTEGER_LESS,
                CommonProgramNodeIds.INTEGER_LESS_EQUAL,
                CommonProgramNodeIds.INTEGER_GREATER,
                CommonProgramNodeIds.INTEGER_GREATER_EQUAL,
                CommonProgramNodeIds.BIG_INTEGER_ADD,
                CommonProgramNodeIds.BIG_INTEGER_SUBTRACT,
                CommonProgramNodeIds.BIG_INTEGER_MULTIPLY,
                CommonProgramNodeIds.BIG_INTEGER_DIVIDE,
                CommonProgramNodeIds.BIG_INTEGER_MODULO,
                CommonProgramNodeIds.BIG_INTEGER_EQUAL,
                CommonProgramNodeIds.BIG_INTEGER_LESS,
                CommonProgramNodeIds.BIG_INTEGER_LESS_EQUAL,
                CommonProgramNodeIds.BIG_INTEGER_GREATER,
                CommonProgramNodeIds.BIG_INTEGER_GREATER_EQUAL,
                CommonProgramNodeIds.FLOAT_ADD,
                CommonProgramNodeIds.FLOAT_SUBTRACT,
                CommonProgramNodeIds.FLOAT_MULTIPLY,
                CommonProgramNodeIds.FLOAT_DIVIDE,
                CommonProgramNodeIds.FLOAT_MODULO,
                CommonProgramNodeIds.FLOAT_EQUAL,
                CommonProgramNodeIds.FLOAT_LESS,
                CommonProgramNodeIds.FLOAT_LESS_EQUAL,
                CommonProgramNodeIds.FLOAT_GREATER,
                CommonProgramNodeIds.FLOAT_GREATER_EQUAL
        ).contains(id);
    }

    private static boolean replacedByCommonTarget(Identifier id) {
        var path = id.getPath();
        return path.endsWith("/target/caster")
                || path.endsWith("/target/look_target")
                || path.endsWith("/target/look_living");
    }

    private static boolean replacedByCommonFilter(Identifier id) {
        return switch (id.getPath()) {
            case "program/filter/alive",
                 "program/filter/distance",
                 "program/filter/allies",
                 "program/filter/enemies",
                 "program/filter/targeted_by",
                 "program/filter/hostile_to",
                 "program/filter/last_damaged_by",
                 "program/filter/entity_type",
                 "program/filter/health",
                 "program/filter/health_below",
                 "program/filter/has_target",
                 "program/filter/visible_from" -> true;
            default -> false;
        };
    }

    private static String displayName(Identifier id) {
        var path = id.getPath()
                .replaceFirst("^program/core/", "")
                .replaceFirst("^program/", "");
        var pieces = path.split("/");
        var shown = pieces[pieces.length - 1].replace('_', ' ');
        if (pieces.length > 1 && (shown.equals("equal") || shown.equals("contains")
                || shown.equals("size") || shown.equals("get") || shown.equals("foreach"))) {
            shown = pieces[pieces.length - 2].replace('_', ' ') + " " + shown;
        }
        return shown;
    }

    private static <C> @Nullable ProgramNodeSchema decodeSchema(
            ProgramNodeType<C> type,
            JsonElement configuration
    ) {
        try {
            var decoded = type.configurationCodec()
                    .parse(JsonOps.INSTANCE, configuration)
                    .result()
                    .orElse(null);
            return decoded == null ? null : type.schema(decoded);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static <C> @Nullable JsonElement normalize(
            ProgramNodeType<C> type,
            JsonElement configuration
    ) {
        try {
            var decoded = type.configurationCodec()
                    .parse(JsonOps.INSTANCE, configuration)
                    .result()
                    .orElse(null);
            return decoded == null ? null : type.configurationCodec()
                    .encodeStart(JsonOps.INSTANCE, decoded)
                    .result()
                    .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public enum Group {
        TARGET,
        COLLECTION,
        FILTER,
        LOGIC,
        FLOW,
        ACTION,
        VALUE
    }

    public record Entry(
            Identifier id,
            ProgramNodeType<?> type,
            JsonElement defaultConfiguration,
            ProgramNodeSchema defaultSchema,
            Group group,
            String displayName,
            String translationKey,
            String portTranslationPrefix,
            boolean visible,
            @Nullable Object metadata
    ) {
        public Entry {
            defaultConfiguration = defaultConfiguration.deepCopy();
            if (translationKey == null || translationKey.isBlank()) {
                throw new IllegalArgumentException("Editor node translation key cannot be blank");
            }
            if (portTranslationPrefix == null || portTranslationPrefix.isBlank()) {
                throw new IllegalArgumentException("Editor port translation prefix cannot be blank");
            }
        }

        @Override
        public JsonElement defaultConfiguration() {
            return defaultConfiguration.deepCopy();
        }

        public String descriptionTranslationKey() {
            return translationKey() + ".description";
        }

        public String portTranslationKey(String port) {
            return portTranslationPrefix + port;
        }

        /**
         * Returns whether this node is limited to one or more ability categories.
         * Common algebra nodes deliberately leave the allowed-category set empty.
         */
        public boolean categoryRestricted() {
            return !type.scope().allowedCategories().isEmpty();
        }

        /**
         * Returns the single owning category when the node is category-exclusive.
         */
        public Optional<Identifier> exclusiveCategory() {
            var categories = type.scope().allowedCategories();
            return categories.size() == 1
                    ? Optional.of(categories.iterator().next())
                    : Optional.empty();
        }

        public <T> Optional<T> metadata(Class<T> type) {
            Objects.requireNonNull(type, "type");
            return type.isInstance(metadata) ? Optional.of(type.cast(metadata)) : Optional.empty();
        }
    }

    public static final class Builder {
        private final Identifier category;
        private final Map<Identifier, Entry> entries = new HashMap<>();

        private Builder(Identifier category) {
            this.category = Objects.requireNonNull(category, "category");
        }

        public Builder includeCommonNodes() {
            CommonProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> add(
                    id,
                    type,
                    commonDefault(id),
                    commonGroup(id, type.role()),
                    "screen.academy.program.node." + id.getPath()
                            .replaceFirst("^program/core/", "")
                            .replace('/', '.'),
                    "screen.academy.program.port.",
                    commonVisible(id),
                    null
            ));
            return this;
        }

        public Builder add(
                Identifier id,
                ProgramNodeType<?> type,
                JsonElement defaultConfiguration,
                Group group,
                String translationKey,
                String portTranslationPrefix,
                @Nullable Object metadata
        ) {
            return add(
                    id,
                    type,
                    defaultConfiguration,
                    group,
                    translationKey,
                    portTranslationPrefix,
                    true,
                    metadata
            );
        }

        public Builder add(
                Identifier id,
                ProgramNodeType<?> type,
                JsonElement defaultConfiguration,
                Group group,
                String translationKey,
                String portTranslationPrefix,
                boolean visible,
                @Nullable Object metadata
        ) {
            put(
                    entries,
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(type, "type"),
                    Objects.requireNonNull(defaultConfiguration, "defaultConfiguration"),
                    Objects.requireNonNull(group, "group"),
                    translationKey,
                    portTranslationPrefix,
                    visible,
                    metadata
            );
            return this;
        }

        public ProgramEditorNodeCatalog build() {
            if (entries.isEmpty()) {
                throw new IllegalStateException("Editor catalog must contain at least one node");
            }
            return new ProgramEditorNodeCatalog(category, entries);
        }
    }
}
