package org.academy.internal.common.compatibility;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.internal.server.storage.SpatialStorageSavedData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional bridge to BeyondDimensions' public 26.2 API, without a mandatory runtime dependency. */
public final class BeyondDimensionsSpatialCompat {
    private static final String API = "com.wintercogs.beyonddimensions.api.";
    private static Access access;
    private static boolean unavailable;

    private BeyondDimensionsSpatialCompat() {
    }

    public static void transfer(ServerPlayer player, SpatialStorageSavedData data, UUID id) {
        if (unavailable || data.count(id) == 0) return;
        try {
            if (access == null) access = new Access();
            var net = access.findNet.invoke(null, player);
            if (net == null) return;
            var storage = access.getStorage.invoke(net);
            data.transfer(id, 128, (resource, count) -> {
                try {
                    var key = access.itemKey.newInstance(resource.toStack());
                    var remainder = access.insert.invoke(storage, key, count, false);
                    return count - (long) access.amount.invoke(remainder);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("BeyondDimensions insertion failed", exception);
                }
            });
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            unavailable = true;
            AcademyCraft.LOGGER.error("Spatial storage: disabling incompatible BeyondDimensions bridge", exception);
        }
    }

    private static final class Access {
        private final Method findNet;
        private final Method getStorage;
        private final Constructor<?> itemKey;
        private final Method insert;
        private final Method amount;

        private Access() throws ReflectiveOperationException {
            var net = Class.forName(API + "dimensionnet.DimensionsNet");
            findNet = net.getMethod("getNetFromPlayer", Player.class);
            getStorage = net.getMethod("getUnifiedStorage");
            itemKey = Class.forName(API + "storage.key.impl.ItemStackKey").getConstructor(ItemStack.class);
            insert = Class.forName(API + "dimensionnet.UnifiedStorage").getMethod("insert",
                    Class.forName(API + "storage.key.IStackKey"), long.class, boolean.class);
            amount = Class.forName(API + "storage.key.KeyAmount").getMethod("amount");
        }
    }
}
