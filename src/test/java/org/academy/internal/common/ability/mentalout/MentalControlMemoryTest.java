package org.academy.internal.common.ability.mentalout;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MentalControlMemoryTest {
    @Test
    void ownershipTagsAreStableAndControllerSpecific() {
        var first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals(
                "academy.mentalout_controlled_by.00000000-0000-0000-0000-000000000001",
                MentalControlMemory.tag(first)
        );
        assertNotEquals(MentalControlMemory.tag(first), MentalControlMemory.tag(second));
    }
}
