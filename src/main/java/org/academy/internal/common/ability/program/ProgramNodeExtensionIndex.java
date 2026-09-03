package org.academy.internal.common.ability.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramNodeEditorMetadata;
import org.academy.api.common.ability.program.ProgramNodeExtension;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.registries.Registries;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validated, deterministic view of registry-backed program-node extensions.
 */
public final class ProgramNodeExtensionIndex {
    private static volatile @Nullable Map<Identifier, Snapshot> frozenSnapshots;

    private ProgramNodeExtensionIndex() {
    }

    /**
     * Freezes the extension set after NeoForge has completed normal registry construction.
     */
    public static synchronized void freeze() {
        if (frozenSnapshots != null) return;
        var candidates = registryCandidates();
        var snapshots = new LinkedHashMap<Identifier, Snapshot>();
        AbilityProgramDefinitions.all().stream()
                .map(AbilityProgramDefinition::category)
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(category -> snapshots.put(category, build(category, candidates)));
        frozenSnapshots = Map.copyOf(snapshots);
        var extensionCount = snapshots.values().stream()
                .flatMap(snapshot -> snapshot.registrations().stream())
                .map(Registration::id)
                .distinct()
                .count();
        var categoryCount = snapshots.values().stream()
                .filter(snapshot -> !snapshot.registrations().isEmpty())
                .count();
        AcademyCraft.LOGGER.info(
                "Frozen {} program-node extensions across {} ability categories.",
                extensionCount,
                categoryCount
        );
    }

    public static Snapshot snapshot(Identifier category) {
        Objects.requireNonNull(category, "category");
        var frozen = frozenSnapshots;
        if (frozen != null) {
            return frozen.getOrDefault(category, Snapshot.empty(category));
        }
        return build(category, registryCandidates());
    }

    static Snapshot build(
            Identifier category,
            Map<Identifier, ProgramNodeType<?>> candidates
    ) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(candidates, "candidates");
        var registrations = new ArrayList<Registration>();
        candidates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    if (!(entry.getValue() instanceof ProgramNodeExtension<?> extension)
                            || !extension.scope().allowsCategory(category)) {
                        return;
                    }
                    try {
                        registrations.add(validate(entry.getKey(), category, extension));
                    } catch (RuntimeException exception) {
                        AcademyCraft.LOGGER.error(
                                "Rejecting invalid program-node extension {} for {}",
                                entry.getKey(), category, exception);
                    }
                });
        return new Snapshot(category, registrations, fingerprint(category, registrations));
    }

    private static Map<Identifier, ProgramNodeType<?>> registryCandidates() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        Registries.PROGRAM_NODE_TYPES.keySet().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(id -> Registries.PROGRAM_NODE_TYPES.get(id)
                        .ifPresent(reference -> result.put(id, reference.value())));
        return Map.copyOf(result);
    }

    private static <C> Registration validate(
            Identifier id,
            Identifier category,
            ProgramNodeExtension<C> extension
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(extension, "extension");
        if (!extension.scope().allowsCategory(category)) {
            throw new IllegalArgumentException("Extension scope rejects category " + category);
        }
        if (extension.compatibilityVersion() < 1) {
            throw new IllegalArgumentException("Extension compatibility version must be positive");
        }
        if (extension.execution() == null) {
            throw new IllegalArgumentException("Extension executor cannot be null");
        }
        var metadata = Objects.requireNonNull(extension.editorMetadata(), "editorMetadata");
        var configuration = extension.configurationCodec()
                .parse(JsonOps.INSTANCE, metadata.defaultConfiguration())
                .result()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Extension editor default cannot be decoded"));
        Objects.requireNonNull(extension.schema(configuration), "extension schema");
        return new Registration(id, extension, metadata, adapt(extension));
    }

    private static <C> ProgramNodeExecutor<C> adapt(ProgramNodeExtension<C> extension) {
        var execution = extension.execution();
        return (context, configuration, inputs) ->
                execution.execute(context, configuration, inputs);
    }

    private static String fingerprint(
            Identifier category,
            Collection<Registration> registrations
    ) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, "academy-program-extensions-v1\n");
            update(digest, category + "\n");
            for (var registration : registrations) {
                var extension = registration.extension();
                var metadata = registration.editorMetadata();
                update(digest, registration.id() + "\n");
                update(digest, extension.getClass().getName() + "\n");
                update(digest, extension.schemaVersion() + ":"
                        + extension.compatibilityVersion() + "\n");
                update(digest, extension.role() + ":" + extension.purity() + "\n");
                extension.scope().allowedCategories().stream()
                        .sorted(Comparator.comparing(Identifier::toString))
                        .forEach(value -> update(digest, "category=" + value + "\n"));
                extension.scope().requiredCapabilities().stream()
                        .sorted(Comparator.comparing(Identifier::toString))
                        .forEach(value -> update(digest, "capability=" + value + "\n"));
                update(digest, metadata.group() + ":" + metadata.visible() + "\n");
                update(digest, metadata.translationKey() + "\n");
                update(digest, metadata.portTranslationPrefix() + "\n");
                update(digest, canonicalJson(metadata.defaultConfiguration()) + "\n");
                metadata.configurationOptions().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            update(digest, "field=" + entry.getKey() + "\n");
                            for (var option : entry.getValue()) {
                                update(digest, canonicalJson(option.value()) + "="
                                        + option.translationKey() + "\n");
                            }
                        });
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalJson(JsonElement element) {
        if (element.isJsonObject()) {
            var source = element.getAsJsonObject();
            var sorted = new JsonObject();
            source.keySet().stream().sorted()
                    .forEach(key -> sorted.add(key, canonicalized(source.get(key))));
            return sorted.toString();
        }
        return canonicalized(element).toString();
    }

    private static JsonElement canonicalized(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            element.getAsJsonObject().keySet().stream().sorted()
                    .forEach(key -> result.add(
                            key, canonicalized(element.getAsJsonObject().get(key))));
            return result;
        }
        if (element.isJsonArray()) {
            var result = new JsonArray();
            element.getAsJsonArray().forEach(value -> result.add(canonicalized(value)));
            return result;
        }
        return element.deepCopy();
    }

    public record Registration(
            Identifier id,
            ProgramNodeExtension<?> extension,
            ProgramNodeEditorMetadata editorMetadata,
            ProgramNodeExecutor<?> executor
    ) {
    }

    public record Snapshot(
            Identifier category,
            List<Registration> registrations,
            String fingerprint
    ) {
        public Snapshot {
            registrations = List.copyOf(registrations);
        }

        public @Nullable Registration find(Identifier id) {
            return registrations.stream()
                    .filter(registration -> registration.id().equals(id))
                    .findFirst()
                    .orElse(null);
        }

        private static Snapshot empty(Identifier category) {
            return new Snapshot(
                    category,
                    List.of(),
                    ProgramNodeExtensionIndex.fingerprint(category, List.of())
            );
        }
    }
}
