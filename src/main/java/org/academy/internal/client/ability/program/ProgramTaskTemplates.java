package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramValueType;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.PrecisionProgramNodeIds;

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
        MENTALOUT_MENTAL_STUPOR("mentalout", "mental_stupor", Shape.ENTITY_SET_TARGET),
        ACCELERATOR_GUIDE_OWNED_NEARBY("accelerator", "structure/move_group", Shape.OWNED_STRUCTURE_SET),
        ELECTROMASTER_RECHARGE_LOW_BATTERY("electromaster", "current_recharge", Shape.LOW_BATTERY),
        AEROMANIP_GUIDE_WITH_NOZZLES("aeromanip", "structure/move_group", Shape.NOZZLE_STRUCTURE_SET),
        TELEPORT_CHECKED_FORWARD("teleport", "self_teleport", Shape.CHECKED_TELEPORT),
        MELTDOWNER_LOCKED_MINING("meltdowner", "mining_beam", Shape.LOCKED_MINING),
        DARKMATTER_FILTERED_BLOCK("darkmatter", "disassemble_block", Shape.FILTERED_BLOCK),
        MENTALOUT_ROSTER_GUARD("mentalout", "guard_mode", Shape.ROSTER_GUARD);

        private final Identifier category;
        private final Identifier action;
        private final Shape shape;

        Kind(String category, String action, Shape shape) {
            this.category = AcademyCraft.academy(category);
            this.action = AcademyCraft.academy("program/" + category + "/action/" + action);
            this.shape = shape;
        }

        public Identifier category() { return category; }
        public boolean advanced() { return shape.ordinal() >= Shape.OWNED_STRUCTURE_SET.ordinal(); }
        public String translationKey() {
            return "screen.academy.program.task_template." + name().toLowerCase(Locale.ROOT);
        }
        public String descriptionTranslationKey() { return translationKey() + ".description"; }
    }

    private enum Shape {
        STRUCTURE, TELEPORT, MINING, BLOCK_TARGET, ENTITY_SET_TARGET,
        OWNED_STRUCTURE_SET, LOW_BATTERY, NOZZLE_STRUCTURE_SET, CHECKED_TELEPORT,
        LOCKED_MINING, FILTERED_BLOCK, ROSTER_GUARD
    }

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
            case OWNED_STRUCTURE_SET, NOZZLE_STRUCTURE_SET -> structureSetTask(builder, entry, kind);
            case LOW_BATTERY -> lowBatteryTask(builder, entry, kind);
            case CHECKED_TELEPORT -> checkedTeleportTask(builder, entry, kind);
            case LOCKED_MINING -> lockedMiningTask(builder, entry, kind);
            case FILTERED_BLOCK -> filteredBlockTask(builder, entry, kind);
            case ROSTER_GUARD -> rosterGuardTask(builder, entry, kind);
        }
        var program = builder.document.program();
        if (!definition.compile(program, capabilities).valid()) {
            throw new IllegalStateException("Task template did not compile: " + kind);
        }
        return program;
    }

    private static void structureSetTask(Builder b, int entry, Kind kind) {
        int caster = b.add(CommonProgramNodeIds.CASTER, 24, 260);
        int position = b.add(CommonProgramNodeIds.ENTITY_POSITION, 260, 260);
        int radius = b.scalar("float", "8.0", 260, 420);
        int nearby = b.add(CommonProgramNodeIds.STRUCTURES_AROUND, 500, 260);
        int owned = b.add(CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE, 740, 260,
                config -> config.addProperty("source_mode", "owned_by_caster"));
        int sample = b.sample(ProgramValueTypes.STRUCTURE_SET, 1000, 48);
        b.connect(caster, "entity", position, "entity");
        b.connect(position, "position", nearby, "center");
        b.connect(radius, "value", nearby, "radius");
        b.connect(nearby, "structures", owned, "structures");
        b.connect(owned, "structures", sample, "value");
        b.connect(entry, "flow", sample, "flow");
        int condition = b.nonempty(sample, "structure", 1240, 300);
        if (kind.shape == Shape.NOZZLE_STRUCTURE_SET) {
            int nozzles = b.add(AcademyCraft.academy("program/aeromanip/query/owned_jet_nozzle_count"), 500, 520);
            b.connect(position, "position", nozzles, "center");
            b.connect(radius, "value", nozzles, "radius");
            int zero = b.scalar("integer", "0", 740, 700);
            int positive = b.compare("integer", "greater", 1000, 540);
            b.connect(nozzles, "count", positive, "left");
            b.connect(zero, "value", positive, "right");
            int both = b.add(CommonProgramNodeIds.BOOLEAN_AND, 1740, 350);
            b.connect(condition, "result", both, "left");
            b.connect(positive, "result", both, "right");
            condition = both;
        }
        int branch = b.add(CommonProgramNodeIds.BRANCH, 1750, 48);
        int action = b.add(kind.action, 2000, 48, config -> config.addProperty("duration_seconds", 1.0));
        b.connect(sample, "flow", branch, "flow");
        b.connect(condition, "result", branch, "condition");
        b.connect(sample, "value", action, "structures");
        b.connect(branch, "true", action, "flow");
        b.finishBranches(branch, action, 2260);
    }

    private static void lowBatteryTask(Builder b, int entry, Kind kind) {
        int target = b.add(CommonProgramNodeIds.LOOK_TARGET, 24, 250,
                config -> config.addProperty("target_type", "block"));
        int sample = b.sample(ProgramValueTypes.BLOCK_POSITION, 300, 48);
        int energy = b.add(AcademyCraft.academy("program/electromaster/query/energy_level"), 560, 250,
                config -> config.addProperty("target_type", "block"));
        int threshold = b.scalar("float", "50.0", 560, 460);
        int low = b.compare("float", "less", 820, 300);
        int availableAndLow = b.add(CommonProgramNodeIds.BOOLEAN_AND, 1080, 250);
        int branch = b.add(CommonProgramNodeIds.BRANCH, 1340, 48);
        int action = b.add(kind.action, 1600, 48, config -> config.addProperty("target_type", "block"));
        b.connect(entry, "flow", sample, "flow");
        b.connect(target, "block", sample, "value");
        b.connect(sample, "value", energy, "block");
        b.connect(energy, "percent", low, "left");
        b.connect(threshold, "value", low, "right");
        b.connect(energy, "available", availableAndLow, "left");
        b.connect(low, "result", availableAndLow, "right");
        b.connect(sample, "flow", branch, "flow");
        b.connect(availableAndLow, "result", branch, "condition");
        b.connect(branch, "true", action, "flow");
        b.connect(sample, "value", action, "block");
        b.finishBranches(branch, action, 1860);
    }

    private static void checkedTeleportTask(Builder b, int entry, Kind kind) {
        int caster = b.add(CommonProgramNodeIds.CASTER, 24, 260);
        int position = b.add(CommonProgramNodeIds.ENTITY_POSITION, 280, 230);
        int direction = b.add(CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, 280, 420);
        int distance = b.scalar("float", "3.0", 280, 590);
        int offset = b.add(CommonProgramNodeIds.WORLD_POSITION_OFFSET, 540, 230);
        int sample = b.sample(ProgramValueTypes.WORLD_POSITION, 800, 48);
        int safety = b.add(AcademyCraft.academy("program/teleport/logic/space_safety"), 1060, 240);
        int branch = b.add(CommonProgramNodeIds.BRANCH, 1320, 48);
        int action = b.add(kind.action, 1580, 48);
        b.connect(caster, "entity", position, "entity");
        b.connect(caster, "entity", direction, "entity");
        b.connect(position, "position", offset, "position");
        b.connect(direction, "direction", offset, "direction");
        b.connect(distance, "value", offset, "distance");
        b.connect(offset, "position", sample, "value");
        b.connect(entry, "flow", sample, "flow");
        b.connect(caster, "entity", safety, "entity");
        b.connect(sample, "value", safety, "position");
        b.connect(sample, "flow", branch, "flow");
        b.connect(safety, "result", branch, "condition");
        b.connect(branch, "true", action, "flow");
        b.connect(sample, "value", action, "destination");
        b.finishBranches(branch, action, 1840);
    }

    private static void lockedMiningTask(Builder b, int entry, Kind kind) {
        int target = b.add(CommonProgramNodeIds.LOOK_TARGET, 24, 240,
                config -> config.addProperty("target_type", "block"));
        int center = b.add(CommonProgramNodeIds.BLOCK_TO_CENTER, 280, 240);
        int sample = b.sample(ProgramValueTypes.WORLD_POSITION, 540, 48);
        int caster = b.add(CommonProgramNodeIds.CASTER, 24, 450);
        int position = b.add(CommonProgramNodeIds.ENTITY_POSITION, 280, 450);
        int distance = b.add(CommonProgramNodeIds.WORLD_POSITION_DISTANCE, 800, 240);
        int limit = b.scalar("float", "16.0", 800, 440);
        int inside = b.compare("float", "less_equal", 1060, 240);
        int branch = b.add(CommonProgramNodeIds.BRANCH, 1320, 48);
        int action = b.add(kind.action, 1580, 48, config -> config.addProperty("aim_mode", "target"));
        b.connect(target, "block", center, "block");
        b.connect(center, "position", sample, "value");
        b.connect(entry, "flow", sample, "flow");
        b.connect(caster, "entity", position, "entity");
        b.connect(position, "position", distance, "left");
        b.connect(sample, "value", distance, "right");
        b.connect(distance, "result", inside, "left");
        b.connect(limit, "value", inside, "right");
        b.connect(sample, "flow", branch, "flow");
        b.connect(inside, "result", branch, "condition");
        b.connect(branch, "true", action, "flow");
        b.connect(sample, "value", action, "target_position");
        b.finishBranches(branch, action, 1840);
    }

    private static void filteredBlockTask(Builder b, int entry, Kind kind) {
        int target = b.add(CommonProgramNodeIds.LOOK_TARGET, 24, 250,
                config -> config.addProperty("target_type", "block"));
        int singleton = b.add(CommonProgramNodeIds.collection("block_position", "singleton"), 280, 250);
        int filter = b.add(CommonProgramNodeIds.FILTER_BLOCK_EXACT, 540, 250,
                config -> config.addProperty("selectors", "minecraft:stone"));
        int sample = b.sample(ProgramValueTypes.BLOCK_POSITION_SET, 800, 48);
        int condition = b.nonempty(sample, "block_position", 1060, 300);
        int index = b.scalar("integer", "1", 1060, 600);
        int get = b.add(CommonProgramNodeIds.collection("block_position", "get"), 1320, 400);
        int branch = b.add(CommonProgramNodeIds.BRANCH, 1580, 48);
        int action = b.add(kind.action, 1840, 48);
        b.connect(target, "block", singleton, "value");
        b.connect(singleton, "values", filter, "blocks");
        b.connect(filter, "blocks", sample, "value");
        b.connect(entry, "flow", sample, "flow");
        b.connect(sample, "flow", branch, "flow");
        b.connect(condition, "result", branch, "condition");
        b.connect(sample, "value", get, "values");
        b.connect(index, "value", get, "index");
        b.connect(get, "value", action, "block");
        b.connect(branch, "true", action, "flow");
        b.finishBranches(branch, action, 2100);
    }

    private static void rosterGuardTask(Builder b, int entry, Kind kind) {
        int roster = b.add(PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.ROSTER), 24, 240);
        int alive = b.add(CommonProgramNodeIds.FILTER_ENTITY_ALIVE, 280, 240);
        int supported = b.add(PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.ABILITY_SUPPORTED), 540, 240,
                config -> config.addProperty("parameter", ControlCapability.GUARD_CONTROL.ordinal()));
        int sample = b.sample(ProgramValueTypes.ENTITY_SET, 800, 48);
        int condition = b.nonempty(sample, "entity", 1060, 300);
        int caster = b.add(CommonProgramNodeIds.CASTER, 1320, 480);
        int branch = b.add(CommonProgramNodeIds.BRANCH, 1580, 48);
        int action = b.add(kind.action, 1840, 48, config -> config.addProperty("parameter", 10.0));
        b.connect(roster, "entities", alive, "entities");
        b.connect(alive, "entities", supported, "entities");
        b.connect(supported, "entities", sample, "value");
        b.connect(entry, "flow", sample, "flow");
        b.connect(sample, "flow", branch, "flow");
        b.connect(condition, "result", branch, "condition");
        b.connect(branch, "true", action, "flow");
        b.connect(sample, "value", action, "subjects");
        b.connect(caster, "entity", action, "destination");
        b.finishBranches(branch, action, 2100);
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
            case OWNED_STRUCTURE_SET, NOZZLE_STRUCTURE_SET -> {
                var types = new java.util.ArrayList<>(List.of(kind.action, CommonProgramNodeIds.CASTER,
                        CommonProgramNodeIds.ENTITY_POSITION, CommonProgramNodeIds.SCALAR_CONSTANT,
                        CommonProgramNodeIds.STRUCTURES_AROUND, CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE,
                        CommonProgramNodeIds.SAMPLE_VALUE, CommonProgramNodeIds.collection("structure", "size"),
                        CommonProgramNodeIds.NUMERIC_COMPARE, CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.STOP));
                if (kind.shape == Shape.NOZZLE_STRUCTURE_SET) {
                    types.add(AcademyCraft.academy("program/aeromanip/query/owned_jet_nozzle_count"));
                    types.add(CommonProgramNodeIds.BOOLEAN_AND);
                }
                yield List.copyOf(types);
            }
            case LOW_BATTERY -> List.of(kind.action, CommonProgramNodeIds.LOOK_TARGET, CommonProgramNodeIds.SAMPLE_VALUE,
                    AcademyCraft.academy("program/electromaster/query/energy_level"), CommonProgramNodeIds.SCALAR_CONSTANT,
                    CommonProgramNodeIds.NUMERIC_COMPARE, CommonProgramNodeIds.BOOLEAN_AND,
                    CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.STOP);
            case CHECKED_TELEPORT -> List.of(kind.action, CommonProgramNodeIds.CASTER, CommonProgramNodeIds.ENTITY_POSITION,
                    CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, CommonProgramNodeIds.SCALAR_CONSTANT,
                    CommonProgramNodeIds.WORLD_POSITION_OFFSET, CommonProgramNodeIds.SAMPLE_VALUE,
                    AcademyCraft.academy("program/teleport/logic/space_safety"), CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.STOP);
            case LOCKED_MINING -> List.of(kind.action, CommonProgramNodeIds.LOOK_TARGET, CommonProgramNodeIds.BLOCK_TO_CENTER,
                    CommonProgramNodeIds.CASTER, CommonProgramNodeIds.ENTITY_POSITION, CommonProgramNodeIds.SAMPLE_VALUE,
                    CommonProgramNodeIds.WORLD_POSITION_DISTANCE, CommonProgramNodeIds.SCALAR_CONSTANT,
                    CommonProgramNodeIds.NUMERIC_COMPARE, CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.STOP);
            case FILTERED_BLOCK -> List.of(kind.action, CommonProgramNodeIds.LOOK_TARGET,
                    CommonProgramNodeIds.collection("block_position", "singleton"), CommonProgramNodeIds.FILTER_BLOCK_EXACT,
                    CommonProgramNodeIds.SAMPLE_VALUE, CommonProgramNodeIds.collection("block_position", "size"),
                    CommonProgramNodeIds.collection("block_position", "get"), CommonProgramNodeIds.SCALAR_CONSTANT,
                    CommonProgramNodeIds.NUMERIC_COMPARE, CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.STOP);
            case ROSTER_GUARD -> List.of(kind.action, PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.ROSTER),
                    PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.ABILITY_SUPPORTED), CommonProgramNodeIds.FILTER_ENTITY_ALIVE,
                    CommonProgramNodeIds.SAMPLE_VALUE, CommonProgramNodeIds.collection("entity", "size"),
                    CommonProgramNodeIds.SCALAR_CONSTANT, CommonProgramNodeIds.NUMERIC_COMPARE,
                    CommonProgramNodeIds.CASTER, CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.STOP);
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

        private int scalar(String type, String value, double x, double y) {
            return add(CommonProgramNodeIds.SCALAR_CONSTANT, x, y, config -> {
                config.addProperty("type", type); config.addProperty("value", value);
            });
        }

        private int sample(ProgramValueType type, double x, double y) {
            return add(CommonProgramNodeIds.SAMPLE_VALUE, x, y,
                    config -> config.addProperty("type", type.id().toString()));
        }

        private int compare(String type, String operator, double x, double y) {
            return add(CommonProgramNodeIds.NUMERIC_COMPARE, x, y, config -> {
                config.addProperty("type", type); config.addProperty("operator", operator);
            });
        }

        private int nonempty(int sample, String domain, double x, double y) {
            int size = add(CommonProgramNodeIds.collection(domain, "size"), x, y);
            int zero = scalar("integer", "0", x, y + 160);
            int positive = compare("integer", "greater", x + 250, y);
            connect(sample, "value", size, "values");
            connect(size, "size", positive, "left");
            connect(zero, "value", positive, "right");
            return positive;
        }

        private void finishBranches(int branch, int action, double x) {
            int success = add(CommonProgramNodeIds.STOP, x, 48);
            int rejected = add(CommonProgramNodeIds.STOP, x, 250);
            connect(action, "flow", success, "flow");
            connect(branch, "false", rejected, "flow");
        }
    }
}
