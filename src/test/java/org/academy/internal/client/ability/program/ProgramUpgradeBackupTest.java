package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramUpgradeBackupTest {
    @TempDir Path gameDirectory;

    @Test
    void archivesBothExactCodecsAndImportableTextBeforeReturningAClosedFile() throws Exception {
        var source = program(new JsonObject());
        var candidate = candidate(source);
        var original = ProgramBookCodec.encodeProgram(source);
        var upgraded = ProgramBookCodec.encodeProgram(candidate);
        var receipt = ProgramUpgradeBackup.create(gameDirectory, 3, 29, source, candidate);
        var json = JsonParser.parseString(Files.readString(receipt.path())).getAsJsonObject();
        assertEquals(gameDirectory.resolve("academy/program-upgrade-backups").toRealPath(), receipt.path().getParent());
        assertEquals("academy_program_numeric_upgrade_backup", json.get("format").getAsString());
        assertEquals(ProgramUpgradeBackup.FORMAT_VERSION, json.get("version").getAsInt());
        assertEquals(3, json.get("slot").getAsInt());
        assertEquals(29, json.get("expected_server_revision").getAsLong());
        assertEquals(source.id().toString(), json.get("program_id").getAsString());
        assertEquals(source.category().toString(), json.get("category").getAsString());
        assertArrayEquals(original, Base64.getDecoder().decode(json.get("original_codec_base64").getAsString()));
        assertArrayEquals(upgraded, Base64.getDecoder().decode(json.get("candidate_codec_base64").getAsString()));
        assertEquals(hash(original), receipt.sourceSha256());
        assertEquals(hash(upgraded), receipt.candidateSha256());
        assertEquals(receipt.sourceSha256(), json.get("source_sha256").getAsString());
        assertEquals(receipt.candidateSha256(), json.get("candidate_sha256").getAsString());
        assertEquals(receipt.originalClipboard(), json.get("original_clipboard").getAsString());
        assertEquals(receipt.candidateClipboard(), json.get("candidate_clipboard").getAsString());
        assertTrue(Files.readString(receipt.path()).contains("\"" + receipt.originalClipboard() + "\""),
                "The importable text must be copyable directly, including literal Base64 padding");
        assertArrayEquals(original, ProgramBookCodec.encodeProgram(
                ProgramClipboardCodec.decodeProgram(receipt.originalClipboard(), source.category())));
        assertArrayEquals(upgraded, ProgramBookCodec.encodeProgram(
                ProgramClipboardCodec.decodeProgram(receipt.candidateClipboard(), source.category())));
        // A returned receipt has no open writer; the file can immediately be reopened exclusively.
        try (var channel = FileChannel.open(receipt.path(), StandardOpenOption.WRITE)) {
            assertEquals(Files.size(receipt.path()), channel.size());
        }
        assertArrayEquals(original, ProgramBookCodec.encodeProgram(source));
        assertTrue(Files.size(receipt.path()) <= ProgramUpgradeBackup.MAX_BACKUP_BYTES);
    }

    @Test
    void preservesUnknownConfigurationNumbersNodeIdsEdgesAndLayoutWithoutNormalization() throws Exception {
        var config = JsonParser.parseString("{\"extension\":{\"value\":9007199254740993,\"items\":[1,\"01\",null]},\"future\":true}").getAsJsonObject();
        var source = program(config);
        var encoded = ProgramBookCodec.encodeProgram(source);
        var receipt = ProgramUpgradeBackup.create(gameDirectory, 0, 0, source, candidate(source));
        var restored = ProgramClipboardCodec.decodeProgram(receipt.originalClipboard(), source.category());
        assertNotNull(restored);
        assertArrayEquals(encoded, ProgramBookCodec.encodeProgram(restored));
        assertEquals(source.graph().nodes().stream().map(ProgramGraph.Node::id).toList(),
                restored.graph().nodes().stream().map(ProgramGraph.Node::id).toList());
        assertEquals(source.graph().edges(), restored.graph().edges());
        assertEquals(source.editorLayout(), restored.editorLayout());
    }

    @Test
    void successiveExplicitBackupsNeverOverwriteEarlierFiles() throws Exception {
        var source = program(new JsonObject());
        var first = ProgramUpgradeBackup.create(gameDirectory, 0, 1, source, candidate(source));
        var bytes = Files.readAllBytes(first.path());
        var second = ProgramUpgradeBackup.create(gameDirectory, 0, 1, source, candidate(source));
        assertNotEquals(first.path(), second.path());
        assertArrayEquals(bytes, Files.readAllBytes(first.path()));
        try (var entries = Files.list(first.path().getParent())) { assertEquals(2, entries.count()); }
    }

    @Test
    void blockedBackupDirectoryLeavesExistingDataAndDraftUntouched() throws Exception {
        var source = program(new JsonObject());
        var bytes = ProgramBookCodec.encodeProgram(source);
        var blocked = gameDirectory.resolve("academy");
        Files.writeString(blocked, "existing user file");
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, 0, 1, source, candidate(source)));
        assertEquals("existing user file", Files.readString(blocked));
        assertArrayEquals(bytes, ProgramBookCodec.encodeProgram(source));
        try (var entries = Files.list(gameDirectory)) { assertEquals(1, entries.count()); }
    }

    @Test
    void fileCountBudgetRejectsWithoutDeletingOrOverwritingBackups() throws Exception {
        var folder = Files.createDirectories(gameDirectory.resolve("academy/program-upgrade-backups"));
        for (int i = 0; i < ProgramUpgradeBackup.MAX_BACKUP_FILES; i++) Files.writeString(folder.resolve("prior-" + i), "keep");
        var source = program(new JsonObject());
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, 0, 0, source, candidate(source)));
        try (var entries = Files.list(folder)) { assertEquals(ProgramUpgradeBackup.MAX_BACKUP_FILES, entries.count()); }
        assertEquals("keep", Files.readString(folder.resolve("prior-0")));
    }

    @Test
    void byteBudgetRejectsWithoutPruningExistingBackup() throws Exception {
        var folder = Files.createDirectories(gameDirectory.resolve("academy/program-upgrade-backups"));
        var prior = folder.resolve("prior.json");
        try (var file = FileChannel.open(prior, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            file.position(ProgramUpgradeBackup.MAX_DIRECTORY_BYTES - 1);
            file.write(ByteBuffer.wrap(new byte[] { 1 }));
        }
        var source = program(new JsonObject());
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, 0, 0, source, candidate(source)));
        assertEquals(ProgramUpgradeBackup.MAX_DIRECTORY_BYTES, Files.size(prior));
        try (var entries = Files.list(folder)) { assertEquals(1, entries.count()); }
    }

    @Test
    void invalidIdentityAndOversizedConfigurationFailBeforeCreatingAnyBackup() throws Exception {
        var source = program(new JsonObject());
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, -1, 0, source, candidate(source)));
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, 0, -1, source, candidate(source)));
        var other = program(new JsonObject());
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, 0, 0, source, other));
        var oversized = new JsonObject(); oversized.addProperty("payload", "x".repeat(9000));
        var tooLarge = program(oversized);
        assertThrows(IOException.class, () -> ProgramUpgradeBackup.create(gameDirectory, 0, 0, tooLarge, candidate(tooLarge)));
        assertFalse(Files.exists(gameDirectory.resolve("academy")));
    }

    @Test
    void exactWireComparisonRejectsGsonNumberEqualityCollisionsAndChangedLayout() {
        var first = program(JsonParser.parseString("{\"value\":9007199254740992}").getAsJsonObject());
        var config = JsonParser.parseString("{\"value\":9007199254740993}").getAsJsonObject();
        var changed = replaceConfiguration(first, config);
        assertEquals(first.graph().nodes().getFirst().configuration(), changed.graph().nodes().getFirst().configuration(),
                "Gson parsed-number equality must never authorize a graph replacement");
        assertFalse(ProgramUpgradeBackup.sameProgram(first, changed));
        var moved = new AbilityProgram(first.schemaVersion(), first.id(), first.name(), first.category(), first.graph(),
                new ProgramEditorLayout(Map.of(7, new ProgramEditorLayout.NodePosition(99, 12))));
        assertFalse(ProgramUpgradeBackup.sameProgram(first, moved));
        assertTrue(ProgramUpgradeBackup.sameProgram(first, ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(first)).program()));
    }

    private static AbilityProgram program(JsonObject configuration) {
        // Unknown category intentionally verifies a raw archival contract, independent of editor defaults or migrations.
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), "original draft", AcademyCraft.academy("backup_contract"),
                new ProgramGraph(List.of(new ProgramGraph.Node(7, CommonProgramNodeIds.INTEGER_ADD, 1, configuration),
                        new ProgramGraph.Node(21, AcademyCraft.academy("extension/unknown"), 3, new JsonObject())),
                        List.of(new ProgramGraph.Edge(new ProgramGraph.Endpoint(7, "result"), new ProgramGraph.Endpoint(21, "input")))),
                new ProgramEditorLayout(Map.of(7, new ProgramEditorLayout.NodePosition(-12.25, 45.5),
                        21, new ProgramEditorLayout.NodePosition(192, -8))));
    }

    private static AbilityProgram candidate(AbilityProgram source) {
        var config = new JsonObject(); config.addProperty("type", "integer"); config.addProperty("operator", "add");
        return new AbilityProgram(source.schemaVersion(), source.id(), source.name(), source.category(),
                new ProgramGraph(List.of(new ProgramGraph.Node(7, CommonProgramNodeIds.NUMERIC_ARITHMETIC, 1, config),
                        source.graph().nodes().get(1)), source.graph().edges()), source.editorLayout());
    }

    private static AbilityProgram replaceConfiguration(AbilityProgram source, JsonObject configuration) {
        return new AbilityProgram(source.schemaVersion(), source.id(), source.name(), source.category(),
                new ProgramGraph(List.of(new ProgramGraph.Node(7, CommonProgramNodeIds.INTEGER_ADD, 1, configuration),
                        source.graph().nodes().get(1)), source.graph().edges()), source.editorLayout());
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
