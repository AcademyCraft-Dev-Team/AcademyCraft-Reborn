package org.academy.api.common.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable editor presentation supplied by a registered program-node extension.
 *
 * <p>Configuration options are optional. Fields without options continue to use the editor's
 * validated text input, which is suitable for stable identifiers and bounded numeric values.</p>
 */
public record ProgramNodeEditorMetadata(
        JsonElement defaultConfiguration,
        Group group,
        String translationKey,
        String portTranslationPrefix,
        boolean visible,
        Map<String, List<ConfigurationOption>> configurationOptions
) {
    public ProgramNodeEditorMetadata {
        Objects.requireNonNull(defaultConfiguration, "defaultConfiguration");
        Objects.requireNonNull(group, "group");
        requireText(translationKey, "translationKey");
        requireText(portTranslationPrefix, "portTranslationPrefix");
        defaultConfiguration = defaultConfiguration.deepCopy();
        configurationOptions = configurationOptions == null
                ? Map.of()
                : configurationOptions.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> requireText(entry.getKey(), "configuration field"),
                entry -> validatedOptions(entry.getKey(), entry.getValue())
        ));
    }

    public ProgramNodeEditorMetadata(
            JsonElement defaultConfiguration,
            Group group,
            String translationKey,
            String portTranslationPrefix
    ) {
        this(defaultConfiguration, group, translationKey, portTranslationPrefix, true, Map.of());
    }

    @Override
    public JsonElement defaultConfiguration() {
        return defaultConfiguration.deepCopy();
    }

    public List<ConfigurationOption> options(String field) {
        return configurationOptions.getOrDefault(field, List.of());
    }

    public Optional<ConfigurationOption> selectedOption(String field, JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return Optional.empty();
        var primitive = value.getAsJsonPrimitive();
        return options(field).stream().filter(option -> option.value().equals(primitive)).findFirst();
    }

    private static List<ConfigurationOption> validatedOptions(
            String field,
            List<ConfigurationOption> options
    ) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException(
                    "Program editor options cannot be empty for field " + field);
        }
        var values = new HashSet<JsonPrimitive>();
        var copy = List.copyOf(options);
        for (var option : copy) {
            Objects.requireNonNull(option, "configuration option");
            if (!values.add(option.value())) {
                throw new IllegalArgumentException(
                        "Duplicate program editor option for field " + field);
            }
        }
        return copy;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Program editor " + name + " cannot be blank");
        }
        return value;
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

    public record ConfigurationOption(JsonPrimitive value, String translationKey) {
        public ConfigurationOption {
            Objects.requireNonNull(value, "value");
            requireText(translationKey, "option translationKey");
        }

        public ConfigurationOption(boolean value, String translationKey) {
            this(new JsonPrimitive(value), translationKey);
        }

        public ConfigurationOption(int value, String translationKey) {
            this(new JsonPrimitive(value), translationKey);
        }

        public ConfigurationOption(double value, String translationKey) {
            this(new JsonPrimitive(value), translationKey);
        }

        public ConfigurationOption(String value, String translationKey) {
            this(new JsonPrimitive(value), translationKey);
        }
    }
}
