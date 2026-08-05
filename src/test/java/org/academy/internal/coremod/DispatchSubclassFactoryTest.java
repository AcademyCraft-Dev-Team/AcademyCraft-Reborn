package org.academy.internal.coremod;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchSubclassFactoryTest {
    @Test
    void generatesFieldFreeSubclassForCustomPlayerLayout() {
        var result = DispatchSubclassFactory.forPlayerType(CustomServerPlayer.class);
        assertTrue(result.successful(), result.failureReason());
        assertSame(CustomServerPlayer.class, result.dispatchType().getSuperclass());
        assertTrue(HotSpotClassPointerAccess.hasNoInstanceFields(result.dispatchType()));
        assertSame(result.dispatchType(),
                DispatchSubclassFactory.forPlayerType(CustomServerPlayer.class).dispatchType());
    }

    @Test
    void rejectsFinalPlayerTypeBeforePointerWrite() {
        var result = DispatchSubclassFactory.forPlayerType(FinalServerPlayer.class);
        assertFalse(result.successful());
        assertTrue(result.failureReason().contains("final"));
    }

    @Test
    void rejectsFinalProtectedOverrideBeforePointerWrite() {
        var result = DispatchSubclassFactory.forPlayerType(FinalHealthServerPlayer.class);
        assertFalse(result.successful());
        assertTrue(result.failureReason().contains("final"));
    }

    public static class CustomServerPlayer extends ServerPlayer {
        int customNumber;
        Object customReference;

        public CustomServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                  ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }
    }

    public static final class FinalServerPlayer extends ServerPlayer {
        public FinalServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                 ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }
    }

    public static class FinalHealthServerPlayer extends ServerPlayer {
        public FinalHealthServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                       ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public final float getHealth() {
            return super.getHealth();
        }
    }
}
