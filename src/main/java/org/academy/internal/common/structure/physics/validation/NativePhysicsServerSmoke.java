package org.academy.internal.common.structure.physics.validation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Dedicated-server module check with a guarded disposable save and automatic shutdown. */
@EventBusSubscriber(modid = "academy", value = Dist.DEDICATED_SERVER)
public final class NativePhysicsServerSmoke {
    private static ExitState exitState;

    private NativePhysicsServerSmoke() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!"server".equals(System.getProperty("academy.nativePhysicsSmoke"))) return;
        var server = event.getServer();
        var directory = NativePhysicsSmokeSupport.verifyDirectory("server", server.getServerDirectory());
        try {
            var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            if (!world.equals(directory.resolve("native-physics-smoke"))) {
                throw new IllegalStateException("Physics server probe must use its isolated native-physics-smoke world");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot verify isolated physics server world", failure);
        }
        if (exitState != null) throw new IllegalStateException("Physics server probe already ran in this JVM");
        var state = new ExitState(server, directory);
        exitState = state;
        try {
            NativePhysicsSmokeSupport.runAndRecord("server", directory);
            state.status = 0;
        } finally {
            state.executed = true;
            server.halt(false);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        var state = exitState;
        if (state != null && state.server == event.getServer() && state.executed) {
            state.orderlyStopObserved = true;
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var state = exitState;
        if (!"server".equals(System.getProperty("academy.nativePhysicsSmoke"))
                || state == null || state.server != event.getServer() || !state.executed || state.watching) return;
        state.watching = true;
        // This event follows stopServer's save/close attempt, including when that attempt throws.
        // It is evidence of lifecycle order, not proof of a successful save or crash recovery.
        System.out.println("NATIVE_PHYSICS_SERVER_STOPPED_EVENT probe_status=" + state.status);
        Thread.ofPlatform().daemon().name("academy-native-physics-smoke-exit").start(() -> exitAfterServerThread(state));
    }

    private static void exitAfterServerThread(ExitState state) {
        var serverThread = state.server.getRunningThread();
        boolean interrupted = false;
        while (serverThread.isAlive()) {
            try {
                serverThread.join();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        // Exit from this daemon only after the server thread ends: a shutdown hook may join it.
        int status = state.status;
        if (interrupted || !state.orderlyStopObserved || state.server.isCurrentlySaving()) status = 1;
        try {
            boolean crashReportsPresent = false;
            var crashDirectory = state.directory.resolve("crash-reports");
            if (Files.isDirectory(crashDirectory)) {
                try (var reports = Files.list(crashDirectory)) {
                    crashReportsPresent = reports.findAny().isPresent();
                }
            }
            if (crashReportsPresent) status = 1;
            Files.writeString(state.directory.resolve("native-physics-server-exit.txt"),
                    "probe_status=" + state.status + "\nexit_status=" + status
                            + "\nserver_stopped_event=true\nserver_thread_terminated=true"
                            + "\norderly_stop_event=" + state.orderlyStopObserved
                            + "\ncrash_reports_present=" + crashReportsPresent + "\n",
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException failure) {
            status = 1;
            System.err.println("NATIVE_PHYSICS_SERVER_EXIT_RECORD_FAILED " + failure);
        }
        System.out.println("NATIVE_PHYSICS_SERVER_THREAD_TERMINATED exit_status=" + status);
        if (interrupted) Thread.currentThread().interrupt();
        ServerLifecycleHooks.handleExit(status);
    }

    private static final class ExitState {
        private final MinecraftServer server;
        private final Path directory;
        private int status = 1;
        private boolean executed;
        private boolean orderlyStopObserved;
        private boolean watching;

        private ExitState(MinecraftServer server, Path directory) {
            this.server = server;
            this.directory = directory;
        }
    }
}
