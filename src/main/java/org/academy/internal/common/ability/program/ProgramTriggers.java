package org.academy.internal.common.ability.program;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Classifies program entry nodes and matches them against server-side trigger events. */
public final class ProgramTriggers {
    public static final int DEFAULT_LOOP_INTERVAL = 40;
    private static final Map<HealthLatchKey, Boolean> HEALTH_LATCHES = new HashMap<>();

    private ProgramTriggers() {
    }

    public static boolean acceptsManualExecution(CompiledProgram program) {
        var entry = program.nodes().get(program.entryNodeId());
        if (entry == null) return false;
        var type = type(entry.typeId());
        return type == null || type == Type.LOOP || type == Type.MOVEMENT;
    }

    public static boolean matches(
            AbilityProgram program,
            Type expected,
            CommonProgramNodeCatalog.MovementCondition movement,
            long gameTime
    ) {
        var entry = triggerEntry(program);
        if (entry == null || type(entry.type()) != expected) return false;
        var configuration = entry.configuration();
        return switch (expected) {
            case LOOP -> {
                var decoded = CommonProgramNodeCatalog.LoopTriggerConfiguration.CODEC
                        .parse(JsonOps.INSTANCE, configuration)
                        .result()
                        .orElse(null);
                yield decoded != null && decoded.enabled()
                        && Math.floorMod(gameTime, Math.max(1, decoded.interval())) == 0;
            }
            case MOVEMENT -> {
                if (movement == null || !configuration.isJsonObject()
                        || !configuration.getAsJsonObject().has("condition")) yield false;
                try {
                    yield CommonProgramNodeCatalog.MovementCondition.byName(
                            configuration.getAsJsonObject().get("condition").getAsString()
                    ) == movement;
                } catch (RuntimeException exception) {
                    yield false;
                }
            }
            case MELEE, HURT -> true;
            case HEALTH -> false;
        };
    }

    public static float costMultiplier(AbilityProgram program) {
        var entry = triggerEntry(program);
        if (entry == null || type(entry.type()) != Type.LOOP) return 1.0f;
        var decoded = CommonProgramNodeCatalog.LoopTriggerConfiguration.CODEC
                .parse(JsonOps.INSTANCE, entry.configuration())
                .result()
                .orElse(null);
        return decoded == null ? 1.0f : loopCostMultiplier(decoded.interval());
    }

    public static float loopCostMultiplier(int interval) {
        var checked = Math.max(1, interval);
        return checked >= DEFAULT_LOOP_INTERVAL
                ? 1.0f : Math.max(2.0f, 10.0f / checked);
    }

    public static boolean matchesHealth(
            AbilityProgram program,
            ServerPlayer player,
            Identifier category,
            int slot
    ) {
        var entry = triggerEntry(program);
        if (entry == null || type(entry.type()) != Type.HEALTH) return false;
        var decoded = CommonProgramNodeCatalog.HealthThresholdTriggerConfiguration.CODEC
                .parse(JsonOps.INSTANCE, entry.configuration())
                .result()
                .orElse(null);
        if (decoded == null) return false;
        var key = new HealthLatchKey(player.getUUID(), category, slot);
        var matches = switch (decoded.mode()) {
            case ABOVE -> player.getHealth() > decoded.threshold();
            case BELOW -> player.getHealth() < decoded.threshold();
        };
        var latched = HEALTH_LATCHES.getOrDefault(key, false);
        if (!matches) {
            HEALTH_LATCHES.remove(key);
            return false;
        }
        if (latched) return false;
        HEALTH_LATCHES.put(key, true);
        return true;
    }

    public static void clear(UUID playerId) {
        HEALTH_LATCHES.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    public static void clear() {
        HEALTH_LATCHES.clear();
    }

    private static ProgramGraph.Node triggerEntry(AbilityProgram program) {
        for (var node : program.graph().nodes()) {
            if (type(node.type()) != null) return node;
        }
        return null;
    }

    private static Type type(net.minecraft.resources.Identifier id) {
        if (id.equals(CommonProgramNodeIds.TRIGGER_HURT)) return Type.HURT;
        if (id.equals(CommonProgramNodeIds.TRIGGER_LOOP)) return Type.LOOP;
        if (id.equals(CommonProgramNodeIds.TRIGGER_MELEE)) return Type.MELEE;
        if (id.equals(CommonProgramNodeIds.TRIGGER_MOVEMENT)) return Type.MOVEMENT;
        if (id.equals(CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD)) return Type.HEALTH;
        return null;
    }

    public enum Type {
        MELEE,
        LOOP,
        MOVEMENT,
        HURT,
        HEALTH
    }

    private record HealthLatchKey(UUID playerId, Identifier category, int slot) {
    }
}
