package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramChatHistoryTest {
    @Test
    void readsBoundedHistoryByDistanceAndUnicodeCodePoints() {
        var player = UUID.randomUUID();
        try {
            ProgramChatHistory.record(player, "先前消息");
            ProgramChatHistory.record(player, "守卫😀开始");

            assertEquals("守卫😀开始", ProgramChatHistory.read(player, 0));
            assertEquals("先前消息", ProgramChatHistory.read(player, 1));
            assertEquals("😀开", ProgramChatHistory.slice(
                    ProgramChatHistory.read(player, 0), 2, 2));
            assertNull(ProgramChatHistory.read(player, 2));
        } finally {
            ProgramChatHistory.clear(player);
        }
    }

    @Test
    void discardsOldMessagesAndTruncatesLongText() {
        var player = UUID.randomUUID();
        try {
            for (var index = 0; index <= ProgramChatHistory.MAX_MESSAGES; index++) {
                ProgramChatHistory.record(player, "message-" + index);
            }
            assertEquals("message-32", ProgramChatHistory.read(player, 0));
            assertEquals("message-1", ProgramChatHistory.read(player, 31));
            ProgramChatHistory.record(player, "😀".repeat(300));
            var newest = ProgramChatHistory.read(player, 0);
            assertNotNull(newest);
            assertEquals(ProgramChatHistory.MAX_CODE_POINTS,
                    newest.codePointCount(0, newest.length()));
        } finally {
            ProgramChatHistory.clear(player);
        }
    }
}
