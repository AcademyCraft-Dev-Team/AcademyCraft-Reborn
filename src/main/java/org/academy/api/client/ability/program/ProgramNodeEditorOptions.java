package org.academy.api.client.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side, player-specific configuration choices for externally registered program nodes.
 *
 * <p>Static choices belong in the node's common editor metadata. This registry is intended for
 * choices that arrive from the current server, such as a bounded catalog learned by one player.
 * Values are still decoded and validated by the common node type when the graph is configured;
 * a provider only controls editor presentation and grants no execution authority.</p>
 */
public final class ProgramNodeEditorOptions {
    private static final int MAX_OPTIONS = 512;
    private static final Map<Identifier, Provider> PROVIDERS = new ConcurrentHashMap<>();

    private ProgramNodeEditorOptions() {
    }

    /**
     * Registers the sole dynamic-option provider for {@code nodeType}.
     *
     * @return a handle that removes this exact registration when closed
     */
    public static Registration register(Identifier nodeType, Provider provider) {
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.putIfAbsent(nodeType, provider) != null) {
            throw new IllegalStateException(
                    "A dynamic program editor provider is already registered for " + nodeType);
        }
        return () -> PROVIDERS.remove(nodeType, provider);
    }

    /**
     * Returns a defensive, bounded snapshot supplied for the current client state.
     */
    public static List<Option> options(
            Identifier nodeType,
            String field,
            JsonElement currentValue
    ) {
        Objects.requireNonNull(nodeType, "nodeType");
        if (field == null || field.isBlank()) return List.of();
        var provider = PROVIDERS.get(nodeType);
        if (provider == null) return List.of();
        var supplied = provider.options(field,
                currentValue == null ? null : currentValue.deepCopy());
        if (supplied == null || supplied.isEmpty()) return List.of();
        if (supplied.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(
                    "Dynamic program editor options exceed the " + MAX_OPTIONS + " entry limit");
        }
        return supplied.stream().map(ProgramNodeEditorOptions::copy).toList();
    }

    private static Option copy(Option option) {
        Objects.requireNonNull(option, "dynamic program editor option");
        return new Option(option.value(), option.label().copy());
    }

    @FunctionalInterface
    public interface Provider {
        List<Option> options(String field, JsonElement currentValue);
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * One primitive configuration value and its already-localized or literal client label.
     */
    public record Option(JsonPrimitive value, Component label) {
        public Option {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(label, "label");
            value = value.deepCopy();
            label = label.copy();
        }

        @Override
        public JsonPrimitive value() {
            return value.deepCopy();
        }

        @Override
        public Component label() {
            return label.copy();
        }
    }
}
