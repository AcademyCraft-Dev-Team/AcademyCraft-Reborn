package org.academy.api.common.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Semantic graph saved for one player-authored ability program.
 * Editor positions and selection state are deliberately stored separately.
 */
public record ProgramGraph(List<Node> nodes, List<Edge> edges) {
    public static final ProgramGraph EMPTY = new ProgramGraph(List.of(), List.of());

    public ProgramGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public record Node(
            int id,
            Identifier type,
            int schemaVersion,
            JsonElement configuration
    ) {
        public Node {
            Objects.requireNonNull(type, "type");
            configuration = configuration == null ? JsonNull.INSTANCE : configuration.deepCopy();
        }

        @Override
        public JsonElement configuration() {
            return configuration.deepCopy();
        }
    }

    public record Endpoint(int nodeId, String port) {
        public Endpoint {
            Objects.requireNonNull(port, "port");
            if (port.isBlank()) throw new IllegalArgumentException("Program endpoint port cannot be blank");
        }
    }

    public record Edge(Endpoint from, Endpoint to) {
        public Edge {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }
}
