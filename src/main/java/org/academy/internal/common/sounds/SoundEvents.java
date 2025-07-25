package org.academy.internal.common.sounds;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;
import static org.academy.AcademyCraft.getResourceLocation;

public class SoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> COIN = SOUND_EVENTS.register("coin",
            () -> SoundEvent.createVariableRangeEvent(getResourceLocation("coin")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN = SOUND_EVENTS.register("railgun",
            () -> SoundEvent.createVariableRangeEvent(getResourceLocation("railgun")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_WEAK = SOUND_EVENTS.register("arc_weak",
            () -> SoundEvent.createVariableRangeEvent(getResourceLocation("arc_weak")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VECTOR_REFLECTION = SOUND_EVENTS.register("vector_reflection",
            () -> SoundEvent.createVariableRangeEvent(getResourceLocation("vector_reflection")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SELECT = SOUND_EVENTS.register("select",
            () -> SoundEvent.createVariableRangeEvent(getResourceLocation("select")));

    private SoundEvents() {
    }
}