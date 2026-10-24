package org.academy.api.server.ability;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.AbilityActorProfile;
import org.academy.api.common.ability.AbilityActorState;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AbilityActorStateTest {
    private static final Identifier ARC = Identifier.parse("academy:arc_generate");
    private static AbilityActorState state() {
        return new AbilityActorState(new AbilityActorProfile(Identifier.parse("academy:test"),
                Identifier.parse("academy:electromaster"), 2, 100, 1, 1, List.of(ARC)));
    }

    @Test
    void timedOccupationExpiresAndMaintenanceCannotBeRecoveredAsFreeCp() {
        var account = new AbilityCpAccount(100);
        var maintenance = account.reserve(10, -1);
        assertNotNull(account.reserve(80, 2));
        assertNull(account.reserve(11, 2));
        assertEquals(10, account.current());
        account.tick();
        assertEquals(10, account.current());
        account.tick();
        assertEquals(90, account.current());
        assertEquals(0, account.recover(100));
        account.release(maintenance);
        assertEquals(100, account.current());
    }

    @Test
    void invalidReservationsDoNotMutateTheAccount() {
        var account = new AbilityCpAccount(100);
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 101})
            assertNull(account.reserve(value, 20));
        assertNull(account.reserve(1, 0));
        assertNull(account.reserve(1, -2));
        assertEquals(100, account.current());
    }

    @Test
    void starvationFreezesTimedOccupationButCooldownStillElapses() {
        var state = state();
        state.cp().reserve(10, 2);
        state.cast(ARC, 40);
        state.tick(false);
        assertEquals(90, state.cp().current());
        assertEquals(2, state.cp().occupations().getFirst().remainingTicks());
        assertEquals(39, state.cooldown(ARC));
    }

    @Test
    void reloadPreservesTimedCpCooldownsAndSwitchesButReleasesOrphanMaintenance() {
        var original = state();
        original.cp().reserve(10, -1);
        original.cp().reserve(20, 100);
        original.cast(ARC, 40);
        original.setEnabled(ARC, false);
        var json = AbilityActorState.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var restored = AbilityActorState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(80, restored.cp().current());
        assertEquals(40, restored.cooldown(ARC));
        assertEquals(20, restored.globalCooldown());
        assertFalse(restored.enabled(ARC));
        assertEquals(70, original.cp().current(), "Encoding must not release live maintenance");
    }

    @Test
    void oldRosterLoadsWithoutAbilityAndNewRosterRoundTripsStableIdentity() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 1234, MisakaPersonality.TIMID, 1);
        var legacy = MisakaSisterRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        assertNull(MisakaSisterRecord.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().ability);
        record.ability = state();
        record.ability.cp().reserve(30, 50);
        var saved = MisakaSisterRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        var loaded = MisakaSisterRecord.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(record.misakaUuid, loaded.misakaUuid);
        assertEquals(70, loaded.ability.cp().current());
    }
}
