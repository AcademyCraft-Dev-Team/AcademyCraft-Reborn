package org.academy.internal.client.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.internal.LazilyParsedNumber;
import org.academy.api.common.ability.program.ProgramGraph;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/** JsonPrimitive.deepCopy retains its Number; a Java caller can supply a mutable Number. */
public final class ProgramEditorCachePolicy {
    private static final Set<Class<?>> NUMBERS = Set.of(Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, BigInteger.class, BigDecimal.class, LazilyParsedNumber.class);

    private ProgramEditorCachePolicy() {}

    public static boolean stableGraph(ProgramGraph graph) {
        return graph.nodes().stream().allMatch(node -> stableJson(node.configuration(), 0));
    }

    private static boolean stableJson(JsonElement value, int depth) {
        if (depth > 32) return false;
        if (value.isJsonNull()) return true;
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            return !primitive.isNumber() || NUMBERS.contains(primitive.getAsNumber().getClass());
        }
        if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) if (!stableJson(child, depth + 1)) return false;
            return true;
        }
        if (value.isJsonObject()) {
            for (var child : value.getAsJsonObject().entrySet())
                if (!stableJson(child.getValue(), depth + 1)) return false;
            return true;
        }
        return false;
    }
}
