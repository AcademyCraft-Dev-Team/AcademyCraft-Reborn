package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * Stable resource identifiers used when importing the original Precision Operation format.
 */
public final class PrecisionProgramNodeIds {
    public static final Identifier ON_CAST = AcademyCraft.academy("program/entry/on_cast");

    private static final String[] PATHS = {
            "target/caster",
            "mentalout/roster",
            "mentalout/intrusion_target",
            "target/look_living",
            "target/nearby_living",
            "filter/alive",
            "filter/distance",
            "filter/allies",
            "filter/enemies",
            "mentalout/filter/control_supported",
            "collection/exclude",
            "collection/nearest",
            "collection/limit",
            "mentalout/action/target_misidentification",
            "mentalout/action/mental_stupor",
            "mentalout/action/impression_manipulation",
            "mentalout/action/perception_mask",
            "mentalout/action/start_intrusion",
            "mentalout/action/end_intrusion",
            "filter/targeted_by",
            "filter/hostile_to",
            "filter/last_damaged_by",
            "target/player_target",
            "collection/sort_by_distance",
            "collection/random",
            "filter/entity_type",
            "filter/health",
            "filter/has_target",
            "mentalout/filter/affected",
            "mentalout/action/path_to",
            "mentalout/action/view_control",
            "mentalout/action/remove_control",
            "target/current_target",
            "target/last_attacker",
            "collection/entity_to_set",
            "collection/union",
            "collection/intersection",
            "collection/subtract",
            "collection/farthest",
            "collection/lowest_health",
            "collection/highest_health",
            "filter/health_below",
            "filter/visible_from",
            "target/sight_position",
            "mentalout/action/guard_mode",
            "target/nearby_all_entities",
            "target/nearby_items",
            "target/nearby_projectiles",
            "target/entity_position",
            "target/direction_between",
            "target/position_offset",
            "flow/health_ratio_branch",
            "flow/distance_branch",
            "flow/entity_type_branch",
            "flow/status_effect_branch"
    };
    private static final Map<Identifier, PrecisionGraph.NodeKind> KINDS_BY_ID = kindsById();

    static {
        if (PATHS.length != PrecisionGraph.NodeKind.values().length) {
            throw new IllegalStateException("Precision node id table is incomplete");
        }
    }

    private PrecisionProgramNodeIds() {
    }

    public static Identifier id(PrecisionGraph.NodeKind kind) {
        return AcademyCraft.academy("program/" + PATHS[kind.wireId()]);
    }

    public static PrecisionGraph.NodeKind kind(Identifier id) {
        return KINDS_BY_ID.get(id);
    }

    private static Map<Identifier, PrecisionGraph.NodeKind> kindsById() {
        var result = new HashMap<Identifier, PrecisionGraph.NodeKind>();
        for (var kind : PrecisionGraph.NodeKind.values()) result.put(id(kind), kind);
        return Map.copyOf(result);
    }
}
