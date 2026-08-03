package org.academy.internal.common.skilldata;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterCreationDataTest {
    @Test
    void addDeduplicatesAndRemoveCleansOwnerList() {
        var data = new DarkmatterCreationData();
        var uuid = UUID.randomUUID();
        data.add(uuid);
        data.add(uuid);
        assertEquals(1, data.getOwnedBeetles().size());
        data.remove(uuid);
        assertTrue(data.getOwnedBeetles().isEmpty());
    }
}
