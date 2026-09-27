package org.academy.internal.common.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorldWeaverConfigAccessTest {
    @Test
    @Timeout(20)
    void concurrentPillarUpdatesAndNbtSavesPreserveAllValues() throws Exception {
        var root = new CompoundTag();
        var pillars = new CompoundTag();
        root.put("pillars", pillars);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Future<?>>();
            for (var worker = 0; worker < 4; worker++) {
                var prefix = worker + "_";
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (var i = 0; i < 1000; i++) {
                        WorldWeaverConfigAccess.putInt(pillars, prefix + i, i);
                        var position = new BlockPos(i, 64, -i);
                        WorldWeaverConfigAccess.store(root, prefix + i, BlockPos.CODEC, position);
                        assertEquals(position, WorldWeaverConfigAccess.read(root, prefix + i, BlockPos.CODEC).orElseThrow());
                        assertTrue(WorldWeaverConfigAccess.contains(pillars, prefix + i));
                        assertEquals(i, WorldWeaverConfigAccess.getIntOr(pillars, prefix + i, -1));
                    }
                    return null;
                }));
            }
            for (var writer = 0; writer < 2; writer++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (var i = 0; i < 100; i++) {
                        var snapshot = WorldWeaverConfigAccess.locked(() -> encode(root));
                        assertTrue(NbtIo.read(new DataInputStream(new ByteArrayInputStream(snapshot)))
                                .contains("pillars"));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        }
        var saved = NbtIo.read(new DataInputStream(new ByteArrayInputStream(encode(root))))
                .getCompoundOrEmpty("pillars");
        assertEquals(4000, saved.size());
        for (var worker = 0; worker < 4; worker++) {
            for (var i = 0; i < 1000; i++) assertEquals(i, saved.getIntOr(worker + "_" + i, -1));
        }
    }

    @Test
    @Timeout(10)
    void lockIsReentrantAndReleasedAfterSaveFailure() throws Exception {
        var failure = new IllegalStateException("write failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> WorldWeaverConfigAccess.locked(() -> WorldWeaverConfigAccess.locked(() -> {
                    throw failure;
                }))));
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertEquals(42, executor.submit(() -> WorldWeaverConfigAccess.locked(() -> 42))
                    .get(5, TimeUnit.SECONDS));
        }
    }

    private static byte[] encode(CompoundTag root) {
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(root, new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
