package org.academy.internal.client.gui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterShapingScreenTest {
    @Test
    void modifierTooltipWidthStaysInsideSmallScreens() {
        assertEquals(180, DarkmatterShapingScreen.modifierTooltipTextWidth(640));
        assertEquals(96, DarkmatterShapingScreen.modifierTooltipTextWidth(120));
        assertEquals(1, DarkmatterShapingScreen.modifierTooltipTextWidth(20));
    }
}
