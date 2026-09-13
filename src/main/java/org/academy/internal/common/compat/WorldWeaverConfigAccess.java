package org.academy.internal.common.compat;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.function.Supplier;

/** Shared by WorldWeaver serialization and BetterEnd's direct world-config accesses. */
public final class WorldWeaverConfigAccess {
    private static final Object LOCK = new Object();

    private WorldWeaverConfigAccess() {}

    public static boolean contains(CompoundTag tag, String key) {
        return locked(() -> tag.contains(key));
    }

    public static int getIntOr(CompoundTag tag, String key, int fallback) {
        return locked(() -> tag.getIntOr(key, fallback));
    }

    public static void putInt(CompoundTag tag, String key, int value) {
        locked(() -> {
            tag.putInt(key, value);
            return null;
        });
    }

    public static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec) {
        return locked(() -> tag.read(key, codec));
    }

    public static <T> void store(CompoundTag tag, String key, Codec<T> codec, T value) {
        locked(() -> {
            tag.store(key, codec, value);
            return null;
        });
    }

    /** Never hold this lock while requesting or generating a chunk. */
    public static <T> T locked(Supplier<T> action) {
        synchronized (LOCK) {
            return action.get();
        }
    }
}
