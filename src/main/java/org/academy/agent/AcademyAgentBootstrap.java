package org.academy.agent;

import org.academy.AcademyCraft;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AcademyAgentBootstrap {
    private static final String HANDLER_CLASS = "org.academy.internal.coremod.AcademyAgentEntrypoint";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    public static void ensureInstalled() {
        preloadHandler();
        if (!STARTED.compareAndSet(false, true)
                || Boolean.getBoolean("academy.agent.embedded.disable")
                || Boolean.getBoolean("academy.agent.installed")) {
            return;
        }

        var selfJar = selfJarPath();
        if (selfJar == null || !Files.isRegularFile(selfJar) || !selfJar.toString().endsWith(".jar")) {
            return;
        }
        var java = javaExecutable();
        if (java == null) return;

        try {
            var command = List.of(
                    java.toString(), "--add-modules", "jdk.attach", "-cp", selfJar.toString(),
                    "org.academy.agent.AttachMain", Long.toString(ProcessHandle.current().pid()),
                    selfJar.toString(), HANDLER_CLASS
            );
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(15L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                AcademyCraft.getLogger().warn("Academy agent self-attach timed out");
                return;
            }
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                AcademyCraft.getLogger().warn("Academy agent self-attach failed ({}): {}",
                        process.exitValue(), output);
            }
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn("Academy agent self-attach failed", error);
        }
    }

    private static void preloadHandler() {
        try {
            Class.forName(HANDLER_CLASS, true, AcademyAgentBootstrap.class.getClassLoader());
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn("Unable to preload Academy agent handler", error);
        }
    }

    private static Path selfJarPath() {
        try {
            var location = AcademyAgentBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
            return Path.of(new URI(location.toString())).toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path javaExecutable() {
        var home = System.getProperty("java.home");
        if (home == null || home.isBlank()) return null;
        var binary = Path.of(home, "bin", System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java");
        return Files.isRegularFile(binary) ? binary : null;
    }

    private AcademyAgentBootstrap() {
    }
}
