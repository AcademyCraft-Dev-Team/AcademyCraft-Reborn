package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTextOperationsTest {
    private static final ProgramWorldPosition ORIGIN = new ProgramWorldPosition(
            Identifier.parse("minecraft:the_nether"), 10, 64, -20);

    @Test
    void acceptsDocumentedTriplesAndRejectsPartialOrNonFiniteNumbers() {
        for (var text : List.of("10 64 -20", "10,64,-20", "(10, 64, -20)", "[10 64 -20]",
                "（10，64，-20）", "x=10 y=64 z=-20", "X:10, Y:64, Z:-20", "1e1 +64 -2e1")) {
            assertEquals(new ProgramVector(10, 64, -20), coordinates(text).resolve(null, false).orElseThrow(), text);
        }
        assertEquals(new ProgramVector(.5, -.25, 1), coordinates(".5 -.25 1.").resolve(null, false).orElseThrow());
        for (var text : List.of("", "1 2", "1 2 3 4", "1,2,3,4", "(1 2 3]", "NaN 1 2",
                "Infinity 1 2", "1e309 1 2", "1e 1 2", "^1 ~2 ^3", "1.2.3 4 5", "v1 2 3")) {
            assertTrue(ProgramTextOperations.parseCoordinates(text, false, 0).isEmpty(), text);
        }
        assertTrue(ProgramTextOperations.parseCoordinates("a".repeat(4097), true, 0).isEmpty());
        assertTrue(ProgramTextOperations.parseCoordinates("1 ".repeat(2048), true, 0).isEmpty());
    }

    @Test
    void extractsWholeExpressionsWithoutSalvagingMalformedTriples() {
        var text = "目标(1 2 3)，下一处 x=4 y=5 z=6；然后 -7 8 9！";
        assertEquals(new ProgramVector(4, 5, 6), ProgramTextOperations.parseCoordinates(text, true, 1)
                .orElseThrow().resolve(null, false).orElseThrow());
        assertEquals(new ProgramVector(-7, 8, 9), ProgramTextOperations.parseCoordinates(text, true, 2)
                .orElseThrow().resolve(null, false).orElseThrow());
        assertTrue(ProgramTextOperations.parseCoordinates(text, true, 3).isEmpty());
        assertTrue(ProgramTextOperations.parseCoordinates(text, true, -1).isEmpty());
        for (var invalid : List.of("目标 (1 2 3]", "坐标 1 2 3 4", "(1e309 2 3)", "abc1 2 3def")) {
            assertTrue(ProgramTextOperations.parseCoordinates(invalid, true, 0).isEmpty(), invalid);
        }
    }

    @Test
    void resolvesRelativeAxesInTheReferenceDimensionAndLocalAxesFromFeet() {
        var south = new ProgramTextOperations.Reference(ORIGIN, 0, 0);
        assertVector(10, 65, -20, coordinates("~ ~1 ~").resolve(south, true).orElseThrow());
        assertVector(9, 80, -18, coordinates("~-1 80 ~2").resolve(south, true).orElseThrow());
        assertVector(12, 67, -15, coordinates("^2 ^3 ^5").resolve(south, true).orElseThrow());
        assertVector(2, 3, 5, coordinates("^2 ^3 ^5").resolve(south, false).orElseThrow());
        assertVector(5, 64, -20, coordinates("^ ^ ^5")
                .resolve(new ProgramTextOperations.Reference(ORIGIN, 90, 0), true).orElseThrow());
        assertVector(10, 69, -20, coordinates("^ ^ ^5")
                .resolve(new ProgramTextOperations.Reference(ORIGIN, 30, -90), true).orElseThrow());
        assertTrue(coordinates("~ ~1 ~").resolve(south, false).isEmpty());
        assertTrue(coordinates("^ ^ ^1").resolve(null, true).isEmpty());
    }

    @Test
    void splitsSentencesWithoutBreakingDecimalCoordinatesAndHandlesUnicode() {
        assertEquals(List.of("目标 1.5 64 -2.25", "开始", "结束", "下一行"),
                ProgramTextOperations.split("目标 1.5 64 -2.25。开始！？结束;\n下一行",
                        ProgramTextOperations.SplitMode.SENTENCE, "", true, true));
        assertEquals(List.of("A", "😀", "B"), ProgramTextOperations.split(" A\t😀　B ",
                ProgramTextOperations.SplitMode.WHITESPACE, "", true, true));
        assertEquals(List.of("a", "b", "c", ""), ProgramTextOperations.split("a\r\nb\rc\n",
                ProgramTextOperations.SplitMode.LINE, "", false, false));
        assertEquals(List.of("", "a", "", "b", ""), ProgramTextOperations.split(".*a.*.*b.*",
                ProgramTextOperations.SplitMode.DELIMITER, ".*", false, false));
        assertEquals(List.of(" a ", " b "), ProgramTextOperations.split(" a | b ",
                ProgramTextOperations.SplitMode.DELIMITER, "|", false, true));
        assertThrows(IllegalArgumentException.class, () -> ProgramTextOperations.split("text",
                ProgramTextOperations.SplitMode.DELIMITER, "", true, true));
    }

    private static ProgramTextOperations.Coordinates coordinates(String text) {
        return ProgramTextOperations.parseCoordinates(text, false, 0).orElseThrow();
    }

    private static void assertVector(double x, double y, double z, ProgramVector actual) {
        assertEquals(x, actual.x(), 1e-9);
        assertEquals(y, actual.y(), 1e-9);
        assertEquals(z, actual.z(), 1e-9);
    }
}
