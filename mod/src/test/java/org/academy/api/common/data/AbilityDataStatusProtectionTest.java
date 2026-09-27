package org.academy.api.common.data;

import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityDataStatusProtectionTest {
    @Test
    void rejectsForeignStatusAndTimerMutation() throws ReflectiveOperationException {
        var data = protectedData(AcademyCraft.class);

        AbilityData.class.getMethod("setStatus", AbilityData.Status.class)
                .invoke(data, AbilityData.Status.OVERLOAD);
        AbilityData.class.getMethod("setStateTimer", int.class).invoke(data, 200);
        AbilityData.class.getMethod("tickStateTimer").invoke(data);

        assertEquals(AbilityData.Status.NORMAL, data.getStatus());
        assertEquals(0, data.getStateTimer());
    }

    @Test
    void owningCodeSourceCanMaintainStatus() throws ReflectiveOperationException {
        var data = protectedData(getClass());

        data.setStatus(AbilityData.Status.OVERLOAD);
        data.setStateTimer(2);
        data.tickStateTimer();

        assertEquals(AbilityData.Status.OVERLOAD, data.getStatus());
        assertEquals(1, data.getStateTimer());
    }

    private static AbilityData protectedData(Class<?> owner) throws ReflectiveOperationException {
        var data = new AbilityData();
        Field field = AbilityData.class.getDeclaredField("statusOwner");
        field.setAccessible(true);
        field.set(data, owner);
        return data;
    }
}
