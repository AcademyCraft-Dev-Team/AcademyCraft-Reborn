package org.academy.api.client.render.vfxgraph.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GraphFileWatcher implements AutoCloseable {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private final Path root;
    private final WatchService watchService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "AcademyVfxGraphWatcher");
        t.setDaemon(true);
        return t;
    });
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();

    public GraphFileWatcher(Path root) throws IOException {
        this.root = root;
        this.watchService = FileSystems.getDefault().newWatchService();
    }

    public void registerTree() throws IOException {
        registerTree(root);
    }

    private void registerTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        var key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchKeys.put(key, dir);
        try (var stream = Files.list(dir)) {
            for (var child : stream.toList()) {
                if (Files.isDirectory(child)) {
                    registerTree(child);
                }
            }
        }
    }

    public void pollOnce() {
        var key = watchService.poll();
        if (key == null) {
            return;
        }
        var dir = watchKeys.get(key);
        for (var event : key.pollEvents()) {
            var kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && dir != null) {
                try {
                    registerTree(dir);
                } catch (IOException ignored) {
                }
            }
            if (dir == null) {
                continue;
            }
            var name = (Path) event.context();
            if (name.toString().endsWith(".json")) {
                handleChange(dir.resolve(name));
            }
        }
        key.reset();
    }

    private void handleChange(Path file) {
        var fileName = file.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return;
        }
        var assetName = fileName.substring(0, fileName.length() - ".json".length());
        var assetId = Identifier.fromNamespaceAndPath("academy", "vfxgraph/" + assetName);
        try {
            var content = Files.readString(file);
            Minecraft.getInstance().execute(() -> {
                try {
                    VfxGraphManager.INSTANCE.reloadFromContent(assetId, content);
                    LOGGER.info("Vfx graph asset reloaded: {}", assetId);
                } catch (Exception exception) {
                    LOGGER.error("Unable to reload vfx graph asset: {}", assetId, exception);
                }
            });
        } catch (IOException exception) {
            LOGGER.error("Unable to read vfx graph asset: {}", file, exception);
        }
    }

    public void startLoop() {
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (watchService.take() != null) {
                        pollOnce();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException exception) {
            LOGGER.warn("Unable to close vfx graph watcher", exception);
        }
    }
}
