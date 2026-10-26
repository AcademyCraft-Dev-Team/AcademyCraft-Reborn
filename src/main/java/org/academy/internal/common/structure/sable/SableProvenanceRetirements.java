package org.academy.internal.common.structure.sable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.structure.DurableFile;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec;
import org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceLedger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/** Durable permanent-removal intents. Unload notifications never enter this journal. */
public final class SableProvenanceRetirements {
    private static final int MAX_BYTES = 65_536;
    private static final int MAX_RECORDS = 65_536;
    private final Path directory;
    private final DurableFile.FileOperations files;
    private final DeleteOperation delete;
    private final Map<StructureRef, Entry> entries = new ConcurrentHashMap<>();
    private final java.util.List<Problem> problems = new java.util.ArrayList<>();
    private int problemCount;
    private boolean healthy = true;

    public record Problem(String file, String reason) {}
    @FunctionalInterface interface DeleteOperation { void delete(Path path) throws IOException; }

    public record Entry(StructureRef reference, StructureProvenance.Metadata metadata, CompoundTag evidence) {
        public Entry {
            reference = StructureProvenanceLedger.stable(reference);
            if (!reference.backend().equals(StructureBackendRouter.SABLE_ID)) throw new IllegalArgumentException("Not Sable");
            java.util.Objects.requireNonNull(metadata);
            evidence = evidence.copy();
            evidence.getInt("plot_x").orElseThrow();
            evidence.getInt("plot_z").orElseThrow();
            if (evidence.getBoolean("saved").orElseThrow()) {
                evidence.getInt("chunk_x").orElseThrow();
                evidence.getInt("chunk_z").orElseThrow();
                int storage = evidence.getInt("storage").orElseThrow();
                int slot = evidence.getInt("slot").orElseThrow();
                if (storage < 0 || storage > Short.MAX_VALUE || slot < 0 || slot > Short.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid saved pointer");
                }
            }
        }

        @Override public CompoundTag evidence() { return evidence.copy(); }
    }

    public SableProvenanceRetirements(Path directory) throws IOException {
        this(directory, DurableFile.FileOperations.DEFAULT);
    }

    SableProvenanceRetirements(Path directory, DurableFile.FileOperations files) throws IOException {
        this(directory, files, Files::deleteIfExists);
    }

    SableProvenanceRetirements(Path directory, DurableFile.FileOperations files, DeleteOperation delete) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.files = files;
        this.delete = delete;
        Files.createDirectories(this.directory);
        try (var paths = Files.list(this.directory)) {
            var iterator = paths.iterator();
            int count = 0;
            while (iterator.hasNext()) {
                var path = iterator.next();
                if (!path.getFileName().toString().endsWith(".dat")) continue;
                if (++count > MAX_RECORDS) { problem(path, "record_limit"); break; }
                String reason = "record_unreadable";
                try {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
                        reason = "not_bounded_regular_file";
                        throw new IOException("Unbounded retirement image");
                    }
                    reason = "record_invalid";
                    var entry = decode(NbtIo.readCompressed(path, NbtAccounter.create(MAX_BYTES)));
                    reason = "identity_mismatch";
                    if (!file(entry.reference()).equals(path) || entries.putIfAbsent(entry.reference(), entry) != null) {
                        throw new IOException("Retirement identity mismatch");
                    }
                } catch (IOException | RuntimeException failure) { problem(path, reason); }
            }
        }
    }

    public boolean healthy() { return healthy; }
    public int size() { return entries.size(); }
    public int problemCount() { return problemCount; }
    public java.util.List<Problem> problems() { return java.util.List.copyOf(problems); }
    public java.util.Optional<Entry> entry(StructureRef reference) {
        return java.util.Optional.ofNullable(entries.get(StructureProvenanceLedger.stable(reference)));
    }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public boolean contains(StructureRef reference) { return entries.containsKey(StructureProvenanceLedger.stable(reference)); }

    public void prepare(Entry entry) throws IOException {
        requireHealthy();
        var existing = entries.get(entry.reference());
        if (existing != null) {
            if (!existing.equals(entry)) throw new IOException("Retirement ownership changed");
            return;
        }
        if (entries.size() >= MAX_RECORDS) throw new IOException("Retirement capacity exhausted");
        var root = new CompoundTag();
        root.putInt("version", 1);
        root.put("reference", StructureProvenanceCodec.encodeReference(entry.reference()));
        root.put("metadata", StructureProvenanceCodec.encode(entry.metadata()));
        root.put("evidence", entry.evidence());
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(bytes))) { NbtIo.writeUnnamedTag(root, output); }
        var data = bytes.toByteArray();
        if (data.length > MAX_BYTES || !entry.equals(decode(NbtIo.readCompressed(
                new ByteArrayInputStream(data), NbtAccounter.create(MAX_BYTES))))) throw new IOException("Invalid retirement encoding");
        DurableFile.writeAtomically(file(entry.reference()), data, files);
        entries.put(entry.reference(), entry);
    }

    public void complete(Entry entry) throws IOException {
        requireHealthy();
        if (!entry.equals(entries.get(entry.reference()))) throw new IOException("Retirement ownership changed");
        delete.delete(file(entry.reference()));
        entries.remove(entry.reference(), entry);
    }

    private void problem(Path path, String reason) {
        healthy = false;
        problemCount++;
        if (problems.size() < 8) problems.add(new Problem(path.getFileName().toString(), reason));
    }

    private Path file(StructureRef reference) {
        return directory.resolve(UUID.nameUUIDFromBytes((reference.dimension() + "\n" + reference.id())
                .getBytes(StandardCharsets.UTF_8)) + ".dat");
    }

    private static Entry decode(CompoundTag root) {
        if (root.getInt("version").orElse(-1) != 1) throw new IllegalArgumentException("Unknown retirement version");
        return new Entry(StructureProvenanceCodec.decodeReference(root.getCompound("reference").orElseThrow()),
                StructureProvenanceCodec.decode(root.getCompound("metadata").orElseThrow()),
                root.getCompound("evidence").orElseThrow());
    }

    private void requireHealthy() throws IOException {
        if (!healthy) throw new IOException("Unknown or corrupt Sable retirement records");
    }
}
