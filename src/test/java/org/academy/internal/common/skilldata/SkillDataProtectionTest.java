package org.academy.internal.common.skilldata;

import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDataProtectionTest {
    @Test
    void rejectsDirectMutationFromAnotherCodeSource() throws ReflectiveOperationException {
        var data = protectedData(AcademyCraft.class);

        SkillData.class.getMethod("setEnabled", boolean.class).invoke(data, false);
        SkillData.class.getMethod("toggleEnabled").invoke(data);
        var privateSetter = SkillData.class.getDeclaredMethod("setEnabledAndNotify", boolean.class);
        privateSetter.setAccessible(true);
        privateSetter.invoke(data, false);
        SkillData.class.getMethod("applyPersistedEnabled", boolean.class).invoke(data, false);

        assertTrue(data.isEnabled());
    }

    @Test
    void owningCodeSourceCanUpdateItsPersistentListener() throws ReflectiveOperationException {
        var listenerCalled = new AtomicBoolean();
        var data = protectedData(getClass(), _ -> listenerCalled.set(true));

        data.setEnabled(false);

        assertFalse(data.isEnabled());
        assertTrue(listenerCalled.get());
    }

    private static CommonSkillData protectedData(Class<?> owner) throws ReflectiveOperationException {
        return protectedData(owner, _ -> {
        });
    }

    private static CommonSkillData protectedData(
            Class<?> owner,
            Consumer<Boolean> listener
    ) throws ReflectiveOperationException {
        var data = new CommonSkillData();
        setField(data, "activationOwner", owner);
        setField(data, "activationStateListener", listener);
        return data;
    }

    private static void setField(SkillData data, String name, Object value)
            throws ReflectiveOperationException {
        Field field = SkillData.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(data, value);
    }
}
