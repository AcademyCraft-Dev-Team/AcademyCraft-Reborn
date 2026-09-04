package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramVector;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramDebugFormatterTest {
    @Test
    void templatesFormatDirectionsPositionsAndListsDeterministically() {
        assertEquals("direction=(1, 0, 0)", ProgramDebugFormatter.render(
                "direction={value}", new ProgramDirection(1.0, 0.0, 0.0)));
        assertEquals("vector=(3, 4, 0)", ProgramDebugFormatter.render(
                "vector={value}", new ProgramVector(3.0, 4.0, 0.0)));
        assertEquals("minecraft:overworld @ (1.25, 64, -2)",
                ProgramDebugFormatter.formatValue(new ProgramWorldPosition(
                        Identifier.parse("minecraft:overworld"), 1.25, 64.0, -2.0)));
        assertEquals("vectors=[(1, 0, 0), (0, 1, 0)]", ProgramDebugFormatter.render(
                "vectors={value}", List.of(
                        new ProgramDirection(1.0, 0.0, 0.0),
                        new ProgramDirection(0.0, 1.0, 0.0))));
    }

    @Test
    void outputIsSingleLineAndBounded() {
        var values = java.util.stream.IntStream.range(0, 40).boxed().toList();
        var formatted = ProgramDebugFormatter.render("line one\n{value}", values);

        assertTrue(formatted.startsWith("line one [0, 1, 2"));
        assertTrue(formatted.contains("… +8"));
        assertTrue(formatted.length() <= ProgramDebugFormatter.MAX_OUTPUT_LENGTH);
        assertTrue(formatted.chars().noneMatch(character -> character == '\n' || character == '\r'));
    }
}
