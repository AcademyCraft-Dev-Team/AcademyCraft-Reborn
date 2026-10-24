package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelOccupancySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import org.academy.internal.common.structure.DurableFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPOutputStream;

/** Durably commits Sable's allocator bitmap through its own schema and namespaced data path. */
final class SableRestorationDurability {
    private SableRestorationDurability() {}

    static void occupancy(ServerLevel host) throws IOException {
        var saved = SubLevelOccupancySavedData.getOrLoad(host);
        // Join previous vanilla writes before an explicit replacement, so an old async image
        // cannot later overwrite this transaction's allocator state. Vanilla swallows write
        // errors; the forced, verified atomic replacement below supplies the actual receipt.
        saved.setDirty();
        host.getDataStorage().saveAndJoin();
        var data = saved.save(new CompoundTag(), host.registryAccess());
        var root = new CompoundTag();
        root.put("data", data);
        NbtUtils.addCurrentDataVersion(root);
        var folder = DimensionType.getStorageFolder(host.dimension(), host.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
        var path = Identifier.fromNamespaceAndPath("sable", SubLevelOccupancySavedData.FILE_ID).withSuffix(".dat").resolveAgainst(folder);
        Files.createDirectories(path.getParent());
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(bytes))) { NbtIo.writeUnnamedTag(root, output); }
        DurableFile.writeAtomically(path, bytes.toByteArray(), (source, target, atomic) -> {
            if (!atomic) throw new IOException("Sable allocator ownership requires atomic replacement");
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
        if (!NbtIo.readCompressed(path, NbtAccounter.create(1024 * 1024)).equals(root)) {
            throw new IOException("Sable allocator image did not round-trip");
        }
    }
}
