package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PvpSettingTest {
    @Test
    void enabledPlayersCanInteract() {
        assertEquals(
                PvpSetting.ProtectionReason.NONE,
                PvpSetting.protectionReason(true, false, true, true)
        );
    }

    @Test
    void disabledAttackerTakesFeedbackPriority() {
        assertEquals(
                PvpSetting.ProtectionReason.ATTACKER_DISABLED,
                PvpSetting.protectionReason(true, false, false, false)
        );
    }

    @Test
    void disabledTargetIsProtectedFromEnabledAttacker() {
        assertEquals(
                PvpSetting.ProtectionReason.TARGET_DISABLED,
                PvpSetting.protectionReason(true, false, true, false)
        );
    }

    @Test
    void nonPlayerAndSelfTargetsRemainAvailable() {
        assertEquals(
                PvpSetting.ProtectionReason.NONE,
                PvpSetting.protectionReason(false, false, false, false)
        );
        assertEquals(
                PvpSetting.ProtectionReason.NONE,
                PvpSetting.protectionReason(true, true, false, false)
        );
    }

    @Test
    void cooldownBlocksOnlyRealStateChanges() {
        assertEquals(
                PvpSetting.ChangeResult.COOLDOWN,
                PvpSetting.changeResult(true, false, PvpSetting.SWITCH_COOLDOWN_TICKS)
        );
        assertEquals(
                PvpSetting.ChangeResult.UNCHANGED,
                PvpSetting.changeResult(true, true, PvpSetting.SWITCH_COOLDOWN_TICKS)
        );
        assertEquals(
                PvpSetting.ChangeResult.APPLIED,
                PvpSetting.changeResult(true, false, 0)
        );
    }

    @Test
    void onlyAppliedSkillDamageToAnotherPlayerStartsCooldown() {
        assertTrue(PvpSetting.shouldStartCooldown(true, false, 1.0f));
        assertFalse(PvpSetting.shouldStartCooldown(false, false, 1.0f));
        assertFalse(PvpSetting.shouldStartCooldown(true, true, 1.0f));
        assertFalse(PvpSetting.shouldStartCooldown(true, false, 0.0f));
        assertFalse(PvpSetting.shouldStartCooldown(true, false, Float.NaN));
    }
}
