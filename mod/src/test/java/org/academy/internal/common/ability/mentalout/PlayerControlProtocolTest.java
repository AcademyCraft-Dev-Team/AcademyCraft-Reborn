package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.academy.api.common.entitycontrol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PlayerControlProtocolTest {
    @Test
    void serverFallbackStartsWithoutSubjectHandshakeOrAfterAppliedFramesStall() {
        assertFalse(PlayerControlSessionManager.shouldUseServerFallback(false, 2L, 1L));
        assertTrue(PlayerControlSessionManager.shouldUseServerFallback(false, 3L, 1L));
        assertFalse(PlayerControlSessionManager.shouldUseServerFallback(true, 3L, 1L));
        assertTrue(PlayerControlSessionManager.shouldUseServerFallback(true, 4L, 1L));
    }

    @Test
    void finalIntrusionMilestoneExtendsPlayerDurationByHalf() {
        assertEquals(120, MentalIntrusionManager.scalePlayerIntrusionDuration(120, 2));
        assertEquals(180, MentalIntrusionManager.scalePlayerIntrusionDuration(120, 3));
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
    void verticalValidationHonorsMode() {
        var walking = new PlayerControlFrame(
                1, 0, 0, 0, false, false, false, false, false, PlayerMovementMode.WALK);
        var jumping = new PlayerControlFrame(
                1, 0, 0, 0, true, false, false, false, false, PlayerMovementMode.JUMP);
        var swimming = new PlayerControlFrame(
                1, 0, 0, 0, false, false, false, false, false, PlayerMovementMode.SWIM);
        assertFalse(PlayerControlSessionManager.validVertical(walking, 1.0));
        assertTrue(PlayerControlSessionManager.validVertical(jumping, 1.0));
        assertTrue(PlayerControlSessionManager.validVertical(swimming, 1.0));
        assertFalse(PlayerControlSessionManager.validVertical(swimming, 3.0));
    }

    @Test
    void selfControlRecognizesMovementAndActionOverrides() {
        assertFalse(PlayerControlSessionManager.hasSelfOverrideInput(0, 0));
        assertTrue(PlayerControlSessionManager.hasSelfOverrideInput(1, 0));
        assertTrue(PlayerControlSessionManager.hasSelfOverrideInput(0, 1));
        assertTrue(PlayerControlSessionManager.hasSelfOverrideInput(0, 4));
        assertTrue(PlayerControlSessionManager.hasSelfOverrideInput(0, 8));
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
    void takeoverInventoryActionCodecRoundTripsValidatedCommand() {
        var id = UUID.randomUUID();
        var expected = new PlayerControlSessionManager.InventoryActionPacket(
                id,
                8L,
                23L,
                PlayerControlSessionManager.InventoryAction.SELECT_HOTBAR,
                6
        );
        var buffer = Unpooled.buffer();
        try {
            PlayerControlSessionManager.InventoryActionPacket.CODEC.encode(buffer, expected);
            var decoded = PlayerControlSessionManager.InventoryActionPacket.CODEC.decode(buffer);
            assertEquals(id, decoded.sessionId());
            assertEquals(8L, decoded.revision());
            assertEquals(23L, decoded.sequence());
            assertEquals(PlayerControlSessionManager.InventoryAction.SELECT_HOTBAR,
                    decoded.action());
            assertEquals(6, decoded.value());
        } finally {
            buffer.release();
        }
    }

    @Test
    void targetViewCodecRoundTripsHotbarAndCombatState() {
        var registries = HolderLookup.Provider.create(Stream.empty());
        var hotbar = new ArrayList<ItemStack>();
        for (var slot = 0; slot < 9; slot++) hotbar.add(ItemStack.EMPTY);
        var state = new PlayerControlSessionManager.TargetViewState(
                hotbar, 2, ItemStack.EMPTY,
                13.5f, 30.0f, 4.0f, 11, 15, 2.0f,
                120, 300, 0.75f, 8, 0.4f,
                true, InteractionHand.OFF_HAND, 23
        );
        var packet = new PlayerControlSessionManager.TargetViewStatePacket(
                UUID.randomUUID(), 4L, 9L, state, registries
        );
        var buffer = Unpooled.buffer();
        try {
            PlayerControlSessionManager.TargetViewStatePacket.CODEC.encode(buffer, packet);
            var decoded = PlayerControlSessionManager.TargetViewStatePacket.CODEC.decode(buffer);
            var decodedState = decoded.decodeState(registries);
            assertEquals(packet.sessionId(), decoded.sessionId());
            assertEquals(4L, decoded.revision());
            assertEquals(9L, decoded.sequence());
            assertEquals(2, decodedState.selectedSlot());
            assertTrue(decodedState.selectedItem().isEmpty());
            assertTrue(decodedState.offhand().isEmpty());
            assertEquals(13.5f, decodedState.health());
            assertEquals(InteractionHand.OFF_HAND, decodedState.useHand());
        } finally {
            buffer.release();
        }
    }

    @Test
    void targetViewStateSanitizesNonFiniteModdedAttributes() {
        var hotbar = new ArrayList<ItemStack>();
        for (var slot = 0; slot < 9; slot++) hotbar.add(ItemStack.EMPTY);

        var state = new PlayerControlSessionManager.TargetViewState(
                hotbar, 0, ItemStack.EMPTY,
                Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                0, 20, Float.NaN,
                300, 300, Float.NaN, 0, Float.POSITIVE_INFINITY,
                false, InteractionHand.MAIN_HAND, 0
        );

        assertEquals(0.0f, state.health());
        assertEquals(20.0f, state.maxHealth());
        assertEquals(0.0f, state.absorption());
        assertEquals(0.0f, state.saturation());
        assertEquals(0.0f, state.experienceProgress());
        assertEquals(0.0f, state.attackStrength());
    }

    @Test
    void ordinaryPlayersHaveNoInputResistanceForNow() {
        assertFalse(PlayerControlSessionManager.isResistant(null));
        assertEquals(0L, PlayerControlSessionManager.resistanceUntil(null));
    }

    @Test
    void beginPacketPreservesSelfControlledRole() {
        var packet = new PlayerControlSessionManager.BeginPacket(
                UUID.randomUUID(), 12L, PlayerControlSessionManager.Role.SELF,
                27, UUID.randomUUID());
        var buffer = Unpooled.buffer();
        try {
            PlayerControlSessionManager.BeginPacket.CODEC.encode(buffer, packet);
            var decoded = PlayerControlSessionManager.BeginPacket.CODEC.decode(buffer);
            assertEquals(PlayerControlSessionManager.Role.SELF, decoded.role());
        } finally {
            buffer.release();
        }
    }
}
