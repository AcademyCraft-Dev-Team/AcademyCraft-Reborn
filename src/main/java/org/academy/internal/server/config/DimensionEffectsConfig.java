package org.academy.internal.server.config;

import com.google.gson.GsonBuilder;
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

    public enum Mode {
        WHITELIST,
        BLACKLIST
    }

    public static final class DimensionRule {
        public Mode mode = Mode.BLACKLIST;
        public Set<String> dimensions = new HashSet<>();

        public boolean allows(String dimension) {
            return mode == Mode.WHITELIST
                    ? dimensions.contains(dimension)
                    : !dimensions.contains(dimension);
        }

        private void validate(String name) {
            if (mode == null || dimensions == null) {
                throw new IllegalArgumentException(name + " requires a valid mode and dimensions array");
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
            var config = gson.fromJson(Files.readString(file), DimensionEffectsConfig.class);
            if (config == null) throw new IllegalArgumentException("Configuration must be a JSON object");
            config.validate();
            return config;
        } catch (IOException | RuntimeException error) {
            // A typo must not silently turn off protection or overwrite the administrator's file.
            throw new IllegalStateException("Cannot load dimension effect rules from " + file, error);
        }
    }
}
