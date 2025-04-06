package org.academy.internal.common.sounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.builtin.SkillNames;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftSoundEvents {
    public static final List<SoundEvent> SOUND_EVENT_LIST = new ArrayList<>();
    public static final SoundEvent COIN = SoundEvent.createVariableRangeEvent(new ResourceLocation(AcademyCraft.MOD_ID, "coin"));
    public static final SoundEvent RAILGUN = SoundEvent.createVariableRangeEvent(new ResourceLocation(AcademyCraft.MOD_ID, "railgun"));
    public static final SoundEvent ARC_WEAK = SoundEvent.createVariableRangeEvent(new ResourceLocation(AcademyCraft.MOD_ID, "arc_weak"));
    public static final SoundEvent VECTOR_REFLECTION = SoundEvent.createVariableRangeEvent(new ResourceLocation(AcademyCraft.MOD_ID, SkillNames.VECTOR_REFLECTION));

    static {
        SOUND_EVENT_LIST.add(COIN);
        SOUND_EVENT_LIST.add(RAILGUN);
        SOUND_EVENT_LIST.add(ARC_WEAK);
        SOUND_EVENT_LIST.add(VECTOR_REFLECTION);
    }

    private AcademyCraftSoundEvents() {
    }
}