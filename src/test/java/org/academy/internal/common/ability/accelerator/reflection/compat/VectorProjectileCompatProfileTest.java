package org.academy.internal.common.ability.accelerator.reflection.compat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorProjectileCompatProfileTest {
    @Test
    void codecLoadsOwnerlessPeerCollisionGuard() {
        var profile = VectorProjectileCompatProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "entity_type": ["twilightforest:hydra_mortar"],
                          "suppress_ownerless_peer_collision": true
                        }
                        """)
        ).getOrThrow();

        assertEquals(List.of(Identifier.parse("twilightforest:hydra_mortar")),
                profile.entityTypes());
        assertTrue(profile.suppressOwnerlessPeerCollision());
    }

    @Test
    void emptyProfileDoesNotEnableAGlobalGuard() {
        var profile = VectorProjectileCompatProfile.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString("{}")
        ).getOrThrow();

        assertTrue(profile.entityTypes().isEmpty());
        assertFalse(profile.suppressOwnerlessPeerCollision());
    }
}
