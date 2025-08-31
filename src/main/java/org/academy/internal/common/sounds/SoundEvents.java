package org.academy.internal.common.sounds;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;

import static org.academy.AcademyCraft.MODID;

public class SoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> COIN = SOUND_EVENTS.register("coin",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("coin")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN = SOUND_EVENTS.register("railgun",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("railgun")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_WEAK = SOUND_EVENTS.register("arc_weak",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("arc_weak")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VECTOR_REFLECTION = SOUND_EVENTS.register("vector_reflection",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("vector_reflection")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SELECT = SOUND_EVENTS.register("select",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("select")));

    private SoundEvents() {
    }
}