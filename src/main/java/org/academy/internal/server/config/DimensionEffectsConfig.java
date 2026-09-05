package org.academy.internal.server.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Server-owned dimension policy, loaded once for each dedicated or integrated server. */
public final class DimensionEffectsConfig {
    public static final String FILE_NAME = "academy-dimension-effects.json";

    public boolean enabled = false;
    public DimensionRule pvp = new DimensionRule();
    public DimensionRule blockDestruction = new DimensionRule();

    public static final class DimensionRule {
        public Set<String> whitelist = new HashSet<>();
        public Set<String> blacklist = new HashSet<>();

        private void validate(String name) {
            validateDimensions(name + ".whitelist", whitelist);
            validateDimensions(name + ".blacklist", blacklist);
        }

        private static void validateDimensions(String name, Set<String> dimensions) {
            if (dimensions == null) {
                throw new IllegalArgumentException(name + " must be a dimensions array");
            }
            for (var dimension : dimensions) {
                if (dimension == null || !dimension.contains(":") || Identifier.tryParse(dimension) == null) {
                    throw new IllegalArgumentException(name + " contains an invalid dimension ID: " + dimension);
                }
            }
        }
    }

    public void validate() {
        if (pvp == null || blockDestruction == null) {
            throw new IllegalArgumentException("pvp and blockDestruction rules must not be null");
        }
        pvp.validate("pvp");
        blockDestruction.validate("blockDestruction");
    }

    public static DimensionEffectsConfig load(Path file) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (!Files.exists(file)) {
                var defaults = new DimensionEffectsConfig();
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, gson.toJson(defaults) + System.lineSeparator());
                return defaults;
            }
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            migrateLegacyRule(root, "pvp");
            migrateLegacyRule(root, "blockDestruction");
            var config = gson.fromJson(root, DimensionEffectsConfig.class);
            config.validate();
            return config;
        } catch (IOException | RuntimeException error) {
            // A typo must not silently turn off protection or overwrite the administrator's file.
            throw new IllegalStateException("Cannot load dimension effect rules from " + file, error);
        }
    }

    /** Read old mode/dimensions files without rewriting them or merging stale lists into new rules. */
    private static void migrateLegacyRule(JsonObject root, String name) {
        if (!root.has(name) || !root.get(name).isJsonObject()) return;
        var rule = root.getAsJsonObject(name);
        if (rule.has("whitelist") || rule.has("blacklist")) return;
        if (!rule.has("mode") && !rule.has("dimensions")) return;
        var mode = rule.has("mode") ? rule.get("mode").getAsString() : "BLACKLIST";
        var destination = switch (mode) {
            case "WHITELIST" -> "whitelist";
            case "BLACKLIST" -> "blacklist";
            default -> throw new IllegalArgumentException(name + " contains an invalid legacy mode: " + mode);
        };
        rule.add(destination, rule.has("dimensions") ? rule.get("dimensions") : new JsonArray());
    }
}
