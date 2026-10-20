package org.academy.internal.common.sublevel;

import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Failure receipts are evidence for recovery, never instructions to replay drops or restore a block. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SubLevelMiningRecovery {
    private static final Map<MinecraftServer, List<Pending>> PENDING = new IdentityHashMap<>();

    private SubLevelMiningRecovery() {
    }

    static synchronized void retain(MinecraftServer server, NativeStructureBreakDrops.Scope scope,
                                    Throwable failure) {
        var pending = new Pending(UUID.randomUUID(), scope, failure.toString());
        try {
            persist(server, pending);
        } catch (IOException | RuntimeException saveFailure) {
            // Keep the actual item/container receipts alive if serialization or disk I/O failed.
            PENDING.computeIfAbsent(server, ignored -> new ArrayList<>()).add(pending);
            failure.addSuppressed(saveFailure);
            AcademyCraft.LOGGER.error("SubLevel mining outputs retained in memory; recovery record {} "
                    + "could not be saved and will be retried before server shutdown", pending.id(), saveFailure);
        }
    }

    @SubscribeEvent
    public static synchronized void stopping(ServerStoppingEvent event) {
        var server = event.getServer();
        var pending = PENDING.get(server);
        if (pending == null) return;
        pending.removeIf(entry -> {
            try {
                persist(server, entry);
                return true;
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("Unpersisted SubLevel mining outputs remain retained: {}",
                        entry.id(), failure);
                return false;
            }
        });
        if (pending.isEmpty()) PENDING.remove(server);
    }

    private static void persist(MinecraftServer server, Pending pending) throws IOException {
        var evidence = pending.scope().recoveryEvidence();
        evidence.putString("failure", pending.failure());
        evidence.putString("recovery_id", pending.id().toString());
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(evidence, bytes);
        var directory = server.getWorldPath(LevelResource.ROOT).resolve("data/academy/sublevel-mining-recovery");
        Files.createDirectories(directory);
        Path target = directory.resolve(pending.id() + ".nbt");
        Path temporary = directory.resolve(pending.id() + ".pending");
        // A failed prior write can be overwritten only for this same retained receipt.
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes.toByteArray());
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        AcademyCraft.LOGGER.error("SubLevel mining output recovery evidence saved to {}. "
                + "Do not automatically replay outputs or restore the original block.", target);
    }

    private record Pending(UUID id, NativeStructureBreakDrops.Scope scope, String failure) {
    }
}
