package org.academy.internal.common.compat;

import net.neoforged.fml.loading.FMLLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Early-loader fingerprint gate; deliberately has no optional mod class references. */
public final class SableStaffMixinGate {
    public static final String VERSION = "1.3.2-port.26.2.0.70";
    public static final String SHA256 = "97066b9410316b370c3b3db7fd4a9eb3e54f41155b3e3c13b146a9de26b35bdd";
    public static final String SABLE_VERSION = "2.0.6-port.26.2.0.70";
    public static final String SABLE_SHA256 = "21a22bcd6b0dd5bc742472e332f7e8a17dd83cbbd471ebe97087e153acc206ca";
    public static final String COMPANION_VERSION = "1.6.0-port.26.2.0.70";
    public static final String COMPANION_SHA256 = "91e4fe9ef30994904bfa18bf140e804714700cd89d272a337b3ecaea9df5cad4";
    private static volatile State state;

    private SableStaffMixinGate() {}

    public static State state() {
        var current = state;
        if (current != null) return current;
        synchronized (SableStaffMixinGate.class) {
            if (state == null) state = inspect();
            return state;
        }
    }

    private static State inspect() {
        var mods = FMLLoader.getCurrent().getLoadingModList();
        var file = mods.getModFileById("simulated");
        if (file == null) return State.ABSENT;
        var sable = mods.getModFileById("sable");
        var companion = mods.getModFileById("sablecompanion");
        if (sable == null || companion == null) return State.UNSUPPORTED;
        var version = file.getMods().stream().filter(info -> info.getModId().equals("simulated"))
                .map(info -> info.getVersion().toString()).findFirst().orElse("");
        var sableVersion = sable.getMods().stream().filter(info -> info.getModId().equals("sable"))
                .map(info -> info.getVersion().toString()).findFirst().orElse("");
        var companionVersion = companion.getMods().stream().filter(info -> info.getModId().equals("sablecompanion"))
                .map(info -> info.getVersion().toString()).findFirst().orElse("");
        if (!VERSION.equals(version) || !SABLE_VERSION.equals(sableVersion)
                || !COMPANION_VERSION.equals(companionVersion)) return State.UNSUPPORTED;
        try {
            return compatible(fingerprint(file.getFile().getFilePath(), version),
                    fingerprint(sable.getFile().getFilePath(), sableVersion),
                    fingerprint(companion.getFile().getFilePath(), companionVersion))
                    ? State.VERIFIED : State.UNSUPPORTED;
        } catch (IOException unreadable) {
            return State.UNSUPPORTED;
        }
    }

    static boolean matches(Path path, String version) throws IOException {
        if (!VERSION.equals(version) || !Files.isRegularFile(path)) return false;
        return new Fingerprint(VERSION, SHA256).equals(fingerprint(path, version));
    }

    static boolean compatible(Fingerprint simulated, Fingerprint sable, Fingerprint companion) {
        return new Fingerprint(VERSION, SHA256).equals(simulated)
                && new Fingerprint(SABLE_VERSION, SABLE_SHA256).equals(sable)
                && new Fingerprint(COMPANION_VERSION, COMPANION_SHA256).equals(companion);
    }

    private static Fingerprint fingerprint(Path path, String version) throws IOException {
        if (!Files.isRegularFile(path)) return new Fingerprint(version, "");
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[65_536];
            for (int count; (count = input.read(buffer)) >= 0;) if (count != 0) digest.update(buffer, 0, count);
            return new Fingerprint(version, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    record Fingerprint(String version, String sha256) {}

    public enum State { ABSENT, VERIFIED, UNSUPPORTED }
}
