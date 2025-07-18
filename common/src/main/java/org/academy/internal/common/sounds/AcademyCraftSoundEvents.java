package org.academy.internal.common.sounds;

import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

import static org.academy.AcademyCraft.getResourceLocation;

public class AcademyCraftSoundEvents {
    public static final List<SoundEvent> SOUND_EVENT_LIST = new ArrayList<>();
    public static final SoundEvent COIN = SoundEvent.createVariableRangeEvent(getResourceLocation("coin"));
    public static final SoundEvent RAILGUN = SoundEvent.createVariableRangeEvent(getResourceLocation("railgun"));
    public static final SoundEvent ARC_WEAK = SoundEvent.createVariableRangeEvent(getResourceLocation("arc_weak"));
    public static final SoundEvent VECTOR_REFLECTION = SoundEvent.createVariableRangeEvent(getResourceLocation("vector_reflection"));
    public static final SoundEvent SELECT = SoundEvent.createVariableRangeEvent(getResourceLocation("select"));

    static {
        SOUND_EVENT_LIST.add(COIN);
        SOUND_EVENT_LIST.add(RAILGUN);
        SOUND_EVENT_LIST.add(ARC_WEAK);
        SOUND_EVENT_LIST.add(VECTOR_REFLECTION);
        SOUND_EVENT_LIST.add(SELECT);
    }

    private AcademyCraftSoundEvents() {
    }
}