package org.academy.internal.common.entitycontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityControlApiTest {
    private static final String[] GETTERS = {
            "getTrueHealth", "getRealHealth", "getCurrentHealth", "getHealth"
    };
    private static final String[] SETTERS = {
            "setTrueHealth", "setRealHealth", "setCurrentHealth", "setHealth"
    };
    private static final String[] FIELDS = {
            "trueHealth", "realHealth", "currentHealth", "health"
    };

    @Test
    void customFieldTakesPriorityOverInheritedVanillaGetter() {
        var target = new FieldBackedHealth();
        var accessor = EntityControlApi.NumericAccessor.resolve(
                target.getClass(), GETTERS, SETTERS, FIELDS
        );

        assertEquals(64.0f, accessor.read(target, Float.NaN));
        assertTrue(accessor.write(target, 18.0f));
        assertEquals(18.0f, accessor.read(target, Float.NaN));
        assertEquals(20.0f, target.getHealth());
    }

    @Test
    void customGetterFallsBackToMatchingFieldWhenNoSetterExists() {
        var target = new GetterAndFieldHealth();
        var accessor = EntityControlApi.NumericAccessor.resolve(
                target.getClass(), GETTERS, SETTERS, FIELDS
        );

        assertEquals(48.0f, accessor.read(target, Float.NaN));
        assertTrue(accessor.write(target, 12.0f));
        assertEquals(12.0f, target.getRealHealth());
        assertEquals(20.0f, target.getHealth());
    }

    private static class VanillaHealth {
        private float health = 20.0f;

        public float getHealth() {
            return health;
        }

        public void setHealth(float health) {
            this.health = health;
        }
    }

    private static final class FieldBackedHealth extends VanillaHealth {
        private final float trueHealth = 64.0f;
    }

    private static final class GetterAndFieldHealth extends VanillaHealth {
        private final float realHealth = 48.0f;

        public float getRealHealth() {
            return realHealth;
        }
    }
}
