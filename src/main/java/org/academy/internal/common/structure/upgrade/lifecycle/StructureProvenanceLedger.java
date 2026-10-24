package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.structure.DurableFile;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/** One forced, bounded record per marked body; corrupt/unknown files block writes and automation. */
public final class StructureProvenanceLedger {
    public static final String DIRECTORY_NAME = "structure_provenance";
    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_RECORDS = 65_536;
    private final Path directory;
    private final DurableFile.FileOperations files;
    // Weakly consistent iteration lets a bounded scheduler finish its scan across ticks while
    // other captures/restorations add or retire marks. Mutation still belongs to one server thread.
    private final Map<StructureRef, Entry> entries = new ConcurrentHashMap<>();
    private final Collection<Entry> view = Collections.unmodifiableCollection(entries.values());
    private boolean healthy = true;

    public record Entry(StructureRef reference, StructureProvenance.Metadata metadata) {
        public Entry {
            reference = stable(Objects.requireNonNull(reference, "reference"));
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    public StructureProvenanceLedger(Path directory) throws IOException {
        this(directory, DurableFile.FileOperations.DEFAULT);
    }

    StructureProvenanceLedger(Path directory, DurableFile.FileOperations files) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.files = Objects.requireNonNull(files, "files");
        Files.createDirectories(this.directory);
        try (var stream = Files.list(this.directory)) {
            var iterator = stream.iterator();
            int count = 0;
            while (iterator.hasNext()) {
                var path = iterator.next();
                if (!path.getFileName().toString().endsWith(".dat")) continue;
                if (++count > MAX_RECORDS) { healthy = false; break; }
                try {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
                        throw new IOException("Provenance record is not a bounded regular file");
                    }
                    var root = NbtIo.readCompressed(path, NbtAccounter.create(MAX_BYTES));
                    var entry = decode(root);
                    if (!path.equals(file(entry.reference())) || entries.putIfAbsent(entry.reference(), entry) != null) {
                        throw new IOException("Provenance identity mismatch");
                    }
                } catch (IOException | RuntimeException invalid) {
                    healthy = false;
                }
            }
        }
    }

    public boolean healthy() { return healthy; }

    /** O(1), weakly consistent view; a bounded scan need not restart when another mark changes. */
    public Collection<Entry> entries() { return healthy ? view : java.util.List.of(); }

    public Optional<StructureProvenance.Metadata> metadata(StructureRef reference) {
        if (!healthy) return Optional.empty();
        var entry = entries.get(stable(reference));
        return entry == null ? Optional.empty() : Optional.of(entry.metadata());
    }

    /** Repeating the exact capture commit is idempotent; ownership cannot be reassigned. */
    public void commit(StructureRef reference, StructureProvenance.Metadata metadata) throws IOException {
        requireHealthy();
        var entry = new Entry(reference, metadata);
        var existing = entries.get(entry.reference());
        if (existing != null) {
            if (!existing.equals(entry)) throw new IOException("Refusing to overwrite structure creation metadata");
            return;
        }
        if (entries.size() >= MAX_RECORDS) throw new IOException("Provenance record capacity exhausted");
        var root = encode(entry);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(bytes))) {
            NbtIo.writeUnnamedTag(root, output);
        }
        var data = bytes.toByteArray();
        if (data.length > MAX_BYTES || !root.equals(NbtIo.readCompressed(
                new ByteArrayInputStream(data), NbtAccounter.create(MAX_BYTES)))) {
            throw new IOException("Invalid provenance encoding");
        }
        DurableFile.writeAtomically(file(entry.reference()), data, files);
        entries.put(entry.reference(), entry);
    }

    /** Only the exact committed creation may retire its mark after content transfer is durable. */
    public boolean remove(StructureRef reference, UUID creationOperationId) throws IOException {
        requireHealthy();
        var stable = stable(reference);
        var existing = entries.get(stable);
        if (existing == null) return true;
        if (!existing.metadata().creationOperationId().equals(creationOperationId)) return false;
        Files.deleteIfExists(file(stable));
        entries.remove(stable);
        return true;
    }

    public static StructureRef stable(StructureRef reference) {
        return new StructureRef(reference.id(), reference.dimension(), reference.backend(), 0);
    }

    private Path file(StructureRef reference) {
        var key = reference.backend() + "\n" + reference.dimension() + "\n" + reference.id();
        return directory.resolve(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)) + ".dat");
    }

    private static CompoundTag encode(Entry entry) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.put("reference", StructureProvenanceCodec.encodeReference(entry.reference()));
        tag.put("metadata", StructureProvenanceCodec.encode(entry.metadata()));
        return tag;
    }

    private static Entry decode(CompoundTag root) {
        if (root.getInt("version").orElse(-1) != 1) throw new IllegalArgumentException("Unknown provenance ledger version");
        return new Entry(StructureProvenanceCodec.decodeReference(root.getCompound("reference").orElseThrow()),
                StructureProvenanceCodec.decode(root.getCompound("metadata").orElseThrow()));
    }

    private void requireHealthy() throws IOException {
        if (!healthy) throw new IOException("Provenance ledger contains unavailable or unknown records");
    }
}
