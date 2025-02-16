package org.academy.internal.common.sounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftSoundEvents {
    public static final List<SoundEvent> SOUND_EVENT_LIST = new ArrayList<>();
    public static final SoundEvent COIN = SoundEvent.createVariableRangeEvent(new ResourceLocation(AcademyCraft.MOD_ID,"coin"));

    static {
        SOUND_EVENT_LIST.add(COIN);
    }

    private AcademyCraftSoundEvents() {
    }
}