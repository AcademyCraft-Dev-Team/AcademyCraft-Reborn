package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Shared Mojang Codecs for Misaka SavedData (string UUID / "x,y,z" BlockPos / dimension keys).
 * Avoids {@link Level#OVERWORLD} so unit tests do not touch FML AttachmentHolder.
 */
public final class MisakaSavedDataCodecs {
    public static final Codec<UUID> UUID_STRING_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );

    public static final Codec<BlockPos> BLOCK_POS_STRING_CODEC = Codec.STRING.flatXmap(
            value -> {
                try {
                    var parts = value.split(",");
                    if (parts.length != 3) {
                        return DataResult.error(() -> "Invalid BlockPos: " + value);
                    }
                    return DataResult.success(new BlockPos(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                    ));
                } catch (NumberFormatException exception) {
                    return DataResult.error(() -> "Invalid BlockPos: " + value);
                }
            },
            pos -> DataResult.success(pos.getX() + "," + pos.getY() + "," + pos.getZ())
    );

    public static final Codec<ResourceKey<Level>> DIMENSION_CODEC = Identifier.CODEC.flatXmap(
            id -> DataResult.success(ResourceKey.create(Registries.DIMENSION, id)),
            key -> DataResult.success(key.identifier())
    );

    public static final ResourceKey<Level> DEFAULT_OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

    private MisakaSavedDataCodecs() {
    }
}
