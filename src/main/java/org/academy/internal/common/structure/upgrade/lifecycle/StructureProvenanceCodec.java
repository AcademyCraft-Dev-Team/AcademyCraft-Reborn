package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/** Strict, versioned creation image shared by the write-ahead capture journal and final ledger. */
public final class StructureProvenanceCodec {
    private StructureProvenanceCodec() {}

    public static CompoundTag encode(StructureProvenance.Metadata metadata) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putString("source", metadata.source().name());
        tag.putString("operation", metadata.creationOperationId().toString());
        tag.putString("lineage", metadata.rootLineageId().toString());
        metadata.parent().ifPresent(parent -> {
            var parentTag = encodeReference(parent);
            parentTag.putLong("generation", parent.generation());
            tag.put("parent", parentTag);
        });
        metadata.ownerId().ifPresent(owner -> tag.putString("owner", owner.toString()));
        tag.putString("skill", metadata.skillId().toString());
        tag.putLong("created", metadata.createdGameTime());
        var rotation = new CompoundTag();
        rotation.putDouble("x", metadata.sourceGridRotation().x());
        rotation.putDouble("y", metadata.sourceGridRotation().y());
        rotation.putDouble("z", metadata.sourceGridRotation().z());
        rotation.putDouble("w", metadata.sourceGridRotation().w());
        tag.put("rotation", rotation);
        var policy = new CompoundTag();
        policy.putString("id", metadata.restorePolicy().id().toString());
        policy.putInt("version", metadata.restorePolicy().version());
        var parameters = new CompoundTag();
        metadata.restorePolicy().parameters().forEach(parameters::putDouble);
        policy.put("parameters", parameters);
        tag.put("policy", policy);
        return tag;
    }

    public static StructureProvenance.Metadata decode(CompoundTag tag) {
        if (integer(tag, "version") != 1) throw new IllegalArgumentException("Unknown provenance version");
        var rotation = compound(tag, "rotation");
        var policy = compound(tag, "policy");
        var parametersTag = compound(policy, "parameters");
        if (parametersTag.size() > StructureProvenance.MAX_POLICY_PARAMETERS) {
            throw new IllegalArgumentException("Too many provenance parameters");
        }
        var parameters = new LinkedHashMap<String, Double>();
        for (var key : parametersTag.keySet()) parameters.put(key, number(parametersTag, key));
        return new StructureProvenance.Metadata(StructureProvenance.CreationKind.valueOf(string(tag, "source")),
                UUID.fromString(string(tag, "operation")), UUID.fromString(string(tag, "lineage")),
                tag.contains("parent") ? Optional.of(decodeParent(compound(tag, "parent"))) : Optional.empty(),
                tag.contains("owner") ? Optional.of(UUID.fromString(string(tag, "owner"))) : Optional.empty(),
                Identifier.parse(string(tag, "skill")), new StructureProvenance.GridRotation(
                        number(rotation, "x"), number(rotation, "y"), number(rotation, "z"), number(rotation, "w")),
                new StructureProvenance.RestorePolicy(Identifier.parse(string(policy, "id")),
                        integer(policy, "version"), parameters), longNumber(tag, "created"));
    }

    /** Persistent identity deliberately excludes activation generation, which changes after loading. */
    public static CompoundTag encodeReference(StructureRef reference) {
        var tag = new CompoundTag();
        tag.putString("id", reference.id().toString());
        tag.putString("backend", reference.backend().toString());
        tag.putString("dimension", reference.dimension().toString());
        return tag;
    }

    public static StructureRef decodeReference(CompoundTag tag) {
        return new StructureRef(UUID.fromString(string(tag, "id")), Identifier.parse(string(tag, "dimension")),
                Identifier.parse(string(tag, "backend")), 0);
    }

    private static StructureRef decodeParent(CompoundTag tag) {
        var reference = decodeReference(tag);
        return new StructureRef(reference.id(), reference.dimension(), reference.backend(), longNumber(tag, "generation"));
    }

    private static void require(CompoundTag tag, String key, byte type) {
        var value = tag.get(key);
        if (value == null || value.getId() != type) throw new IllegalArgumentException("Invalid provenance field " + key);
    }

    private static String string(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_STRING);
        return tag.getString(key).orElseThrow();
    }

    private static int integer(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT);
        return tag.getInt(key).orElseThrow();
    }

    private static long longNumber(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LONG);
        return tag.getLong(key).orElseThrow();
    }

    private static double number(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_DOUBLE);
        return tag.getDouble(key).orElseThrow();
    }

    private static CompoundTag compound(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_COMPOUND);
        return tag.getCompound(key).orElseThrow();
    }
}
