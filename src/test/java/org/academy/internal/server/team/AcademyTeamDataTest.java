package org.academy.internal.server.team;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademyTeamDataTest {
    @Test
    void invitedPlayersBecomeTeammatesAfterAccepting() {
        var data = new AcademyTeamData();
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();

        assertEquals(AcademyTeamData.MutationResult.SUCCESS, data.create(owner, "Alpha-Team"));
        assertEquals(AcademyTeamData.MutationResult.SUCCESS, data.invite(owner, member));
        assertTrue(data.invitationsFor(member).contains("alpha-team"));
        assertEquals(AcademyTeamData.MutationResult.SUCCESS, data.accept(member, "ALPHA-TEAM"));
        assertTrue(data.areTeammates(owner, member));
    }

    @Test
    void ordinaryMembersCannotKickOrDisband() {
        var data = new AcademyTeamData();
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var other = UUID.randomUUID();
        data.create(owner, "test");
        data.invite(owner, member);
        data.accept(member, "test");

        assertEquals(AcademyTeamData.MutationResult.NOT_OWNER, data.kick(member, other));
        assertEquals(AcademyTeamData.MutationResult.NOT_OWNER, data.disband(member));
    }

    @Test
    void ownerLeavingDisbandsTheCustomTeam() {
        var data = new AcademyTeamData();
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        data.create(owner, "test");
        data.invite(owner, member);
        data.accept(member, "test");

        assertEquals(AcademyTeamData.MutationResult.TEAM_DISBANDED, data.leave(owner));
        assertFalse(data.areTeammates(owner, member));
        assertTrue(data.teamNames().isEmpty());
    }
}
