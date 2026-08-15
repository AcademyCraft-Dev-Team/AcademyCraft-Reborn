package org.academy.internal.client.ability.program;

import com.mojang.blaze3d.platform.InputConstants;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.program.ProgramBookCodec;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityProgramEditorClientTest {
    @Test
    void nonMentaloutEditorUsesUnmodifiedBackslashKeyByDefault() {
        var key = AbilityProgramEditorClient.defaultOpenKey();

        assertEquals(InputSystem.InputType.KEYBOARD, key.type());
        assertEquals(java.util.Set.of(InputConstants.KEY_BACKSLASH), key.keys());
        assertEquals(InputConstants.PRESS, key.action());
        assertEquals(0, key.modifiers());
        assertFalse(key.availableWhenScreen());
        assertFalse(key.unbound());
    }

    @Test
    void oldEqualDefaultCanBeRecognizedForMigration() {
        assertEquals(java.util.Set.of(GLFW.GLFW_KEY_EQUAL),
                AbilityProgramEditorClient.legacyDefaultOpenKey().keys());
    }

    @Test
    void customSkillSlotsUseAltNumberExecutionKeys() {
        var keys = new int[]{
                InputConstants.KEY_1,
                InputConstants.KEY_2,
                InputConstants.KEY_3,
                InputConstants.KEY_4
        };
        for (var keyCode : keys) {
            var key = AbilityProgramEditorClient.defaultExecuteKey(keyCode);
            assertEquals(InputSystem.InputType.KEYBOARD, key.type());
            assertEquals(java.util.Set.of(keyCode), key.keys());
            assertEquals(InputConstants.PRESS, key.action());
            assertEquals(InputConstants.MOD_ALT, key.modifiers());
            assertFalse(key.availableWhenScreen());
            assertFalse(key.unbound());
        }
    }

    @Test
    void categoryRoutingExcludesPrecisionOperationAndUndevelopedPlayers() {
        assertTrue(AbilityProgramEditorClient.isSupportedCategoryId(
                AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR)));
        assertTrue(AbilityProgramEditorClient.isSupportedCategoryId(
                AcademyCraft.academy(AbilityCategoryNames.TELEPORT)));
        assertFalse(AbilityProgramEditorClient.isSupportedCategoryId(
                AcademyCraft.academy(AbilityCategoryNames.MENTALOUT)));
        assertFalse(AbilityProgramEditorClient.isSupportedCategoryId(
                AcademyCraft.academy(AbilityCategoryNames.LEVEL0)));
        assertFalse(AbilityProgramEditorClient.isSupportedCategoryId(
                AcademyCraft.academy("unknown_category")));
    }

    @Test
    void persistentBooksAreBoundToTheirAbilityCategory() {
        var accelerator = AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR);
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "Vector slot",
                accelerator,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );
        var book = new ProgramBook(
                ProgramBook.CURRENT_SCHEMA_VERSION,
                7,
                2,
                List.of(
                        new ProgramBook.Slot(program),
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY
                )
        );
        var encoded = Base64.getEncoder().encodeToString(ProgramBookCodec.encode(book));

        var decoded = AbilityProgramEditorClient.decodeBook(accelerator, encoded);

        assertEquals(7, decoded.revision());
        assertEquals(2, decoded.selectedSlot());
        assertEquals(program, decoded.slot(0).program());

        var wrongCategory = AbilityProgramEditorClient.decodeBook(
                AcademyCraft.academy(AbilityCategoryNames.TELEPORT), encoded);
        assertEquals(0, wrongCategory.revision());
        assertNull(wrongCategory.slot(0).program());
        assertTrue(AbilityProgramEditorClient.shouldImportCachedBook(
                ProgramBook.empty(AbilityProgramEditorClient.SLOT_COUNT), book));
        assertFalse(AbilityProgramEditorClient.shouldImportCachedBook(
                book, ProgramBook.empty(AbilityProgramEditorClient.SLOT_COUNT)));
    }

    @Test
    void corruptBookFallsBackWithoutAffectingOtherPlayerOrCategoryKeys() {
        var player = UUID.fromString("20000000-0000-0000-0000-000000000002");
        var accelerator = AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR);
        var teleport = AcademyCraft.academy(AbilityCategoryNames.TELEPORT);

        var decoded = AbilityProgramEditorClient.decodeBook(accelerator, "not base64");

        assertEquals(AbilityProgramEditorClient.SLOT_COUNT, decoded.slots().size());
        assertTrue(decoded.slots().stream().allMatch(ProgramBook.Slot::empty));
        assertFalse(AbilityProgramEditorClient.storageKey(player, accelerator)
                .equals(AbilityProgramEditorClient.storageKey(player, teleport)));
        assertFalse(AbilityProgramEditorClient.storageKey(player, accelerator)
                .equals(AbilityProgramEditorClient.storageKey(UUID.randomUUID(), accelerator)));
    }
}
