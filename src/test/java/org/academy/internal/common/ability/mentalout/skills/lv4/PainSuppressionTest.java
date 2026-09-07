package org.academy.internal.common.ability.mentalout.skills.lv4;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PainSuppressionTest {
    @Test
    void damageCostUsesAnExclusiveThresholdAndPerHitCap() {
        assertEquals(0, PainSuppression.damageCpCost(0));
        assertEquals(0, PainSuppression.damageCpCost(2));
        assertEquals(Math.nextUp(2.0f), PainSuppression.damageCpCost(Math.nextUp(2.0f)));
        assertEquals(3.5f, PainSuppression.damageCpCost(3.5f));
        assertEquals(20, PainSuppression.damageCpCost(20));
        assertEquals(20, PainSuppression.damageCpCost(200));
        assertEquals(0, PainSuppression.damageCpCost(Float.NaN));
        assertEquals(0, PainSuppression.damageCpCost(Float.POSITIVE_INFINITY));
    }

    @Test
    void authoredNodeFitsTheVisiblePanelAndDoesNotOverlapOtherMentalNodes() throws Exception {
        var layout = Files.readString(Path.of("src/main/resources/assets/academy/gui/ability_developer_gui_layout.txt"));
        var mental = layout.split("\\[academy:mentalout\\]")[1].split("\\[")[0];
        assertTrue(mental.contains("academy:pain_suppression=112.0,110.0"));
        assertTrue(PainSuppression.NODE_X >= 0 && PainSuppression.NODE_X + 16 <= 257);
        assertTrue(PainSuppression.NODE_Y >= 0 && PainSuppression.NODE_Y + 16 <= 139);
        for (var line : mental.lines().filter(value -> value.startsWith("academy:")).toList()) {
            if (line.startsWith("academy:pain_suppression=")) continue;
            var coordinates = line.split("=")[1].split(",");
            var x = Float.parseFloat(coordinates[0]);
            var y = Float.parseFloat(coordinates[1]);
            assertTrue(Math.abs(x - PainSuppression.NODE_X) >= 25 || Math.abs(y - PainSuppression.NODE_Y) >= 25,
                    "New node overlaps " + line);
        }
    }
}
