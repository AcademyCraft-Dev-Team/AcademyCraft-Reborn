package org.academy.internal.common.sublevel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.DurableFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

/** Atomic Phase 1 manifest for plot-backed SubLevels; block content remains in plot chunks. */
public final class SubLevelManifest {
    public static final int VERSION = 1;
    public static final int MAX_ENTRIES = 4_096;
    public static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;

    private final Path file;
    private final Map<SubLevelId, Entry> entries = new LinkedHashMap<>();

    public SubLevelManifest(Path directory) throws IOException {
        var root = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Files.createDirectories(root);
        this.file = root.resolve("metadata.dat");
        if (!Files.exists(file)) return;
        var attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        if (!attributes.isRegularFile() || attributes.size() > MAX_PAYLOAD_BYTES) {
            throw new IOException("SubLevel manifest is not a bounded regular file");
        }
        entries.putAll(decode(NbtIo.readCompressed(file, NbtAccounter.create(MAX_PAYLOAD_BYTES))));
    }

    public Optional<Entry> get(SubLevelId id) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(id, "id")));
    }

    public void put(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!entries.containsKey(entry.id()) && entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("SubLevel manifest capacity reached");
        }
        entries.put(entry.id(), entry);
    }

    public boolean remove(SubLevelId id) {
        return entries.remove(Objects.requireNonNull(id, "id")) != null;
    }

    public int size() {
        return entries.size();
    }

    public Collection<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public void persist() throws IOException {
        var encoded = encode(entries.values());
        var bytes = writeCompressed(encoded);
        DurableFile.writeAtomically(file, bytes, DurableFile.FileOperations.DEFAULT);
    }

    public Path file() {
        return file;
    }

    /** The original path remains authoritative for existing overworld saves. */
    public static Path directoryForHost(Path root, Identifier host) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(host, "host");
        if (host.equals(Identifier.fromNamespaceAndPath("minecraft", "overworld"))) return root;
        // Encode the full identifier: resource paths may contain slashes or dot segments.
        return root.resolve("hosts").resolve(HexFormat.of().formatHex(
                host.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** Loads absent hosts too, so their persisted plots cannot be reused by another dimension. */
    public static Map<Identifier, SubLevelManifest> loadHosts(Path root, Collection<Identifier> liveHosts) throws IOException {
        var manifests = new LinkedHashMap<Identifier, SubLevelManifest>();
        var overworld = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        manifests.put(overworld, new SubLevelManifest(root));
        var hostsDirectory = root.resolve("hosts");
        if (Files.exists(hostsDirectory)) {
            try (var children = Files.list(hostsDirectory)) {
                for (var child : children.toList()) {
                    if (Files.isSymbolicLink(child) || !Files.isDirectory(child)) {
                        throw new IOException("Invalid SubLevel host manifest directory: " + child);
                    }
                    Identifier host;
                    try {
                        host = Identifier.parse(new String(HexFormat.of().parseHex(child.getFileName().toString()),
                                java.nio.charset.StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException invalid) {
                        throw new IOException("Invalid SubLevel host identifier: " + child, invalid);
                    }
                    if (!directoryForHost(root, host).equals(child) || manifests.containsKey(host)) {
                        throw new IOException("Duplicate or noncanonical SubLevel host: " + host);
                    }
                    manifests.put(host, new SubLevelManifest(child));
                }
            }
        }
        for (var host : liveHosts) {
            if (!manifests.containsKey(host)) manifests.put(host, new SubLevelManifest(directoryForHost(root, host)));
        }
        validateOwnership(manifests.values());
        return Map.copyOf(manifests);
    }

    static void validateOwnership(Collection<SubLevelManifest> manifests) throws IOException {
        var plots = new java.util.HashSet<PlotPos>();
        var identities = new java.util.HashSet<SubLevelId>();
        for (var manifest : manifests) for (var entry : manifest.entries()) {
            if (entry.state() == SubLevelState.REMOVED) continue;
            if (!plots.add(entry.plot())) throw new IOException("SubLevel plot is owned by multiple manifests: " + entry.plot());
            if (!identities.add(entry.id())) throw new IOException("SubLevel identity is owned by multiple hosts: " + entry.id());
        }
    }

    public static CompoundTag encode(Collection<Entry> values) {
        var root = new CompoundTag();
        root.putInt("version", VERSION);
        var list = new ListTag();
        for (var entry : values) {
            var tag = new CompoundTag();
            tag.putString("id", entry.id().value().toString());
            tag.putInt("plot_x", entry.plot().x());
            tag.putInt("plot_z", entry.plot().z());
            var bounds = entry.localBounds();
            tag.putDouble("min_x", bounds.minX);
            tag.putDouble("min_y", bounds.minY);
            tag.putDouble("min_z", bounds.minZ);
            tag.putDouble("max_x", bounds.maxX);
            tag.putDouble("max_y", bounds.maxY);
            tag.putDouble("max_z", bounds.maxZ);
            var pose = entry.pose();
            tag.putDouble("x", pose.worldOrigin().x);
            tag.putDouble("y", pose.worldOrigin().y);
            tag.putDouble("z", pose.worldOrigin().z);
            tag.putDouble("rotation_x", pose.rotationX());
            tag.putDouble("rotation_y", pose.rotationY());
            tag.putDouble("rotation_z", pose.rotationZ());
            tag.putDouble("rotation_w", pose.rotationW());
            tag.putString("state", entry.state().name());
            tag.putLong("revision", entry.revision());
            list.add(tag);
        }
        root.put("entries", list);
        return root;
    }

    public static Map<SubLevelId, Entry> decode(CompoundTag root) throws IOException {
        requireType(root, "version", Tag.TAG_INT);
        if (root.getInt("version").orElseThrow() != VERSION) throw new IOException("Unknown SubLevel manifest version");
        requireType(root, "entries", Tag.TAG_LIST);
        var list = root.getList("entries").orElseThrow();
        if (list.size() > MAX_ENTRIES) throw new IOException("Too many SubLevel entries");
        var decoded = new LinkedHashMap<SubLevelId, Entry>();
        for (var value : list) {
            if (!(value instanceof CompoundTag tag)) throw new IOException("Expected a SubLevel entry");
            for (var key : java.util.List.of("min_x", "min_y", "min_z", "max_x", "max_y", "max_z",
                    "x", "y", "z", "rotation_x", "rotation_y", "rotation_z", "rotation_w")) {
                requireType(tag, key, Tag.TAG_DOUBLE);
            }
            for (var key : java.util.List.of("plot_x", "plot_z")) requireType(tag, key, Tag.TAG_INT);
            requireType(tag, "id", Tag.TAG_STRING);
            requireType(tag, "state", Tag.TAG_STRING);
            requireType(tag, "revision", Tag.TAG_LONG);
            SubLevelId id;
            try { id = new SubLevelId(java.util.UUID.fromString(tag.getString("id").orElseThrow())); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid SubLevel UUID", invalid); }
            var plot = new PlotPos(tag.getInt("plot_x").orElseThrow(), tag.getInt("plot_z").orElseThrow());
            var bounds = new AABB(tag.getDouble("min_x").orElseThrow(), tag.getDouble("min_y").orElseThrow(),
                    tag.getDouble("min_z").orElseThrow(), tag.getDouble("max_x").orElseThrow(),
                    tag.getDouble("max_y").orElseThrow(), tag.getDouble("max_z").orElseThrow());
            var pose = new StructurePose(new Vec3(tag.getDouble("x").orElseThrow(),
                    tag.getDouble("y").orElseThrow(), tag.getDouble("z").orElseThrow()),
                    tag.getDouble("rotation_x").orElseThrow(), tag.getDouble("rotation_y").orElseThrow(),
                    tag.getDouble("rotation_z").orElseThrow(), tag.getDouble("rotation_w").orElseThrow());
            SubLevelState state;
            try { state = SubLevelState.valueOf(tag.getString("state").orElseThrow()); }
            catch (IllegalArgumentException invalid) { throw new IOException("Unknown SubLevel state", invalid); }
            var entry = new Entry(id, plot, bounds, pose, state, tag.getLong("revision").orElseThrow());
            if (decoded.putIfAbsent(id, entry) != null) throw new IOException("Duplicate SubLevel identity");
        }
        return Map.copyOf(decoded);
    }

    private static byte[] writeCompressed(CompoundTag payload) throws IOException {
        var encoded = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(encoded))) {
            NbtIo.writeUnnamedTag(payload, output);
        }
        var data = encoded.toByteArray();
        if (data.length > MAX_PAYLOAD_BYTES) throw new IOException("SubLevel manifest is too large");
        try {
            var decoded = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MAX_PAYLOAD_BYTES));
            if (!payload.equals(decoded)) throw new IOException("SubLevel manifest did not round-trip");
        } catch (RuntimeException invalid) {
            throw new IOException("SubLevel manifest exceeds decoding limits", invalid);
        }
        return data;
    }

    private static void requireType(CompoundTag tag, String key, byte type) throws IOException {
        var value = tag.get(key);
        if (value == null || value.getId() != type) throw new IOException("Missing or invalid SubLevel field: " + key);
    }

    public record Entry(SubLevelId id, PlotPos plot, AABB localBounds, StructurePose pose,
                        SubLevelState state, long revision) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(localBounds, "localBounds");
            Objects.requireNonNull(pose, "pose");
            Objects.requireNonNull(state, "state");
            if (revision < 0) throw new IllegalArgumentException("SubLevel revision cannot be negative");
        }
    }
}
