package org.academy.internal.common.music;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkipVoteSessionTest {
    @Test
    void firstVoteCountedAtCreation() {
        var session = new SkipVoteSession(100L, 45 * 20L, UUID.randomUUID(), "Steve");
        assertEquals(1, session.voteCount());
        assertTrue(session.voterNames().contains("Steve"));
    }

    @Test
    void duplicateVotesRejected() {
        var uuid = UUID.randomUUID();
        var session = new SkipVoteSession(0L, 100L, uuid, "Steve");
        assertFalse(session.addVote(uuid, "Steve"));
        assertEquals(1, session.voteCount());
        assertTrue(session.addVote(UUID.randomUUID(), "Alex"));
        assertEquals(2, session.voteCount());
    }

    @Test
    void expiryFollowsWindow() {
        var session = new SkipVoteSession(1000L, 45 * 20L, UUID.randomUUID(), "Steve");
        assertFalse(session.isExpired(1000L + 45 * 20L - 1));
        assertTrue(session.isExpired(1000L + 45 * 20L));
        assertEquals(1000L + 45 * 20L, session.endsAtGameTime());
    }

    @Test
    void removeVoteDropsVoter() {
        var uuid = UUID.randomUUID();
        var session = new SkipVoteSession(0L, 100L, UUID.randomUUID(), "Steve");
        session.addVote(uuid, "Alex");
        assertTrue(session.removeVote(uuid));
        assertFalse(session.removeVote(uuid));
        assertEquals(1, session.voteCount());
    }
}
