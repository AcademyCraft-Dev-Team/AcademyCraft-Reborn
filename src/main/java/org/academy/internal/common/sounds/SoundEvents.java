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
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGNET_MOVE_LOOP = SOUND_EVENTS.register("magnet_move_loop",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("magnet_move_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VECTOR_REFLECTION = SOUND_EVENTS.register("vector_reflection",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("vector_reflection")));
    public static final DeferredHolder<SoundEvent, SoundEvent> KINETIC_SHOCKWAVE = SOUND_EVENTS.register("kinetic_shockwave",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("kinetic_shockwave")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DIR_STRIKE = SOUND_EVENTS.register("dir_strike",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("dir_strike")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VECTOR_ACCEL = SOUND_EVENTS.register("vector_accel",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("vector_accel")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLASMA_GENERATION = SOUND_EVENTS.register("plasma_generation",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("plasma_generation")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLASMA_GENERATION_BOOM = SOUND_EVENTS.register("plasma_generation_boom",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("plasma_generation_boom")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOODFLOW_REVERSE = SOUND_EVENTS.register("bloodflow_reverse",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("bloodflow_reverse")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_SHIELD_STARTUP = SOUND_EVENTS.register("light_shield_startup",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("light_shield_startup")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_SHIELD_LOOP = SOUND_EVENTS.register("light_shield_loop",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("light_shield_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGLE_HIGH_SPEED_ELECTRON_BEAM = SOUND_EVENTS.register("single_high_speed_electron_beam",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("single_high_speed_electron_beam")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SCATTER_BOMB_CHARGE = SOUND_EVENTS.register("scatter_bomb_charge",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("scatter_bomb_charge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SELECT = SOUND_EVENTS.register("select",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("select")));
    public static final DeferredHolder<SoundEvent, SoundEvent> THREATENING_TELEPORT = SOUND_EVENTS.register("threatening_teleport",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("threatening_teleport")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_TELEPORT = SOUND_EVENTS.register("self_teleport",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("self_teleport")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_RIPPING = SOUND_EVENTS.register("flesh_ripping",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("flesh_ripping")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PENETRATE_TELEPORT = SOUND_EVENTS.register("penetrate_teleport",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("penetrate_teleport")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHING = SOUND_EVENTS.register("flashing",
            () -> SoundEvent.createVariableRangeEvent(AcademyCraft.academy("flashing")));

    private SoundEvents() {
    }
}
