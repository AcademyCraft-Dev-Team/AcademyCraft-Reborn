package org.academy.internal.client.ability.program;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Explicit local backup only. Does not edit a draft, save a server book or delete older backups. */
public final class ProgramUpgradeBackup {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_BACKUP_BYTES = 512 * 1024;
    public static final int MAX_BACKUP_FILES = 128;
    public static final long MAX_DIRECTORY_BYTES = 32L * 1024 * 1024;
    private ProgramUpgradeBackup() {}

    public static Receipt create(Path gameDirectory, int slot, long revision,
                                 AbilityProgram source, AbilityProgram candidate) throws IOException {
        if (slot < 0 || slot >= 10 || revision < 0 || !source.id().equals(candidate.id())
                || !source.category().equals(candidate.category()) || !source.name().equals(candidate.name())) {
            throw new IOException("Upgrade backup identity changed");
        }
        final byte[] originalBytes, candidateBytes;
        final AbilityProgram originalSnapshot, candidateSnapshot;
        try {
            originalBytes = ProgramBookCodec.encodeProgram(source);
            candidateBytes = ProgramBookCodec.encodeProgram(candidate);
            originalSnapshot = snapshot(originalBytes); candidateSnapshot = snapshot(candidateBytes);
        } catch (IllegalArgumentException failure) { throw new IOException("Program exceeds the import/export bounds", failure); }
        String originalClipboard = ProgramClipboardCodec.encodeProgram(originalSnapshot);
        String candidateClipboard = ProgramClipboardCodec.encodeProgram(candidateSnapshot);
        var body = new JsonObject();
        body.addProperty("format", "academy_program_numeric_upgrade_backup");
        body.addProperty("version", FORMAT_VERSION);
        body.addProperty("created_at_utc", Instant.now().toString());
        body.addProperty("slot", slot); body.addProperty("expected_server_revision", revision);
        body.addProperty("program_id", source.id().toString()); body.addProperty("category", source.category().toString());
        body.addProperty("source_sha256", sha(originalBytes)); body.addProperty("candidate_sha256", sha(candidateBytes));
        body.addProperty("original_codec_base64", Base64.getEncoder().encodeToString(originalBytes));
        body.addProperty("candidate_codec_base64", Base64.getEncoder().encodeToString(candidateBytes));
        body.addProperty("original_clipboard", originalClipboard); body.addProperty("candidate_clipboard", candidateClipboard);
        // Keep Base64 padding literal so a user can copy the quoted clipboard value from a text editor.
        byte[] bytes = (new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(body) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BACKUP_BYTES) throw new IOException("Upgrade backup exceeds 512 KiB");
        Path root = gameDirectory.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Game directory is unavailable");
        Path academy = directory(root.resolve("academy"), root);
        Path folder = directory(academy.resolve("program-upgrade-backups"), root);
        long total = 0; int count = 0;
        try (var entries = Files.newDirectoryStream(folder)) {
            for (var entry : entries) {
                if (++count >= MAX_BACKUP_FILES) throw new IOException("Upgrade backup folder reached 128 entries; organize it manually");
                if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) total += Files.size(entry);
                if (total + bytes.length > MAX_DIRECTORY_BYTES) throw new IOException("Upgrade backup folder reached 32 MiB; organize it manually");
            }
        }
        Path file = folder.resolve("program-upgrade-" + UUID.randomUUID() + ".json");
        boolean created = false;
        try {
            try (var channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        } catch (IOException failure) {
            if (created) try { Files.deleteIfExists(file); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        return new Receipt(file, sha(originalBytes), sha(candidateBytes), originalClipboard, candidateClipboard);
    }

    public static boolean sameProgram(AbilityProgram left, AbilityProgram right) {
        try { return Arrays.equals(ProgramBookCodec.encodeProgram(left), ProgramBookCodec.encodeProgram(right)); }
        catch (IllegalArgumentException failure) { return false; }
    }

    private static AbilityProgram snapshot(byte[] bytes) throws IOException {
        var decoded = ProgramBookCodec.decodeProgram(bytes);
        if (!decoded.valid() || decoded.program() == null
                || !Arrays.equals(bytes, ProgramBookCodec.encodeProgram(decoded.program()))) {
            throw new IOException("Program cannot round-trip exactly through its backup codec");
        }
        return decoded.program();
    }
    private static Path directory(Path path, Path root) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path);
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().startsWith(root)) throw new IOException("Upgrade backup folder must stay inside the game directory");
        return path;
    }
    private static String sha(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
    public record Receipt(Path path, String sourceSha256, String candidateSha256,
                          String originalClipboard, String candidateClipboard) {}
}
