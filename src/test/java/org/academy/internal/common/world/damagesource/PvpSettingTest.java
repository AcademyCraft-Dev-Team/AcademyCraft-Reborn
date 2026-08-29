package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
