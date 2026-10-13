package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Bounded write-ahead journal for structure transactions. One durable file per transaction is
 * persisted before the first world write of its stage and retained until the outcome is
 * confirmed. Cell payloads are opaque; the journal validates identity, stage order and geometry
 * but never interprets block content. Corrupt files are isolated and retained, never guessed.
 */
public final class StructureTransactionJournal {
    public static final String DIRECTORY_NAME = "structure_transactions";
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    public static final int MAX_CELLS = StructureCaptureRequest.MAXIMUM_BLOCKS;
    private static final int VERSION = 1;
    private static final String SUFFIX = ".dat";

    /** Journal stages are ordered; a record can only advance, never skip backwards. */
    public enum Stage {
        PREPARED(1),
        TARGET_ALLOCATED(2),
        COPIED(3),
        COMMIT_DECIDED(4),
        PUBLISHED(5);

        private final int value;

        Stage(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Stage fromValue(int value) {
            for (var stage : values()) {
                if (stage.value == value) return stage;
            }
            throw new IllegalArgumentException("Unknown structure transaction stage: " + value);
        }
    }

    /** A source cell identity plus the executor-owned payload required to restore or verify it. */
    public record Cell(BlockPos position, CompoundTag payload) {
        public Cell {
            position = Objects.requireNonNull(position, "position").immutable();
            payload = Objects.requireNonNull(payload, "payload").copy();
        }

        @Override
        public CompoundTag payload() {
            return payload.copy();
        }
    }

    /** Immutable journal record; stage, target identity and failure evidence only move forward. */
    public record Record(
            UUID transactionId,
            Identifier backend,
            Identifier hostDimension,
            BlockPos origin,
            Stage stage,
            @Nullable UUID targetStructure,
            List<Cell> cells,
            @Nullable CompoundTag failureEvidence
    ) {
        public Record {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(backend, "backend");
            Objects.requireNonNull(hostDimension, "hostDimension");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(cells, "cells");
            if (cells.isEmpty() || cells.size() > MAX_CELLS) {
                throw new IllegalArgumentException("A structure transaction journal requires 1 to "
                        + MAX_CELLS + " cells");
            }
            if (stage == Stage.PREPARED && targetStructure != null) {
                throw new IllegalArgumentException("A prepared transaction has no target identity yet");
            }
            if (stage != Stage.PREPARED && targetStructure == null) {
                throw new IllegalArgumentException("An allocated transaction requires its target identity");
            }
            var positions = new LinkedHashSet<BlockPos>();
            for (var cell : cells) {
                Objects.requireNonNull(cell, "cell");
                if (!positions.add(cell.position())) {
                    throw new IllegalArgumentException("Duplicate journal cell " + cell.position());
                }
            }
            cells = List.copyOf(cells);
            failureEvidence = failureEvidence == null ? null : failureEvidence.copy();
        }

        public Record withStage(Stage replacement) {
            Objects.requireNonNull(replacement, "replacement");
            if (replacement.compareTo(stage) < 0) {
                throw new IllegalArgumentException("Journal stages cannot move backwards");
            }
            return new Record(transactionId, backend, hostDimension, origin, replacement,
                    targetStructure, cells, failureEvidence);
        }

        /** Attaching the reserved target identity advances a prepared record to allocation. */
        public Record withTarget(UUID replacement) {
            Objects.requireNonNull(replacement, "replacement");
            var nextStage = stage == Stage.PREPARED ? Stage.TARGET_ALLOCATED : stage;
            return new Record(transactionId, backend, hostDimension, origin, nextStage,
                    replacement, cells, failureEvidence);
        }

        public Record withEvidence(CompoundTag evidence) {
            return new Record(transactionId, backend, hostDimension, origin, stage,
                    targetStructure, cells, Objects.requireNonNull(evidence, "evidence"));
        }
    }

    private final Path directory;
    private final Map<UUID, Record> records = new LinkedHashMap<>();
    private final List<String> corrupt = new ArrayList<>();
    private final boolean healthy;

    public StructureTransactionJournal(Path directory) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        healthy = scan();
    }

    /**
     * Re-reads the directory. A journal instance is the only writer during normal operation, but
     * recovery must observe records persisted by another session or a fixture before deciding.
     */
    private boolean scan() {
        records.clear();
        corrupt.clear();
        try (var files = Files.list(directory)) {
            for (var file : files.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(SUFFIX)) continue;
                try {
                    var decoded = decodeFile(file);
                    var previous = records.putIfAbsent(decoded.transactionId(), decoded);
                    if (previous != null) {
                        // Duplicate identities are ambiguous; isolate both records instead of guessing.
                        records.remove(decoded.transactionId());
                        corrupt.add(file.getFileName().toString());
                    }
                } catch (IOException | RuntimeException malformed) {
                    // Damaged evidence is isolated and retained; its direction is never guessed.
                    corrupt.add(file.getFileName().toString());
                }
            }
            return true;
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    public boolean healthy() {
        return healthy;
    }

    public List<Record> records() {
        if (healthy) scan();
        return List.copyOf(records.values());
    }

    public Optional<Record> find(UUID transactionId) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(transactionId, "transactionId")));
    }

    public boolean exists(UUID transactionId) {
        return records.containsKey(Objects.requireNonNull(transactionId, "transactionId"));
    }

    /** File names of retained, unreadable records; their direction is never inferred. */
    public List<String> corruptFiles() {
        return List.copyOf(corrupt);
    }

    /** Persists the complete record with an atomic, forced and round-trip verified replacement. */
    public void persist(Record record) throws IOException {
        Objects.requireNonNull(record, "record");
        if (!healthy) throw new IOException("Structure transaction journal is unavailable");
        var existing = records.get(record.transactionId());
        if (existing != null && record.stage().compareTo(existing.stage()) < 0) {
            throw new IOException("Refusing to move a persisted transaction stage backwards");
        }
        writeAtomically(file(record.transactionId()), encode(record));
        records.put(record.transactionId(), record);
    }

    /** Removes a record only after its outcome was confirmed on both sides. */
    public void delete(UUID transactionId) throws IOException {
        Objects.requireNonNull(transactionId, "transactionId");
        if (!healthy) throw new IOException("Structure transaction journal is unavailable");
        Files.deleteIfExists(file(transactionId));
        records.remove(transactionId);
    }

    private Path file(UUID transactionId) {
        return directory.resolve(transactionId + SUFFIX);
    }

    private Record decodeFile(Path file) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > MAX_PAYLOAD_BYTES) {
            throw new IOException("Structure transaction record is not a bounded regular file");
        }
        var root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_PAYLOAD_BYTES));
        var record = decode(root);
        var expected = record.transactionId() + SUFFIX;
        if (!file.getFileName().toString().equals(expected)) {
            throw new IOException("Structure transaction record name does not match its identity");
        }
        return record;
    }

    private static CompoundTag encode(Record record) {
        var root = new CompoundTag();
        root.putInt("version", VERSION);
        root.putString("transaction_id", record.transactionId().toString());
        root.putString("backend", record.backend().toString());
        root.putString("host_dimension", record.hostDimension().toString());
        root.putInt("stage", record.stage().value());
        var origin = new CompoundTag();
        origin.putInt("x", record.origin().getX());
        origin.putInt("y", record.origin().getY());
        origin.putInt("z", record.origin().getZ());
        root.put("origin", origin);
        if (record.targetStructure() != null) {
            root.putString("target_structure", record.targetStructure().toString());
        }
        var cells = new ListTag();
        for (var cell : record.cells()) {
            var child = new CompoundTag();
            child.putInt("x", cell.position().getX());
            child.putInt("y", cell.position().getY());
            child.putInt("z", cell.position().getZ());
            child.put("payload", cell.payload());
            cells.add(child);
        }
        root.put("cells", cells);
        if (record.failureEvidence() != null) {
            root.put("failure_evidence", record.failureEvidence());
        }
        return root;
    }

    private static Record decode(CompoundTag root) throws IOException {
        requireType(root, "version", Tag.TAG_INT);
        if (root.getInt("version").orElseThrow() != VERSION) {
            throw new IOException("Unknown structure transaction journal version");
        }
        for (var key : List.of("transaction_id", "backend", "host_dimension")) {
            requireType(root, key, Tag.TAG_STRING);
        }
        requireType(root, "stage", Tag.TAG_INT);
        requireType(root, "origin", Tag.TAG_COMPOUND);
        requireType(root, "cells", Tag.TAG_LIST);
        var originTag = root.getCompound("origin").orElseThrow();
        for (var key : List.of("x", "y", "z")) requireType(originTag, key, Tag.TAG_INT);
        var cellsTag = root.getList("cells").orElseThrow();
        if (cellsTag.size() < 1 || cellsTag.size() > MAX_CELLS) {
            throw new IOException("Invalid structure transaction cell count");
        }
        var cells = new ArrayList<Cell>(cellsTag.size());
        for (var value : cellsTag) {
            if (!(value instanceof CompoundTag child)) {
                throw new IOException("Expected a structure transaction cell");
            }
            for (var key : List.of("x", "y", "z")) requireType(child, key, Tag.TAG_INT);
            requireType(child, "payload", Tag.TAG_COMPOUND);
            cells.add(new Cell(new BlockPos(child.getInt("x").orElseThrow(),
                    child.getInt("y").orElseThrow(), child.getInt("z").orElseThrow()),
                    child.getCompound("payload").orElseThrow()));
        }
        UUID target = null;
        if (root.get("target_structure") != null) {
            requireType(root, "target_structure", Tag.TAG_STRING);
            target = UUID.fromString(root.getString("target_structure").orElseThrow());
        }
        CompoundTag evidence = null;
        if (root.get("failure_evidence") != null) {
            requireType(root, "failure_evidence", Tag.TAG_COMPOUND);
            evidence = root.getCompound("failure_evidence").orElseThrow();
        }
        try {
            return new Record(UUID.fromString(root.getString("transaction_id").orElseThrow()),
                    Identifier.parse(root.getString("backend").orElseThrow()),
                    Identifier.parse(root.getString("host_dimension").orElseThrow()),
                    new BlockPos(originTag.getInt("x").orElseThrow(), originTag.getInt("y").orElseThrow(),
                            originTag.getInt("z").orElseThrow()),
                    Stage.fromValue(root.getInt("stage").orElseThrow()), target, cells, evidence);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IOException("Invalid structure transaction identity", invalid);
        }
    }

    private static void requireType(CompoundTag tag, String key, byte type) throws IOException {
        var value = tag.get(key);
        if (value == null || value.getId() != type) {
            throw new IOException("Missing or invalid structure transaction field: " + key);
        }
    }

    private static void writeAtomically(Path target, CompoundTag payload) throws IOException {
        var size = payload.sizeInBytes();
        if (size < 0 || size > MAX_PAYLOAD_BYTES) {
            throw new IOException("Structure transaction payload is too large");
        }
        var encoded = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(encoded))) {
            // writeCompressed uses vanilla's oversized-string fallback, which can erase data.
            NbtIo.writeUnnamedTag(payload, output);
        }
        var data = encoded.toByteArray();
        if (data.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Structure transaction payload is too large");
        }
        try {
            var decoded = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MAX_PAYLOAD_BYTES));
            if (!payload.equals(decoded)) {
                throw new IOException("Structure transaction payload did not round-trip without changes");
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Structure transaction payload exceeds decoding limits", invalid);
        }
        var temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = ByteBuffer.wrap(data);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
