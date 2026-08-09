package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.Unpooled;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlDomain;
import org.academy.api.common.entitycontrol.PlayerControlFrame;
import org.academy.api.common.entitycontrol.PlayerMovementMode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerControlProtocolTest {
    @Test
    void activeTakeoverDoesNotExpireOnlyBecauseClientFramesAreSilent() {
        assertFalse(PlayerControlSessionManager.shouldEndForMissingAppliedFrame(
                false, 120L, 0L));
        assertTrue(PlayerControlSessionManager.shouldEndForMissingAppliedFrame(
                true, 20L, 0L));
    }

    @Test
    void directControlOwnsMovementViewAndActionWhileViewIsIndependent() {
        assertEquals(
                Set.of(ControlDomain.MOVEMENT, ControlDomain.VIEW, ControlDomain.ACTION),
                new ControlDirective.DirectControl().domains()
        );
        assertEquals(Set.of(ControlDomain.VIEW), ControlCapability.VIEW_CONTROL.domains());
    }

    @Test
    void playerFrameRejectsNonFiniteValuesAndClampsAxesAndPitch() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerControlFrame(
                Float.NaN, 0.0f, 0.0f, 0.0f,
                false, false, false, false, false, PlayerMovementMode.WALK));

        var frame = new PlayerControlFrame(
                4.0f, -2.0f, 45.0f, 120.0f,
                true, false, true, false, false, PlayerMovementMode.JUMP);
        assertEquals(1.0f, frame.forward());
        assertEquals(-1.0f, frame.strafe());
        assertEquals(90.0f, frame.pitch());
    }

    @Test
    void serverPlannedMovementModeSurvivesPathFrameNormalization() {
        for (var mode : Set.of(
                PlayerMovementMode.JUMP,
                PlayerMovementMode.SWIM,
                PlayerMovementMode.FLY
        )) {
            var frame = new PlayerControlFrame(
                    1.0f, 0.0f, 450.0f, -25.0f,
                    mode != PlayerMovementMode.FLY,
                    false, true, false, false, mode
            );
            var normalized = PlayerControlSessionManager.normalizePathFrame(frame);
            assertEquals(mode, normalized.mode());
            assertEquals(90.0f, normalized.yaw());
            assertEquals(frame.jump(), normalized.jump());
        }
    }

    @Test
    void intentCodecRoundTripsQuantizedFrameAndSessionEnvelope() {
        var id = UUID.randomUUID();
        var expected = new PlayerControlSessionManager.IntentPacket(
                id,
                12L,
                31L,
                new PlayerControlFrame(
                        0.5f, -0.25f, 137.25f, -32.5f,
                        true, true, false, true, false, PlayerMovementMode.SWIM
                )
        );
        var buffer = Unpooled.buffer();
        try {
            PlayerControlSessionManager.IntentPacket.CODEC.encode(buffer, expected);
            var decoded = PlayerControlSessionManager.IntentPacket.CODEC.decode(buffer);
            assertEquals(id, decoded.sessionId());
            assertEquals(12L, decoded.revision());
            assertEquals(31L, decoded.sequence());
            assertEquals(expected.frame().forward(), decoded.frame().forward(), 1.0f / 127.0f);
            assertEquals(expected.frame().strafe(), decoded.frame().strafe(), 1.0f / 127.0f);
            assertEquals(expected.frame().yaw(), decoded.frame().yaw(), 0.011f);
            assertEquals(expected.frame().pitch(), decoded.frame().pitch(), 0.011f);
            assertEquals(expected.frame().mode(), decoded.frame().mode());
            assertEquals(expected.frame().attack(), decoded.frame().attack());
        } finally {
            buffer.release();
        }
    }

    @Test
    void struggleAddsAtMostTwoPointsAndVerticalValidationHonorsMode() {
        assertEquals(0, PlayerControlSessionManager.strugglePoints(1, 1, 0));
        assertEquals(1, PlayerControlSessionManager.strugglePoints(1, 2, 0));
        assertEquals(2, PlayerControlSessionManager.strugglePoints(1, 2, 3));

        var walking = new PlayerControlFrame(
                1, 0, 0, 0, false, false, false, false, false, PlayerMovementMode.WALK);
        var jumping = new PlayerControlFrame(
                1, 0, 0, 0, true, false, false, false, false, PlayerMovementMode.JUMP);
        var swimming = new PlayerControlFrame(
                1, 0, 0, 0, false, false, false, false, false, PlayerMovementMode.SWIM);
        assertEquals(false, PlayerControlSessionManager.validVertical(walking, 1.0));
        assertEquals(true, PlayerControlSessionManager.validVertical(jumping, 1.0));
        assertEquals(true, PlayerControlSessionManager.validVertical(swimming, 1.0));
        assertEquals(false, PlayerControlSessionManager.validVertical(swimming, 3.0));
    }

    @Test
    void appliedFrameCodecRoundTripsEnvelope() {
        var id = UUID.randomUUID();
        var expected = new PlayerControlSessionManager.AppliedFramePacket(id, 7L, 19L);
        var buffer = Unpooled.buffer();
        try {
            PlayerControlSessionManager.AppliedFramePacket.CODEC.encode(buffer, expected);
            var decoded = PlayerControlSessionManager.AppliedFramePacket.CODEC.decode(buffer);
            assertEquals(id, decoded.sessionId());
            assertEquals(7L, decoded.revision());
            assertEquals(19L, decoded.sequence());
        } finally {
            buffer.release();
        }
    }

    @Test
    void targetViewCodecRoundTripsHotbarAndCombatState() {
        var hotbar = new java.util.ArrayList<ItemStack>();
        for (var slot = 0; slot < 9; slot++) hotbar.add(ItemStack.EMPTY);
        var state = new PlayerControlSessionManager.TargetViewState(
                hotbar, 2, ItemStack.EMPTY,
                13.5f, 30.0f, 4.0f, 11, 15, 2.0f,
                120, 300, 0.75f, 8, 0.4f,
                true, InteractionHand.OFF_HAND, 23
        );
        var packet = new PlayerControlSessionManager.TargetViewStatePacket(
                UUID.randomUUID(), 4L, 9L, state
        );
        var buffer = Unpooled.buffer();
        try {
            PlayerControlSessionManager.TargetViewStatePacket.CODEC.encode(buffer, packet);
            var decoded = PlayerControlSessionManager.TargetViewStatePacket.CODEC.decode(buffer);
            assertEquals(packet.sessionId(), decoded.sessionId());
            assertEquals(4L, decoded.revision());
            assertEquals(9L, decoded.sequence());
            assertEquals(2, decoded.state().selectedSlot());
            assertEquals(true, decoded.state().selectedItem().isEmpty());
            assertEquals(true, decoded.state().offhand().isEmpty());
            assertEquals(13.5f, decoded.state().health());
            assertEquals(InteractionHand.OFF_HAND, decoded.state().useHand());
        } finally {
            buffer.release();
        }
    }

    @Test
    void ordinaryPlayersHaveNoInputResistanceForNow() {
        assertEquals(false, PlayerControlSessionManager.isResistant(null));
        assertEquals(0L, PlayerControlSessionManager.resistanceUntil(null));
    }
}
