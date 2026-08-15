package org.academy.internal.common.ability.program;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramLimits;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Stable bounded binary representation used by skill data and the program editor protocol. */
public final class ProgramBookCodec {
    public static final int MAX_PROGRAM_ENCODED_BYTES = 65_536;
    public static final int MAX_BOOK_ENCODED_BYTES = 1_048_576;
    private static final int VERSION = 1;
    private static final int MAX_SLOTS = 16;
    private static final int MAX_NAME_BYTES = 256;
    private static final int MAX_IDENTIFIER_BYTES = 256;
    private static final int MAX_PORT_BYTES = 128;
    private static final int MAX_CONFIGURATION_BYTES = 8_192;

    private ProgramBookCodec() {
    }

    public static byte[] encode(ProgramBook book) {
        if (book == null) throw new IllegalArgumentException("Program book cannot be null");
        try {
            var output = new ByteArrayOutputStream();
            try (var data = new DataOutputStream(output)) {
                data.writeByte(VERSION);
                data.writeInt(book.schemaVersion());
                data.writeLong(book.revision());
                data.writeByte(book.selectedSlot());
                requireCount(book.slots().size(), 1, MAX_SLOTS, "slot");
                data.writeByte(book.slots().size());
                for (var slot : book.slots()) {
                    var program = slot.program();
                    data.writeBoolean(program != null);
                    if (program != null) writeProgram(data, program);
                }
            }
            var encoded = output.toByteArray();
            if (encoded.length > MAX_BOOK_ENCODED_BYTES) {
                throw new IllegalArgumentException("Program book exceeds 1 MiB");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static byte[] encodeProgram(@Nullable AbilityProgram program) {
        var encoded = encode(new ProgramBook(
                ProgramBook.CURRENT_SCHEMA_VERSION,
                0,
                0,
                List.of(new ProgramBook.Slot(program))
        ));
        if (encoded.length > MAX_PROGRAM_ENCODED_BYTES) {
            throw new IllegalArgumentException("Ability program exceeds 64 KiB");
        }
        return encoded;
    }

    public static DecodeResult decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) return malformed();
        if (encoded.length > MAX_BOOK_ENCODED_BYTES) {
            return new DecodeResult(null, Diagnostic.TOO_LARGE);
        }
        try (var data = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (data.readUnsignedByte() != VERSION) {
                return new DecodeResult(null, Diagnostic.UNSUPPORTED_VERSION);
            }
            var schemaVersion = data.readInt();
            var revision = data.readLong();
            var selectedSlot = data.readUnsignedByte();
            var slotCount = data.readUnsignedByte();
            requireCount(slotCount, 1, MAX_SLOTS, "slot");
            var slots = new ArrayList<ProgramBook.Slot>(slotCount);
            for (var slot = 0; slot < slotCount; slot++) {
                slots.add(new ProgramBook.Slot(data.readBoolean() ? readProgram(data) : null));
            }
            if (data.available() != 0) return malformed();
            return new DecodeResult(
                    new ProgramBook(schemaVersion, revision, selectedSlot, slots),
                    Diagnostic.OK
            );
        } catch (IOException | RuntimeException exception) {
            return malformed();
        }
    }

    public static ProgramDecodeResult decodeProgram(byte[] encoded) {
        if (encoded != null && encoded.length > MAX_PROGRAM_ENCODED_BYTES) {
            return new ProgramDecodeResult(null, Diagnostic.TOO_LARGE);
        }
        var decoded = decode(encoded);
        if (!decoded.valid()) {
            return new ProgramDecodeResult(null, decoded.diagnostic);
        }
        if (decoded.book.slots().size() != 1) {
            return new ProgramDecodeResult(null, Diagnostic.MALFORMED);
        }
        return new ProgramDecodeResult(decoded.book.slot(0).program(), Diagnostic.OK);
    }

    private static void writeProgram(DataOutputStream data, AbilityProgram program) throws IOException {
        data.writeInt(program.schemaVersion());
        data.writeLong(program.id().getMostSignificantBits());
        data.writeLong(program.id().getLeastSignificantBits());
        writeString(data, program.name(), MAX_NAME_BYTES);
        writeString(data, program.category().toString(), MAX_IDENTIFIER_BYTES);
        writeGraph(data, program.graph());
        writeLayout(data, program.editorLayout());
    }

    private static AbilityProgram readProgram(DataInputStream data) throws IOException {
        var schemaVersion = data.readInt();
        var id = new UUID(data.readLong(), data.readLong());
        var name = readString(data, MAX_NAME_BYTES);
        var category = parseIdentifier(readString(data, MAX_IDENTIFIER_BYTES));
        return new AbilityProgram(
                schemaVersion,
                id,
                name,
                category,
                readGraph(data),
                readLayout(data)
        );
    }

    private static void writeGraph(DataOutputStream data, ProgramGraph graph) throws IOException {
        requireCount(graph.nodes().size(), 0, ProgramLimits.DEFAULT.maxNodes(), "node");
        requireCount(graph.edges().size(), 0, ProgramLimits.DEFAULT.maxEdges(), "edge");
        data.writeShort(graph.nodes().size());
        for (var node : graph.nodes()) {
            data.writeInt(node.id());
            writeString(data, node.type().toString(), MAX_IDENTIFIER_BYTES);
            data.writeInt(node.schemaVersion());
            writeString(data, node.configuration().toString(), MAX_CONFIGURATION_BYTES);
        }
        data.writeShort(graph.edges().size());
        for (var edge : graph.edges()) {
            writeEndpoint(data, edge.from());
            writeEndpoint(data, edge.to());
        }
    }

    private static ProgramGraph readGraph(DataInputStream data) throws IOException {
        var nodeCount = data.readUnsignedShort();
        requireCount(nodeCount, 0, ProgramLimits.DEFAULT.maxNodes(), "node");
        var nodes = new ArrayList<ProgramGraph.Node>(nodeCount);
        for (var index = 0; index < nodeCount; index++) {
            nodes.add(new ProgramGraph.Node(
                    data.readInt(),
                    parseIdentifier(readString(data, MAX_IDENTIFIER_BYTES)),
                    data.readInt(),
                    JsonParser.parseString(readString(data, MAX_CONFIGURATION_BYTES))
            ));
        }
        var edgeCount = data.readUnsignedShort();
        requireCount(edgeCount, 0, ProgramLimits.DEFAULT.maxEdges(), "edge");
        var edges = new ArrayList<ProgramGraph.Edge>(edgeCount);
        for (var index = 0; index < edgeCount; index++) {
            edges.add(new ProgramGraph.Edge(readEndpoint(data), readEndpoint(data)));
        }
        return new ProgramGraph(nodes, edges);
    }

    private static void writeLayout(
            DataOutputStream data,
            ProgramEditorLayout layout
    ) throws IOException {
        requireCount(layout.nodePositions().size(), 0, ProgramLimits.DEFAULT.maxNodes(), "layout");
        var positions = layout.nodePositions().entrySet().stream()
                .sorted(Comparator.comparingInt(java.util.Map.Entry::getKey))
                .toList();
        data.writeShort(positions.size());
        for (var entry : positions) {
            data.writeInt(entry.getKey());
            data.writeDouble(entry.getValue().x());
            data.writeDouble(entry.getValue().y());
        }
    }

    private static ProgramEditorLayout readLayout(DataInputStream data) throws IOException {
        var count = data.readUnsignedShort();
        requireCount(count, 0, ProgramLimits.DEFAULT.maxNodes(), "layout");
        var positions = new HashMap<Integer, ProgramEditorLayout.NodePosition>();
        for (var index = 0; index < count; index++) {
            if (positions.putIfAbsent(
                    data.readInt(),
                    new ProgramEditorLayout.NodePosition(data.readDouble(), data.readDouble())
            ) != null) throw new IllegalArgumentException("Duplicate layout node");
        }
        return new ProgramEditorLayout(positions);
    }

    private static void writeEndpoint(
            DataOutputStream data,
            ProgramGraph.Endpoint endpoint
    ) throws IOException {
        data.writeInt(endpoint.nodeId());
        writeString(data, endpoint.port(), MAX_PORT_BYTES);
    }

    private static ProgramGraph.Endpoint readEndpoint(DataInputStream data) throws IOException {
        return new ProgramGraph.Endpoint(data.readInt(), readString(data, MAX_PORT_BYTES));
    }

    private static void writeString(DataOutputStream data, String value, int maximum) throws IOException {
        var encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximum) throw new IllegalArgumentException("Program string is too long");
        data.writeInt(encoded.length);
        data.write(encoded);
    }

    private static String readString(DataInputStream data, int maximum) throws IOException {
        var length = data.readInt();
        if (length < 0 || length > maximum || length > data.available()) {
            throw new IllegalArgumentException("Invalid program string length");
        }
        return new String(data.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static Identifier parseIdentifier(String value) {
        var identifier = Identifier.tryParse(value);
        if (identifier == null) throw new IllegalArgumentException("Invalid program identifier");
        return identifier;
    }

    private static void requireCount(int count, int minimum, int maximum, String type) {
        if (count < minimum || count > maximum) {
            throw new IllegalArgumentException("Invalid program " + type + " count");
        }
    }

    private static DecodeResult malformed() {
        return new DecodeResult(null, Diagnostic.MALFORMED);
    }

    public enum Diagnostic {
        OK,
        TOO_LARGE,
        MALFORMED,
        UNSUPPORTED_VERSION
    }

    public record DecodeResult(@Nullable ProgramBook book, Diagnostic diagnostic) {
        public boolean valid() {
            return diagnostic == Diagnostic.OK && book != null;
        }
    }

    public record ProgramDecodeResult(
            @Nullable AbilityProgram program,
            Diagnostic diagnostic
    ) {
        public boolean valid() {
            return diagnostic == Diagnostic.OK;
        }
    }
}
