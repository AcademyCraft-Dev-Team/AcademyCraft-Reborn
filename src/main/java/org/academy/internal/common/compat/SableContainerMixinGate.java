package org.academy.internal.common.compat;

import net.neoforged.fml.loading.FMLLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Early-loader fingerprint gate for the Sable container tick compatibility mixin; deliberately
 * has no optional mod class references. The mixin is applied only to the exact audited artifact,
 * so a different Sable build disables the workaround instead of failing to apply.
 */
public final class SableContainerMixinGate {
    public static final String SABLE_VERSION = "2.0.6-port.26.2.0.70";
    public static final String SABLE_SHA256 = "21a22bcd6b0dd5bc742472e332f7e8a17dd83cbbd471ebe97087e153acc206ca";
    private static volatile State state;

    private SableContainerMixinGate() {}

    public static State state() {
        var current = state;
        if (current != null) return current;
        synchronized (SableContainerMixinGate.class) {
            if (state == null) state = inspect();
            return state;
        }
    }

    private static State inspect() {
        var mods = FMLLoader.getCurrent().getLoadingModList();
        var file = mods.getModFileById("sable");
        if (file == null) return State.ABSENT;
        var version = file.getMods().stream().filter(info -> info.getModId().equals("sable"))
                .map(info -> info.getVersion().toString()).findFirst().orElse("");
        if (!SABLE_VERSION.equals(version)) return State.UNSUPPORTED;
        try {
            return matches(file.getFile().getFilePath()) ? State.VERIFIED : State.UNSUPPORTED;
        } catch (IOException unreadable) {
            return State.UNSUPPORTED;
        }
    }

    private static boolean matches(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return false;
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[65_536];
            for (int count; (count = input.read(buffer)) >= 0;) if (count != 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(SABLE_SHA256);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public enum State { ABSENT, VERIFIED, UNSUPPORTED }
}
