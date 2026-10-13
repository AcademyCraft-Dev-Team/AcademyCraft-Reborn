package org.academy.internal.common.structure.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsAttachment;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsRegionLayout;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Bounded native-space index and permanent slot ownership markers. Invalid metadata disables
 * writes rather than replacing the index with an empty save. These files do not form a
 * transaction with vanilla chunk files and do not provide crash-atomic world transfers.
 */
public final class NativeStructureManifest {
    public static final int SLOT_COUNT = 4;
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private static final int VERSION = 3;
    private static final Identifier BACKEND = Identifier.fromNamespaceAndPath("academy", "native_level");

    public record Entry(StructureRef reference, int slot, StructurePose pose,
                        boolean active, boolean quarantined, @Nullable NativeStructurePhysicsAttachment physicsAttachment) {
        public Entry(StructureRef reference, int slot, StructurePose pose, boolean active, boolean quarantined) {
            this(reference, slot, pose, active, quarantined, null);
        }

        public Entry {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(pose, "pose");
            validateSlot(slot);
            if (!BACKEND.equals(reference.backend())) {
                throw new IllegalArgumentException("Unexpected native structure backend");
            }
            if (isStorageDimension(reference.dimension())) {
                throw new IllegalArgumentException("A storage dimension cannot be the visible host");
            }
        }

        public Entry withPose(StructurePose replacement) {
            return new Entry(reference, slot, replacement, active, quarantined, physicsAttachment);
        }

        public Entry withAvailability(boolean active, boolean quarantined) {
            return new Entry(reference, slot, pose, active, quarantined, physicsAttachment);
        }

        public Entry withPhysicsAttachment(@Nullable NativeStructurePhysicsAttachment replacement) {
            return new Entry(reference, slot, pose, active, quarantined, replacement);
        }
    }

    private final Path directory;
    private final Path metadata;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean healthy = true;

    public NativeStructureManifest(Path directory) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        metadata = this.directory.resolve("metadata.dat");
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(metadata, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            // Permanent owner markers may exist without an index after an interrupted allocation.
            return;
        } catch (IOException unavailable) {
            healthy = false;
            return;
        }
        try {
            if (!attributes.isRegularFile() || attributes.size() > MAX_PAYLOAD_BYTES) {
                throw new IOException("Native structure metadata is not a bounded regular file");
            }
            var decoded = decode(NbtIo.readCompressed(metadata, NbtAccounter.create(MAX_PAYLOAD_BYTES)));
            entries.putAll(decoded);
        } catch (IOException | RuntimeException malformed) {
            healthy = false;
            entries.clear();
        }
    }

    /** Read-only installation preflight: even a missing directory stays missing. */
    public static Preflight inspect(Path directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            var attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()) return new Preflight(0, true, false);
        } catch (NoSuchFileException absent) {
            return new Preflight(0, false, true);
        } catch (IOException | RuntimeException unavailable) {
            return new Preflight(0, true, false);
        }
        try {
            boolean claims = false;
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                claims |= present(directory.resolve("owner_" + slot + ".txt"));
                claims |= present(directory.resolve("recovery_" + slot + ".dat"));
            }
            var metadata = directory.resolve("metadata.dat");
            if (!present(metadata)) return new Preflight(0, claims, true);
            var file = Files.readAttributes(metadata, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!file.isRegularFile() || file.size() > MAX_PAYLOAD_BYTES) return new Preflight(0, claims, false);
            var entries = decode(NbtIo.readCompressed(metadata, NbtAccounter.create(MAX_PAYLOAD_BYTES)));
            return new Preflight(entries.size(), claims, true);
        } catch (IOException | RuntimeException malformed) {
            return new Preflight(0, true, false);
        }
    }

    private static boolean present(Path path) throws IOException {
        try { Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); return true; }
        catch (NoSuchFileException absent) { return false; }
    }

    public record Preflight(int structures, boolean claimsOrRecovery, boolean healthy) {
        public boolean requiresMigration() { return structures != 0 || claimsOrRecovery || !healthy; }
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public Optional<Entry> get(UUID id) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(id, "id")));
    }

    /** Updates an existing identity or adds one previously unindexed identity. */
    public void put(Entry entry) {
        putAll(List.of(Objects.requireNonNull(entry, "entry")));
    }

    /** Validates the complete candidate index before publishing any member of a checkpoint. */
    public void putAll(List<Entry> replacements) {
        if (!healthy) throw new IllegalStateException("Native structure metadata requires recovery");
        Objects.requireNonNull(replacements, "replacements");
        var candidate = new LinkedHashMap<>(entries);
        var replacedIds = new HashSet<UUID>();
        for (var entry : replacements) {
            Objects.requireNonNull(entry, "entry");
            if (!replacedIds.add(entry.reference().id())) throw new IllegalArgumentException("Duplicate checkpoint replacement");
            var prior = entries.get(entry.reference().id());
            if (prior != null && (!prior.reference().equals(entry.reference()) || prior.slot() != entry.slot())) {
                throw new IllegalArgumentException("An existing structure cannot change its identity or slot");
            }
            candidate.put(entry.reference().id(), entry);
        }
        validateEntries(candidate.values());
        entries.clear();
        entries.putAll(candidate);
    }

    public boolean healthy() {
        return healthy;
    }

    public void persist() throws IOException {
        requireHealthy();
        validateEntries(entries.values());
        writeAtomically(metadata, encodeIndex(entries.values()));
    }

    private void persist(Map<UUID, Entry> candidate) throws IOException {
        requireHealthy();
        validateEntries(candidate.values());
        writeAtomically(metadata, encodeIndex(candidate.values()));
        entries.clear();
        entries.putAll(candidate);
    }

    private static CompoundTag encodeIndex(Collection<Entry> values) {
        var root = new CompoundTag();
        root.putInt("version", VERSION);
        var list = new ListTag();
        for (var entry : values) list.add(encode(entry));
        root.put("entries", list);
        return root;
    }

    /**
     * Removes an identity from the index only when no recovery evidence references its slot.
     * The candidate index is validated and written before the in-memory index publishes it.
     * Deleting the permanent owner marker is a separate, later step ({@link #releaseSlot}).
     */
    public void removeEntry(UUID id) throws IOException {
        requireHealthy();
        Objects.requireNonNull(id, "id");
        var entry = entries.get(id);
        if (entry == null) return;
        if (hasRecovery(entry.slot())) {
            throw new IOException("Recovery evidence blocks removing this structure identity");
        }
        var candidate = new LinkedHashMap<>(entries);
        candidate.remove(id);
        persist(candidate);
    }

    /**
     * Deletes a slot's permanent owner marker and any residual recovery file. Callers must have
     * removed every referencing index entry first; the marker delete happens last so an
     * interrupted release can only leak a claim, never resurrect deleted content.
     */
    public void releaseSlot(int slot, UUID owner) throws IOException {
        requireHealthy();
        validateSlot(slot);
        Objects.requireNonNull(owner, "owner");
        if (entries.values().stream().anyMatch(entry -> entry.slot() == slot)) {
            throw new IOException("An indexed structure still references this slot");
        }
        if (hasRecovery(slot)) throw new IOException("Recovery evidence blocks slot release");
        if (!ownsSlot(slot, owner)) throw new IOException("Slot ownership mismatch");
        Files.deleteIfExists(marker(slot));
    }

    /** Claims once; neither unloading nor losing the index makes a slot reusable. */
    public void claimSlot(int slot, UUID owner) throws IOException {
        requireHealthy();
        validateSlot(slot);
        Objects.requireNonNull(owner, "owner");
        var marker = marker(slot);
        try (var channel = FileChannel.open(marker, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var bytes = ByteBuffer.wrap(owner.toString().getBytes(StandardCharsets.US_ASCII));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        } catch (FileAlreadyExistsException occupied) {
            if (!ownsSlot(slot, owner)) throw new IOException("Native structure slot is already claimed", occupied);
        }
    }

    /** Unreadable, malformed, or non-regular markers never establish ownership. */
    public boolean ownsSlot(int slot, UUID owner) {
        validateSlot(slot);
        Objects.requireNonNull(owner, "owner");
        if (!healthy) return false;
        try {
            var path = marker(slot);
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() != 36) return false;
            return owner.toString().equals(Files.readString(path, StandardCharsets.US_ASCII));
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    /** Any marker or failure to inspect it reserves the slot, even without valid metadata. */
    public boolean slotClaimed(int slot) {
        validateSlot(slot);
        if (!healthy) return true;
        try {
            Files.readAttributes(marker(slot), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException absent) {
            return false;
        } catch (IOException | RuntimeException unavailable) {
            return true;
        }
    }

    /** Recovery evidence independently blocks activation even if the index save failed. */
    public boolean hasRecovery(int slot) {
        validateSlot(slot);
        if (!healthy) return true;
        try {
            Files.readAttributes(directory.resolve("recovery_" + slot + ".dat"),
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException absent) {
            return false;
        } catch (IOException | RuntimeException unavailable) {
            return true;
        }
    }

    /** Saves recovery evidence only; this method never applies or automatically replays it. */
    public void persistRecovery(int slot, CompoundTag recovery) throws IOException {
        requireHealthy();
        validateSlot(slot);
        writeAtomically(directory.resolve("recovery_" + slot + ".dat"),
                Objects.requireNonNull(recovery, "recovery"));
    }

    private Path marker(int slot) {
        return directory.resolve("owner_" + slot + ".txt");
    }

    private void requireHealthy() throws IOException {
        if (!healthy) throw new IOException("Native structure metadata requires recovery");
    }

    private static void validateSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IllegalArgumentException("Invalid native structure slot");
    }

    private static boolean isStorageDimension(Identifier dimension) {
        if (!dimension.getNamespace().equals("academy")) return false;
        for (var slot = 0; slot < SLOT_COUNT; slot++) {
            if (dimension.getPath().equals("structure_space_" + slot)) return true;
        }
        return false;
    }

    private static CompoundTag encode(Entry entry) {
        var tag = new CompoundTag();
        tag.putString("id", entry.reference().id().toString());
        tag.putString("dimension", entry.reference().dimension().toString());
        tag.putString("backend", entry.reference().backend().toString());
        tag.putLong("generation", entry.reference().generation());
        tag.putInt("slot", entry.slot());
        tag.putBoolean("active", entry.active());
        tag.putBoolean("quarantined", entry.quarantined());
        var pose = new CompoundTag();
        pose.putDouble("x", entry.pose().worldOrigin().x);
        pose.putDouble("y", entry.pose().worldOrigin().y);
        pose.putDouble("z", entry.pose().worldOrigin().z);
        pose.putDouble("rotation_x", entry.pose().rotationX());
        pose.putDouble("rotation_y", entry.pose().rotationY());
        pose.putDouble("rotation_z", entry.pose().rotationZ());
        pose.putDouble("rotation_w", entry.pose().rotationW());
        tag.put("pose", pose);
        if (entry.physicsAttachment() != null) {
            tag.put("physics", NativeStructurePhysicsAttachmentCodec.encode(entry.physicsAttachment()));
        }
        return tag;
    }

    private static Map<UUID, Entry> decode(CompoundTag root) throws IOException {
        requireType(root, "version", Tag.TAG_INT);
        int version = root.getInt("version").orElseThrow();
        if (version != 1 && version != 2 && version != VERSION) throw new IOException("Unknown manifest version");
        requireType(root, "entries", Tag.TAG_LIST);
        var values = root.getList("entries").orElseThrow();
        if (values.size() > SLOT_COUNT) throw new IOException("Too many native structures");
        var decoded = new LinkedHashMap<UUID, Entry>();
        var usedSlots = new boolean[SLOT_COUNT];
        var legacyRegions = new LinkedHashMap<Identifier, AABB>();
        for (var value : values) {
            if (!(value instanceof CompoundTag tag)) throw new IOException("Expected a structure entry");
            for (var key : List.of("id", "dimension", "backend")) requireType(tag, key, Tag.TAG_STRING);
            requireType(tag, "generation", Tag.TAG_LONG);
            requireType(tag, "slot", Tag.TAG_INT);
            requireType(tag, "pose", Tag.TAG_COMPOUND);
            var reference = new StructureRef(UUID.fromString(tag.getString("id").orElseThrow()),
                    Identifier.parse(tag.getString("dimension").orElseThrow()),
                    Identifier.parse(tag.getString("backend").orElseThrow()),
                    tag.getLong("generation").orElseThrow());
            NativeStructurePhysicsAttachment attachment = null;
            if (tag.get("physics") != null) {
                if (version == 1) throw new IOException("Legacy manifests cannot contain physics checkpoints");
                requireType(tag, "physics", Tag.TAG_COMPOUND);
                var physics = tag.getCompound("physics").orElseThrow();
                attachment = version == 2 ? NativeStructurePhysicsAttachmentCodec.decodeLegacyV2(physics, reference.dimension())
                        : NativeStructurePhysicsAttachmentCodec.decode(physics);
                if (version == 2) {
                    var prior = legacyRegions.putIfAbsent(reference.dimension(), attachment.bounds());
                    if (prior != null && !prior.equals(attachment.bounds())) {
                        throw new IOException("Legacy host contains conflicting physics region bounds");
                    }
                }
            }
            var pose = tag.getCompound("pose").orElseThrow();
            for (var key : List.of("x", "y", "z", "rotation_x", "rotation_y", "rotation_z", "rotation_w")) {
                requireType(pose, key, Tag.TAG_DOUBLE);
            }
            var entry = new Entry(reference, tag.getInt("slot").orElseThrow(), new StructurePose(
                    new Vec3(pose.getDouble("x").orElseThrow(), pose.getDouble("y").orElseThrow(),
                            pose.getDouble("z").orElseThrow()),
                    pose.getDouble("rotation_x").orElseThrow(), pose.getDouble("rotation_y").orElseThrow(),
                    pose.getDouble("rotation_z").orElseThrow(), pose.getDouble("rotation_w").orElseThrow()),
                    readFlag(tag, "active"), readFlag(tag, "quarantined"), attachment);
            if (decoded.putIfAbsent(reference.id(), entry) != null || usedSlots[entry.slot()]) {
                throw new IOException("Duplicate native structure identity or slot");
            }
            usedSlots[entry.slot()] = true;
        }
        validateEntries(decoded.values());
        return decoded;
    }

    private static void validateEntries(Iterable<Entry> candidates) {
        var usedSlots = new HashSet<Integer>();
        var usedIds = new HashSet<UUID>();
        var regions = new ArrayList<NativeStructurePhysicsRegionLayout.Region>();
        for (var entry : candidates) {
            if (!usedSlots.add(entry.slot()) || !usedIds.add(entry.reference().id())) {
                throw new IllegalArgumentException("Duplicate native structure identity or slot");
            }
            var physics = entry.physicsAttachment();
            if (physics == null) continue;
            regions.add(new NativeStructurePhysicsRegionLayout.Region(physics.regionId(), entry.reference().dimension(), physics.bounds()));
        }
        NativeStructurePhysicsRegionLayout.validate(regions);
    }

    private static boolean readFlag(CompoundTag tag, String key) throws IOException {
        requireType(tag, key, Tag.TAG_BYTE);
        var value = tag.getByte(key).orElseThrow();
        if (value != 0 && value != 1) throw new IOException("Invalid manifest flag: " + key);
        return value == 1;
    }

    private static void requireType(CompoundTag tag, String key, byte type) throws IOException {
        var value = tag.get(key);
        if (value == null || value.getId() != type) throw new IOException("Missing or invalid manifest field: " + key);
    }

    private static void writeAtomically(Path target, CompoundTag payload) throws IOException {
        var size = payload.sizeInBytes();
        if (size < 0 || size > MAX_PAYLOAD_BYTES) throw new IOException("Native structure payload is too large");
        var encoded = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(encoded))) {
            // writeCompressed uses vanilla's oversized-string fallback, which can erase data.
            NbtIo.writeUnnamedTag(payload, output);
        }
        var data = encoded.toByteArray();
        if (data.length > MAX_PAYLOAD_BYTES) throw new IOException("Native structure payload is too large");
        try {
            // The same bounded decoder must be able to reopen every file that we commit.
            var decoded = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MAX_PAYLOAD_BYTES));
            if (!payload.equals(decoded)) {
                throw new IOException("Native structure payload did not round-trip without changes");
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Native structure payload exceeds decoding limits", invalid);
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
