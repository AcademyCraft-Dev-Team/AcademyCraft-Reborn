package org.academy.internal.common.ability.program;

import io.netty.buffer.Unpooled;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.server.world.level.storage.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityProgramManagerTest {
    @Test
    void storedBooksAreBoundedAndBoundToTheirCategory() {
        var accelerator = AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR);
        var program = program(accelerator);
        var book = new ProgramBook(
                ProgramBook.CURRENT_SCHEMA_VERSION,
                3L,
                1,
                List.of(
                        new ProgramBook.Slot(program),
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY
                )
        );

        var encoded = AbilityProgramManager.encodeStoredBook(book);
        var decoded = AbilityProgramManager.decodeStoredBook(accelerator, encoded);

        assertEquals(book.resize(AbilityProgramManager.SLOT_COUNT), decoded);
        assertEquals(AbilityProgramManager.SLOT_COUNT, decoded.slots().size());
        assertTrue(AbilityProgramManager.validBook(accelerator, decoded));
        var wrongCategory = AbilityProgramManager.decodeStoredBook(
                AcademyCraft.academy(AbilityCategoryNames.TELEPORT), encoded);
        assertEquals(0L, wrongCategory.revision());
        assertTrue(wrongCategory.slots().stream().allMatch(ProgramBook.Slot::empty));
        assertEquals(0L, AbilityProgramManager.decodeStoredBook(
                accelerator, "not base64").revision());
    }

    @Test
    void learnedSkillIdsBecomeServerCompileCapabilities() {
        var player = new Player();
        var vectorAccel = AcademyCraft.academy(SkillNames.VECTOR_ACCEL);
        player.getSkillDataMap().put(vectorAccel.toString(), new CommonSkillData());
        player.getSkillDataMap().put("invalid identifier", new CommonSkillData());

        var capabilities = AbilityProgramManager.learnedCapabilities(player);

        assertEquals(java.util.Set.of(vectorAccel), capabilities);
    }

    @Test
    void requestSaveAndExecutePacketsRoundTripCategoryAndBounds() {
        var category = AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR);
        var requestBuffer = Unpooled.buffer();
        AbilityProgramManager.RequestPacket.CODEC.encode(
                requestBuffer, new AbilityProgramManager.RequestPacket(category));
        assertEquals(category.toString(), AbilityProgramManager.RequestPacket.CODEC
                .decode(requestBuffer).category());

        var importBook = ProgramBookCodec.encode(ProgramBook.empty(AbilityProgramManager.SLOT_COUNT));
        var importBuffer = Unpooled.buffer();
        AbilityProgramManager.ImportPacket.CODEC.encode(importBuffer,
                new AbilityProgramManager.ImportPacket(category, 0L, importBook));
        var imported = AbilityProgramManager.ImportPacket.CODEC.decode(importBuffer);
        assertEquals(category.toString(), imported.category());
        assertEquals(0L, imported.expectedRevision());
        assertArrayEquals(importBook, imported.book());

        var programBytes = ProgramBookCodec.encodeProgram(program(category));
        var saveBuffer = Unpooled.buffer();
        AbilityProgramManager.SavePacket.CODEC.encode(saveBuffer,
                new AbilityProgramManager.SavePacket(category, 2, 19L, programBytes));
        var save = AbilityProgramManager.SavePacket.CODEC.decode(saveBuffer);
        assertEquals(category.toString(), save.category());
        assertEquals(2, save.slot());
        assertEquals(19L, save.expectedRevision());
        assertArrayEquals(programBytes, save.program());

        var executeBuffer = Unpooled.buffer();
        AbilityProgramManager.ExecutePacket.CODEC.encode(executeBuffer,
                new AbilityProgramManager.ExecutePacket(category, 3, 27L));
        var execute = AbilityProgramManager.ExecutePacket.CODEC.decode(executeBuffer);
        assertEquals(category.toString(), execute.category());
        assertEquals(3, execute.slot());
        assertEquals(27L, execute.sequence());
    }

    @Test
    void syncAndStructuredResultPacketsRoundTrip() {
        var category = AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR).toString();
        var book = new byte[]{4, 8, 15, 16, 23, 42};
        var syncBuffer = Unpooled.buffer();
        AbilityProgramManager.SyncPacket.CODEC.encode(syncBuffer,
                new AbilityProgramManager.SyncPacket(category, book));
        var sync = AbilityProgramManager.SyncPacket.CODEC.decode(syncBuffer);
        assertEquals(category, sync.category());
        assertArrayEquals(book, sync.book());

        var resultBuffer = Unpooled.buffer();
        AbilityProgramManager.ResultPacket.CODEC.encode(resultBuffer,
                new AbilityProgramManager.ResultPacket(
                        category,
                        1,
                        AbilityProgramManager.FeedbackType.ERROR,
                        31L,
                        AbilityProgramManager.ResultCode.INVALID_PROGRAM,
                        ProgramDiagnosticCode.CAPABILITY_MISSING,
                        7,
                        ProgramVmDiagnostic.EXECUTOR_ERROR
                ));
        var result = AbilityProgramManager.ResultPacket.CODEC.decode(resultBuffer);
        assertEquals(category, result.category());
        assertEquals(1, result.slot());
        assertEquals(AbilityProgramManager.FeedbackType.ERROR, result.type());
        assertEquals(31L, result.revision());
        assertEquals(AbilityProgramManager.ResultCode.INVALID_PROGRAM, result.code());
        assertEquals(ProgramDiagnosticCode.CAPABILITY_MISSING, result.diagnostic());
        assertEquals(7, result.nodeId());
        assertEquals(ProgramVmDiagnostic.EXECUTOR_ERROR, result.vmDiagnostic());
    }

    @Test
    void supportedRoutingExcludesPrecisionAndLevelZero() {
        assertTrue(AbilityProgramManager.isSupportedCategory(
                AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR)));
        assertFalse(AbilityProgramManager.isSupportedCategory(
                AcademyCraft.academy(AbilityCategoryNames.MENTALOUT)));
        assertFalse(AbilityProgramManager.isSupportedCategory(
                AcademyCraft.academy(AbilityCategoryNames.LEVEL0)));
        assertFalse(AbilityProgramManager.isSupportedCategory(AcademyCraft.academy("unknown")));
        assertFalse(AbilityProgramManager.isSupportedCategory(null));
    }

    private static AbilityProgram program(net.minecraft.resources.Identifier category) {
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "Server program",
                category,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );
    }
}
