package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Editable task examples made only from existing, entitled node types. Creation has no runtime side effects. */
public final class ProgramTaskTemplates {
    private ProgramTaskTemplates() {}

    public enum Kind {
        ACCELERATOR_GUIDE_STRUCTURE("accelerator", "structure/move_group", Shape.STRUCTURE),
        ACCELERATOR_ROTATE_STRUCTURE("accelerator", "structure/rotate_group", Shape.STRUCTURE),
        ELECTROMASTER_GUIDE_STRUCTURE("electromaster", "structure/move_group", Shape.STRUCTURE),
        ELECTROMASTER_ROTATE_STRUCTURE("electromaster", "structure/rotate_group", Shape.STRUCTURE),
        AEROMANIP_GUIDE_STRUCTURE("aeromanip", "structure/move_group", Shape.STRUCTURE),
        AEROMANIP_ROTATE_STRUCTURE("aeromanip", "structure/rotate_group", Shape.STRUCTURE),
        TELEPORT_FORWARD("teleport", "self_teleport", Shape.TELEPORT),
        MELTDOWNER_MINING_BEAM("meltdowner", "mining_beam", Shape.MINING),
        DARKMATTER_DISASSEMBLE_BLOCK("darkmatter", "disassemble_block", Shape.BLOCK_TARGET),
        MENTALOUT_MENTAL_STUPOR("mentalout", "mental_stupor", Shape.ENTITY_SET_TARGET);

        private final Identifier category;
        private final Identifier action;
        private final Shape shape;

        Kind(String category, String action, Shape shape) {
            this.category = AcademyCraft.academy(category);
            this.action = AcademyCraft.academy("program/" + category + "/action/" + action);
            this.shape = shape;
        }

        public Identifier category() { return category; }
        public String translationKey() {
            return "screen.academy.program.task_template." + name().toLowerCase(Locale.ROOT);
        }
        public String descriptionTranslationKey() { return translationKey() + ".description"; }
    }

    private enum Shape { STRUCTURE, TELEPORT, MINING, BLOCK_TARGET, ENTITY_SET_TARGET }

    /** Category membership is independent of whether the player has learned the required skills. */
    public static List<Kind> forCategory(AbilityProgramDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return Arrays.stream(Kind.values()).filter(kind -> kind.category.equals(definition.category())).toList();
    }

    /** The palette may offer only recipes whose original nodes are present, released and entitled. */
    public static List<Kind> available(AbilityProgramDefinition definition, Set<Identifier> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        return forCategory(definition).stream().filter(kind -> supported(definition, kind)
                && capabilities.containsAll(requiredCapabilities(definition, kind))).toList();
    }

    /** Uses the real node scopes, plus the learned skill enforced at runtime by the legacy mental action. */
    public static Set<Identifier> requiredCapabilities(AbilityProgramDefinition definition, Kind kind) {
        requireCategory(definition, kind);
        var required = new HashSet<Identifier>();
        for (var type : nodeTypes(kind)) {
            var entry = definition.editorCatalog().entry(type);
            if (entry == null || !entry.type().scope().allowsCategory(definition.category())) {
                throw new IllegalArgumentException("Task template node is unavailable: " + type);
            }
            required.addAll(entry.type().scope().requiredCapabilities());
        }
        // Precision bridge nodes retain category-only scopes; the template must still require
        // its actual learned skill without weakening the original runtime's own admission.
        if (kind == Kind.MENTALOUT_MENTAL_STUPOR) required.add(AcademyCraft.academy(SkillNames.MENTAL_STUPOR));
        return Set.copyOf(required);
    }

    /** Builds a normal expanded graph for an empty slot, retaining its UUID, name and category. */
    public static AbilityProgram create(AbilityProgram empty, AbilityProgramDefinition definition,
                                        Set<Identifier> capabilities, Kind kind) {
        Objects.requireNonNull(empty, "empty");
        Objects.requireNonNull(capabilities, "capabilities");
        requireCategory(definition, kind);
        if (!empty.category().equals(definition.category())) {
            throw new IllegalArgumentException("Task template category does not match the slot");
        }
        if (!empty.graph().nodes().isEmpty() || !empty.graph().edges().isEmpty()) {
            throw new IllegalArgumentException("Task template requires an empty slot");
        }
        if (!supported(definition, kind) || !capabilities.containsAll(requiredCapabilities(definition, kind))) {
            throw new IllegalArgumentException("Task template requires available nodes and learned capabilities");
        }
        var builder = new Builder(empty, definition, capabilities);
        int entry = builder.add(manualEntry(definition), 24, 48);
        switch (kind.shape) {
            case STRUCTURE, MINING -> {
                int action = builder.add(kind.action, 320, 48);
                builder.connect(entry, "flow", action, "flow");
            }
            case BLOCK_TARGET -> {
                int action = builder.add(kind.action, 340, 48);
                int target = builder.add(CommonProgramNodeIds.LOOK_TARGET, 24, 220,
                        config -> config.addProperty("target_type", "block"));
                builder.connect(entry, "flow", action, "flow");
                builder.connect(target, "block", action, "block");
            }
            case ENTITY_SET_TARGET -> {
                int action = builder.add(kind.action, 560, 48);
                int target = builder.add(CommonProgramNodeIds.LOOK_TARGET, 24, 230,
                        config -> config.addProperty("target_type", "entity"));
                int singleton = builder.add(CommonProgramNodeIds.collection("entity", "singleton"), 280, 230);
                builder.connect(entry, "flow", action, "flow");
                builder.connect(target, "entity", singleton, "value");
                builder.connect(singleton, "values", action, "subjects");
            }
            case TELEPORT -> {
                int action = builder.add(kind.action, 850, 48);
                int caster = builder.add(CommonProgramNodeIds.CASTER, 24, 230);
                int position = builder.add(CommonProgramNodeIds.ENTITY_POSITION, 270, 190,
                        config -> config.addProperty("anchor", "feet"));
                int direction = builder.add(CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, 270, 350);
                int distance = builder.add(CommonProgramNodeIds.SCALAR_CONSTANT, 270, 510, config -> {
                    config.addProperty("type", "float");
                    config.addProperty("value", "3.0");
                });
                int destination = builder.add(CommonProgramNodeIds.WORLD_POSITION_OFFSET, 550, 190);
                builder.connect(entry, "flow", action, "flow");
                builder.connect(caster, "entity", position, "entity");
                builder.connect(caster, "entity", direction, "entity");
                builder.connect(position, "position", destination, "position");
                builder.connect(direction, "direction", destination, "direction");
                builder.connect(distance, "value", destination, "distance");
                builder.connect(destination, "position", action, "destination");
            }
        }
        var program = builder.document.program();
        if (!definition.compile(program, capabilities).valid()) {
            throw new IllegalStateException("Task template did not compile: " + kind);
        }
        return program;
    }

    private static void requireCategory(AbilityProgramDefinition definition, Kind kind) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(kind, "kind");
        if (!kind.category.equals(definition.category())) {
            throw new IllegalArgumentException("Task template belongs to a different ability category");
        }
    }

    private static boolean supported(AbilityProgramDefinition definition, Kind kind) {
        return nodeTypes(kind).stream().allMatch(type -> {
            var entry = definition.editorCatalog().entry(type);
            return entry != null && entry.visible()
                    && entry.presentationStatus() == ProgramEditorNodeCatalog.PresentationStatus.AVAILABLE
                    && entry.type().scope().allowsCategory(definition.category());
        });
    }

    private static List<Identifier> nodeTypes(Kind kind) {
        return switch (kind.shape) {
            case STRUCTURE, MINING -> List.of(kind.action);
            case BLOCK_TARGET -> List.of(kind.action, CommonProgramNodeIds.LOOK_TARGET);
            case ENTITY_SET_TARGET -> List.of(kind.action, CommonProgramNodeIds.LOOK_TARGET,
                    CommonProgramNodeIds.collection("entity", "singleton"));
            case TELEPORT -> List.of(kind.action, CommonProgramNodeIds.CASTER, CommonProgramNodeIds.ENTITY_POSITION,
                    CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, CommonProgramNodeIds.SCALAR_CONSTANT,
                    CommonProgramNodeIds.WORLD_POSITION_OFFSET);
        };
    }

    private static Identifier manualEntry(AbilityProgramDefinition definition) {
        return definition.categoryNodeTypes().entrySet().stream()
                .filter(entry -> entry.getValue().role() == ProgramNodeRole.ENTRY)
                .map(java.util.Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task template requires a manual program entry"));
    }

    private static final class Builder {
        private ProgramEditorDocument document;
        private final AbilityProgramDefinition definition;

        private Builder(AbilityProgram empty, AbilityProgramDefinition definition, Set<Identifier> capabilities) {
            this.definition = definition;
            document = new ProgramEditorDocument(empty, definition, capabilities);
        }

        private int add(Identifier type, double x, double y) {
            return add(type, x, y, _ -> {});
        }

        private int add(Identifier type, double x, double y, Consumer<JsonObject> configure) {
            var entry = definition.editorCatalog().entry(type);
            if (entry == null || !entry.defaultConfiguration().isJsonObject()) {
                throw new IllegalArgumentException("Task template node has no supported default: " + type);
            }
            var configuration = entry.defaultConfiguration().getAsJsonObject();
            configure.accept(configuration);
            var before = document.program().graph().nodes().stream().map(ProgramGraph.Node::id)
                    .collect(java.util.stream.Collectors.toSet());
            var result = document.addNode(type, x, y, configuration);
            if (!result.successful()) throw new IllegalStateException("Task template node could not be added: " + type);
            document = result.document();
            return document.program().graph().nodes().stream().map(ProgramGraph.Node::id)
                    .filter(id -> !before.contains(id)).findFirst().orElseThrow();
        }

        private void connect(int from, String output, int to, String input) {
            var result = document.connect(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
            if (!result.successful()) throw new IllegalStateException("Task template connection failed: " + output + " -> " + input);
            document = result.document();
        }
    }
}
