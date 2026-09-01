package org.academy.internal.common.ability.program;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
        var vectorAccel = AcademyCraft.academy(SkillNames.VECTOR_ACCEL);
        var player = WorldData.createGson().fromJson("""
                {
                  "skillData": {
                    "academy:vector_accel": {"proficiency": 0.0},
                    "invalid identifier": {"proficiency": 0.0}
                  }
                }
                """, Player.class);

        var capabilities = AbilityProgramManager.learnedCapabilities(player);

        assertEquals(Set.of(vectorAccel), capabilities);
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
    void supportedRoutingIncludesMentaloutAndExcludesLevelZero() {
        assertTrue(AbilityProgramManager.isSupportedCategory(
                AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR)));
        assertTrue(AbilityProgramManager.isSupportedCategory(
                AcademyCraft.academy(AbilityCategoryNames.MENTALOUT)));
        assertFalse(AbilityProgramManager.isSupportedCategory(
                AcademyCraft.academy(AbilityCategoryNames.LEVEL0)));
        assertFalse(AbilityProgramManager.isSupportedCategory(AcademyCraft.academy("unknown")));
        assertFalse(AbilityProgramManager.isSupportedCategory(null));
    }

    @Test
    void actionFailuresExposePlayerActionableDiagnostics() {
        assertAll(
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.BLOCK_BREAK_DISABLED,
                        "Darkmatter block destruction is disabled"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_PROTECTED,
                        "Darkmatter target block cannot be broken or is protected"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_OUT_OF_RANGE,
                        "Darkmatter block target is outside program range"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.POWER_LIMIT,
                        "Entity displacement exceeds its strength limit"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.WORLD_UNAVAILABLE,
                        "Teleport destination is not loaded"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.DESTINATION_BLOCKED,
                        "Block destination is occupied"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.DESTINATION_UNSAFE,
                        "Entity destination is unsafe"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.INVENTORY_FULL,
                        "Player inventory cannot hold all collected items"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.INVALID_DIRECTION,
                        "Darkmatter Cut requires a non-vertical direction"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_MOVEMENT_PROTECTED,
                        "Target rejected forced movement"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_MOVEMENT_PROTECTED,
                        "Entity rejected forced teleportation"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_TYPE_UNSUPPORTED,
                        "Entity target is not magnetic"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_TYPE_UNSUPPORTED,
                        "Mounted entity displacement is not supported"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.TARGET_INVALID,
                        "Entity cannot be disassembled by this program"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.ACTION_CONDITION_FAILED,
                        "Kinetic shockwave was rejected"),
                () -> assertDiagnostic(
                        ProgramVmDiagnostic.ACTION_REJECTED,
                        "Unexpected world mutation")
        );
    }

    @Test
    void vmDiagnosticsProvideStableTranslationKeys() {
        assertEquals(
                "message.academy.program.execution.diagnostic.block_unbreakable",
                ProgramVmDiagnostic.BLOCK_UNBREAKABLE.translationKey()
        );
    }

    private static void assertDiagnostic(
            ProgramVmDiagnostic expected,
            String message
    ) {
        assertEquals(expected, AbilityProgramManager.actionDiagnostic(
                new IllegalStateException(message)));
    }

    private static AbilityProgram program(Identifier category) {
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
