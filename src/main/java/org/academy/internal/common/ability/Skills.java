package org.academy.internal.common.ability;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.accelerator.skills.lv1.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirStrike;
import org.academy.internal.common.ability.accelerator.skills.lv2.VectorAccel;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.skills.lv5.BloodflowReverse;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv5.BallLightning;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.ability.meltdowner.skills.HellFlare;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;

public final class Skills {
    public static final DeferredRegister<Skill> SKILLS = DeferredRegister.create(Registries.Keys.SKILLS, AcademyCraft.MOD_ID);
    /**
     * Accelerator
     */
    public static final DeferredHolder<Skill, VectorReflection> VECTOR_REFLECTION = SKILLS.register(SkillNames.VECTOR_REFLECTION, VectorReflection::new);
    public static final DeferredHolder<Skill, VectorAccel> VECTOR_ACCEL = SKILLS.register(SkillNames.VECTOR_ACCEL, VectorAccel::new);
    public static final DeferredHolder<Skill, StormWing> STORM_WING = SKILLS.register(SkillNames.STORM_WING, StormWing::new);
    public static final DeferredHolder<Skill, PlasmaGeneration> PLASMA_GENERATION = SKILLS.register(SkillNames.PLASMA_GENERATION, PlasmaGeneration::new);
    public static final DeferredHolder<Skill, KineticEnergyApplied> KINETIC_ENERGY_APPLIED = SKILLS.register(SkillNames.KINETIC_ENERGY_APPLIED, KineticEnergyApplied::new);
    public static final DeferredHolder<Skill, DirStrike> DIR_STRIKE = SKILLS.register(SkillNames.DIR_STRIKE, DirStrike::new);
    public static final DeferredHolder<Skill, BloodflowReverse> BLOODFLOW_REVERSE = SKILLS.register(SkillNames.BLOODFLOW_REVERSE, BloodflowReverse::new);
    /**
     * Electromaster
     */
    public static final DeferredHolder<Skill, ArcGenerate> ARC_GENERATE = SKILLS.register(SkillNames.ARC_GENERATE, ArcGenerate::new);
    public static final DeferredHolder<Skill, BallLightning> BALL_LIGHTNING = SKILLS.register(SkillNames.BALL_LIGHTNING, BallLightning::new);
    public static final DeferredHolder<Skill, MagnetManipulation> MAGNET_MANIPULATION = SKILLS.register(SkillNames.MAGNET_MANIPULATION, MagnetManipulation::new);
    public static final DeferredHolder<Skill, Railgun> RAILGUN = SKILLS.register(SkillNames.RAILGUN, Railgun::new);
    /**
     * Meltdowner
     */
    public static final DeferredHolder<Skill, SingleHighSpeedElectronBeam> SINGLE_HIGH_SPEED_ELECTRON_BEAM = SKILLS.register(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM, SingleHighSpeedElectronBeam::new);
    public static final DeferredHolder<Skill, HellFlare> HELL_FLARE = SKILLS.register(SkillNames.HELL_FLARE, HellFlare::new);
    /**
     * Teleport
     */
    public static final DeferredHolder<Skill, SelfTeleport> SELF_TELEPORT = SKILLS.register(SkillNames.SELF_TELEPORT, SelfTeleport::new);

    private Skills() {
    }
}
