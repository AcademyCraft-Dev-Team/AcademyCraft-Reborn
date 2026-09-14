package org.academy.api.client.ability.program;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Player-specific named presets. A preset grants no authority and never registers a node type. */
public final class ProgramNodePalette {
    private static final Map<Identifier, Supplier<List<Preset>>> PROVIDERS = new ConcurrentHashMap<>();
    private ProgramNodePalette() { }

    public static void register(Identifier id, Supplier<List<Preset>> provider) {
        if (PROVIDERS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(provider)) != null)
            throw new IllegalStateException("Duplicate palette provider " + id);
    }
    public static boolean hasProvider(Identifier id) { return PROVIDERS.containsKey(id); }
    public static List<Preset> presets(Identifier id) {
        var provider = PROVIDERS.get(id);
        if (provider == null) return List.of();
        var values = List.copyOf(provider.get());
        if (values.size() > 512) throw new IllegalArgumentException("Too many node presets");
        return values;
    }
    @Nullable
    public static Component label(Identifier id, JsonElement configuration) {
        return presets(id).stream().filter(p -> p.configuration().equals(configuration))
                .map(Preset::label).findFirst().orElse(null);
    }
    public record Preset(JsonElement configuration, Component label) {
        public Preset {
            configuration = Objects.requireNonNull(configuration).deepCopy();
            label = Objects.requireNonNull(label).copy();
        }
        @Override public JsonElement configuration() { return configuration.deepCopy(); }
        @Override public Component label() { return label.copy(); }
    }
}
