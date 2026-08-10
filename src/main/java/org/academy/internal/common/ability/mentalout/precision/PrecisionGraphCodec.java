package org.academy.internal.common.ability.mentalout.precision;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

public final class PrecisionGraphCodec {
    private static final int VERSION = 4;
    private static final PrecisionGraph.NodeKind[] LEGACY_KINDS = java.util.Arrays.copyOf(
            PrecisionGraph.NodeKind.values(),
            32
    );

    private PrecisionGraphCodec() {
    }

    public static byte[] encode(PrecisionGraph graph) {
        var validation = graph == null
                ? PrecisionGraph.EMPTY.validate()
                : graph.validate();
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.diagnostic().name());
        }
        try {
            var output = new ByteArrayOutputStream();
            try (var data = new DataOutputStream(output)) {
                data.writeByte(VERSION);
                data.writeByte(validation.normalized().nodes().size());
                for (var node : validation.normalized().nodes()) {
                    data.writeInt(node.id());
                    data.writeByte(node.kind().wireId());
                    data.writeDouble(node.parameter());
                    data.writeFloat((float) node.x());
                    data.writeFloat((float) node.y());
                }
                data.writeByte(validation.normalized().edges().size());
                for (var edge : validation.normalized().edges()) {
                    data.writeInt(edge.fromNode());
                    data.writeByte(edge.fromPort());
                    data.writeInt(edge.toNode());
                    data.writeByte(edge.toPort());
                }
            }
            var bytes = output.toByteArray();
            if (bytes.length > PrecisionGraph.MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(PrecisionGraph.Diagnostic.TOO_LARGE.name());
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static DecodeResult decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new DecodeResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.OK);
        }
        if (bytes.length > PrecisionGraph.MAX_ENCODED_BYTES) {
            return new DecodeResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.TOO_LARGE);
        }
        try (var data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var version = data.readUnsignedByte();
            if (version < 1 || version > VERSION) return malformed();
            var nodeCount = data.readUnsignedByte();
            if (nodeCount > PrecisionGraph.MAX_NODES) {
                return new DecodeResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.TOO_MANY_NODES);
            }
            var nodes = new ArrayList<PrecisionGraph.Node>(nodeCount);
            for (var i = 0; i < nodeCount; i++) {
                var id = data.readInt();
                var encodedKind = data.readUnsignedByte();
                var kind = version == 1
                        ? encodedKind < LEGACY_KINDS.length ? LEGACY_KINDS[encodedKind] : null
                        : PrecisionGraph.NodeKind.byWireId(encodedKind);
                if (kind == null) return malformed();
                nodes.add(new PrecisionGraph.Node(
                        id,
                        kind,
                        data.readDouble(),
                        data.readFloat(),
                        data.readFloat()
                ));
            }
            var edgeCount = data.readUnsignedByte();
            if (edgeCount > PrecisionGraph.MAX_EDGES) {
                return new DecodeResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.TOO_MANY_EDGES);
            }
            var edges = new ArrayList<PrecisionGraph.Edge>(edgeCount);
            for (var i = 0; i < edgeCount; i++) {
                edges.add(new PrecisionGraph.Edge(
                        data.readInt(),
                        data.readUnsignedByte(),
                        data.readInt(),
                        data.readUnsignedByte()
                ));
            }
            if (data.available() != 0) return malformed();
            var graph = new PrecisionGraph(nodes, edges);
            if (version == 1) {
                var migration = PrecisionGraph.migrateLegacy(graph);
                return migration.valid()
                        ? new DecodeResult(migration.graph(), PrecisionGraph.Diagnostic.OK)
                        : new DecodeResult(PrecisionGraph.EMPTY, migration.diagnostic());
            }
            var validation = graph.validate();
            return validation.valid()
                    ? new DecodeResult(validation.normalized(), PrecisionGraph.Diagnostic.OK)
                    : new DecodeResult(PrecisionGraph.EMPTY, validation.diagnostic());
        } catch (IOException | RuntimeException exception) {
            return malformed();
        }
    }

    private static DecodeResult malformed() {
        return new DecodeResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.MALFORMED);
    }

    public record DecodeResult(PrecisionGraph graph, PrecisionGraph.Diagnostic diagnostic) {
        public boolean valid() {
            return diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }
}
