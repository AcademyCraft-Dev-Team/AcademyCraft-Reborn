package org.academy.internal.common.ability.mentalout;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentaloutRequestGuardTest {
    @Test
    void rejectsDuplicateOldAndNegativeSkillSequences() {
        var state = new MentaloutRequestGuard.SkillSequenceState();
        var skill = MentaloutRequestGuard.SkillUse.MENTAL_INTERVENTION;

        assertTrue(state.accept(skill, 7L));
        assertFalse(state.accept(skill, 7L));
        assertFalse(state.accept(skill, 6L));
        assertFalse(state.accept(skill, -1L));
        assertTrue(state.accept(skill, 8L));
    }

    @Test
    void tracksEachSkillIndependently() {
        var state = new MentaloutRequestGuard.SkillSequenceState();

        assertTrue(state.accept(MentaloutRequestGuard.SkillUse.MENTAL_INTERVENTION, 20L));
        assertTrue(state.accept(MentaloutRequestGuard.SkillUse.MENTAL_STUPOR, 3L));
        assertFalse(state.accept(MentaloutRequestGuard.SkillUse.MENTAL_INTERVENTION, 19L));
        assertTrue(state.accept(MentaloutRequestGuard.SkillUse.MENTAL_STUPOR, 4L));
    }

    @Test
    void acceptsSequenceWrapWithoutReacceptingDelayedPreWrapPacket() {
        var state = new MentaloutRequestGuard.SkillSequenceState();
        var skill = MentaloutRequestGuard.SkillUse.TARGET_MISIDENTIFICATION;

        assertTrue(state.accept(skill, Long.MAX_VALUE));
        assertTrue(state.accept(skill, 0L));
        assertTrue(state.accept(skill, 1L));
        assertFalse(state.accept(skill, Long.MAX_VALUE));
    }

    @Test
    void rejectsAmbiguousHalfRangeJump() {
        var state = new MentaloutRequestGuard.SkillSequenceState();
        var skill = MentaloutRequestGuard.SkillUse.MENTAL_STUPOR;

        assertTrue(state.accept(skill, 0L));
        assertFalse(state.accept(skill, 1L << 62));
    }

    @Test
    void freshSessionAcceptsRestartedClientSequence() {
        var oldSession = new MentaloutRequestGuard.SkillSequenceState();
        var newSession = new MentaloutRequestGuard.SkillSequenceState();
        var skill = MentaloutRequestGuard.SkillUse.IMPRESSION_MANIPULATION;

        assertTrue(oldSession.accept(skill, 100L));
        assertFalse(oldSession.accept(skill, 0L));
        assertTrue(newSession.accept(skill, 0L));
    }

    @Test
    void clientCounterWrapsAfterMaxValue() {
        var counter = new MentaloutRequestGuard.SequenceCounter(Long.MAX_VALUE - 1L);

        assertEquals(Long.MAX_VALUE - 1L, counter.next());
        assertEquals(Long.MAX_VALUE, counter.next());
        assertEquals(0L, counter.next());
        assertEquals(1L, counter.next());
    }

    @Test
    void rosterResyncUsesACooldownAndAcceptsAResetTickClock() {
        var gate = new MentaloutRequestGuard.TickCooldownGate(20L);

        assertTrue(gate.accept(40L));
        assertFalse(gate.accept(40L));
        assertFalse(gate.accept(41L));
        assertFalse(gate.accept(59L));
        assertTrue(gate.accept(60L));
        assertFalse(gate.accept(61L));
        assertTrue(gate.accept(5L));
    }

    @Test
    void rosterResyncCooldownMustBePositive() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new MentaloutRequestGuard.TickCooldownGate(0L)
        );
    }
}
