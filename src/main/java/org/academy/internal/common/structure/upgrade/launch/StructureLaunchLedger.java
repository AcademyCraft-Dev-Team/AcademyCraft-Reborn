package org.academy.internal.common.structure.upgrade.launch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.upgrade.StructureLaunches;
import org.academy.internal.common.structure.DurableFile;
import org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Bounded write-ahead authorization ledger. Claims/revocations survive restart; no effect replay. */
public final class StructureLaunchLedger {
    private static final int MAX_BYTES = 16_384, MAX_RECORDS = 65_536;
    private final Path directory;
    private final DurableFile.FileOperations files;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private boolean healthy = true;

    public record Entry(StructureLaunches.Grant grant, boolean revoked, Optional<UUID> effect) {
        public Entry { Objects.requireNonNull(grant); Objects.requireNonNull(effect); }
    }
    public StructureLaunchLedger(Path directory) throws IOException { this(directory, DurableFile.FileOperations.DEFAULT); }
    StructureLaunchLedger(Path directory, DurableFile.FileOperations files) throws IOException {
        this.directory = directory.toAbsolutePath().normalize(); this.files = files;
        Files.createDirectories(this.directory);
        try (var paths = Files.list(this.directory)) {
            var iterator = paths.iterator(); int count = 0;
            while (iterator.hasNext()) {
                var path = iterator.next();
                if (!path.getFileName().toString().endsWith(".dat")) continue;
                if (++count > MAX_RECORDS) { healthy = false; break; }
                try {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) throw new IOException("Unbounded launch record");
                    var entry = decode(NbtIo.readCompressed(path, NbtAccounter.create(MAX_BYTES)));
                    if (!path.equals(file(entry.grant().id())) || entries.putIfAbsent(entry.grant().id(), entry) != null) throw new IOException("Launch identity conflict");
                } catch (IOException | RuntimeException invalid) { healthy = false; }
            }
        }
    }
    public boolean healthy() { return healthy; }
    public Optional<Entry> find(UUID id) { return healthy ? Optional.ofNullable(entries.get(id)) : Optional.empty(); }

    public void issue(StructureLaunches.Grant grant) throws IOException {
        requireHealthy();
        var entry = new Entry(grant, false, Optional.empty());
        var previous = entries.get(grant.id());
        if (previous != null) {
            if (!previous.equals(entry)) throw new IOException("Launch identity already used");
            return;
        }
        if (entries.size() >= MAX_RECORDS) throw new IOException("Launch ledger full");
        write(entry);
    }
    /** Only the first claim succeeds, including repeated calls with the same effect id. */
    public boolean claim(StructureLaunches.Grant grant, UUID effect, long now) throws IOException {
        requireHealthy();
        var current = entries.get(grant.id());
        if (current == null || !current.grant().equals(grant) || current.revoked() || current.effect().isPresent()
                || now < grant.createdTick() || now >= grant.expiresTick()) return false;
        write(new Entry(grant, false, Optional.of(effect))); return true;
    }
    public boolean revoke(UUID id) throws IOException {
        requireHealthy(); var current = entries.get(id);
        if (current == null) return false;
        if (!current.revoked()) write(new Entry(current.grant(), true, current.effect()));
        return true;
    }
    private void write(Entry entry) throws IOException {
        try {
            var root = encode(entry); var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) { NbtIo.writeUnnamedTag(root, out); }
            var data = bytes.toByteArray();
            if (data.length > MAX_BYTES || !entry.equals(decode(NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MAX_BYTES))))) throw new IOException("Invalid launch encoding");
            DurableFile.writeAtomically(file(entry.grant().id()), data, files);
            entries.put(entry.grant().id(), entry);
        } catch (IOException | RuntimeException failure) {
            // A failed atomic publish can be ambiguous. No further authorization in this process.
            healthy = false; throw failure;
        }
    }
    private void requireHealthy() throws IOException { if (!healthy) throw new IOException("Launch ledger unavailable"); }
    private Path file(UUID id) { return directory.resolve(id + ".dat"); }
    private static CompoundTag encode(Entry entry) {
        var grant = entry.grant(); var tag = new CompoundTag();
        tag.putInt("version", 1); tag.putString("id", grant.id().toString());
        tag.putString("dimension", grant.dimension().toString()); tag.putString("actor", grant.actorId().toString());
        tag.putString("actor_type", grant.actorType().toString()); grant.ownerId().ifPresent(id -> tag.putString("owner", id.toString()));
        tag.putString("policy", grant.policyId().toString()); tag.putInt("policy_version", grant.policyVersion());
        tag.putLong("created", grant.createdTick()); tag.putLong("expires", grant.expiresTick());
        grant.source().ifPresent(source -> {
            tag.put("source", StructureProvenanceCodec.encodeReference(source.reference()));
            tag.putString("creation", source.creationId().toString());
        });
        tag.putBoolean("revoked", entry.revoked()); entry.effect().ifPresent(id -> tag.putString("effect", id.toString()));
        return tag;
    }
    private static Entry decode(CompoundTag tag) {
        if (tag.getInt("version").orElse(-1) != 1) throw new IllegalArgumentException("Unknown launch version");
        var source = tag.contains("source") ? Optional.of(new StructureLaunches.Source(
                StructureProvenanceCodec.decodeReference(tag.getCompound("source").orElseThrow()), uuid(tag,"creation"))) : Optional.<StructureLaunches.Source>empty();
        var grant = new StructureLaunches.Grant(uuid(tag,"id"), id(tag,"dimension"), uuid(tag,"actor"), id(tag,"actor_type"),
                optionalUuid(tag,"owner"), id(tag,"policy"), tag.getInt("policy_version").orElseThrow(),
                tag.getLong("created").orElseThrow(), tag.getLong("expires").orElseThrow(), source);
        return new Entry(grant, tag.getBoolean("revoked").orElseThrow(), optionalUuid(tag,"effect"));
    }
    private static UUID uuid(CompoundTag tag, String key) { return UUID.fromString(tag.getString(key).orElseThrow()); }
    private static Identifier id(CompoundTag tag, String key) { return Identifier.parse(tag.getString(key).orElseThrow()); }
    private static Optional<UUID> optionalUuid(CompoundTag tag, String key) { return tag.contains(key) ? Optional.of(uuid(tag,key)) : Optional.empty(); }
}
