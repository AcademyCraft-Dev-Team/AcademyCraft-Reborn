package org.academy.api.common.ability.program;

import com.google.gson.Gson;
import org.academy.api.server.world.level.storage.PlayerData;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramSharedVariablesTest {
    @Test
    void namedValuesCoexistAndCanBeClearedIndividuallyOrTogether() {
        var player = new PlayerData();
        var mode = ProgramSharedVariables.encode(new ProgramValue<>(ProgramValueTypes.TEXT, "守卫"));
        var count = ProgramSharedVariables.encode(new ProgramValue<>(ProgramValueTypes.INTEGER, 3));
        ProgramSharedVariables.validateName("模式");
        player.setProgramSharedVariable("academy:mentalout", "模式", mode);
        player.setProgramSharedVariable("academy:mentalout", "count", count);
        player.setProgramSharedVariable("academy:electromaster", "mode", count);

        assertEquals(2, player.getProgramSharedVariables("academy:mentalout").size());
        player.removeProgramSharedVariable("academy:mentalout", "模式");
        assertEquals(count, player.getProgramSharedVariables("academy:mentalout").get("count"));
        player.clearProgramSharedVariables("academy:mentalout");
        assertTrue(player.getProgramSharedVariables("academy:mentalout").isEmpty());
        assertEquals(count, player.getProgramSharedVariables("academy:electromaster").get("mode"));

        var restored = new Gson().fromJson(new Gson().toJson(player), PlayerData.class);
        assertEquals(count, restored.getProgramSharedVariables("academy:electromaster").get("mode"));
    }

    @Test
    void encodesSupportedTypedValuesAndRejectsOversizedText() {
        for (var value : new ProgramValue<?>[]{
                new ProgramValue<>(ProgramValueTypes.BOOLEAN, true),
                new ProgramValue<>(ProgramValueTypes.INTEGER, 42),
                new ProgramValue<>(ProgramValueTypes.BIG_INTEGER, new BigInteger("12345678901234567890")),
                new ProgramValue<>(ProgramValueTypes.FLOAT, 2.5),
                new ProgramValue<>(ProgramValueTypes.TEXT, "守卫😀")
        }) {
            assertEquals(value, ProgramSharedVariables.decode(ProgramSharedVariables.encode(value)));
        }
        assertThrows(IllegalArgumentException.class, () -> ProgramSharedVariables.encode(
                new ProgramValue<>(ProgramValueTypes.TEXT, "a".repeat(257))));
    }
}
