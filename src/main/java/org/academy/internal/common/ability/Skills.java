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
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.ability.meltdowner.skills.HellFlare;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticSuperposition;
import org.academy.internal.common.ability.electromaster.skills.lv1.PulseCharge;
import org.academy.internal.common.ability.meltdowner.skills.lv2.SpreadingBlast;
import org.academy.internal.common.ability.teleport.skills.lv1.MatterWarp;
import org.academy.internal.common.ability.accelerator.skills.lv1.FlowControl;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.electromaster.skills.lv1.ElectricalContact;
import org.academy.internal.common.ability.electromaster.skills.lv2.BioelectricSurge;
import org.academy.internal.common.ability.meltdowner.skills.lv1.TraceRing;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirectedShock;
import org.academy.internal.common.ability.accelerator.skills.lv3.HyperAccelerate;
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetMomentCharge;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ElectronBarrier;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.teleport.skills.lv1.ClipThrough;
import org.academy.internal.common.ability.teleport.skills.lv2.VisualTeleport;
import org.academy.internal.common.ability.electromaster.skills.lv2.LightningNova;
import org.academy.internal.common.ability.electromaster.skills.lv4.LightningStorm;
import org.academy.internal.common.ability.meltdowner.skills.lv3.Cloudroom;
import org.academy.internal.common.ability.meltdowner.skills.lv3.BetaParticleStream;
import org.academy.internal.common.ability.meltdowner.skills.lv3.HomingBlast;
import org.academy.internal.common.ability.meltdowner.skills.lv4.JetStrike;
import org.academy.internal.common.ability.teleport.skills.lv2.Disarm;
import org.academy.internal.common.ability.teleport.skills.lv3.Shackle;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.academy.internal.common.ability.meltdowner.skills.lv5.ChainFusion;
import org.academy.internal.common.ability.meltdowner.skills.lv5.Disintegrate;
import org.academy.internal.common.ability.teleport.skills.lv3.CoordinateTeleport;
import org.academy.internal.common.ability.teleport.skills.lv4.FlashBack;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialReplace;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;
import org.academy.internal.common.ability.level0.skills.Level0PassiveLv1;
import org.academy.internal.common.ability.level0.skills.Level0PassiveLv2;
import org.academy.internal.common.ability.level0.skills.Level0PassiveLv3;
import org.academy.internal.common.ability.level0.skills.Level0PassiveLv4;
import org.academy.internal.common.ability.level0.skills.Level0PassiveLv5;
import org.academy.internal.common.ability.teleport.skills.lv4.PhantomFalling;
import org.academy.internal.common.ability.teleport.skills.lv3.CutThrough;
import org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy;
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
    /**
     * Phase 1 - New Skills
     */
    public static final DeferredHolder<Skill, KineticSuperposition> KINETIC_SUPERPOSITION = SKILLS.register(SkillNames.KINETIC_SUPERPOSITION, KineticSuperposition::new);
    public static final DeferredHolder<Skill, PulseCharge> PULSE_CHARGE = SKILLS.register(SkillNames.PULSE_CHARGE, PulseCharge::new);
    public static final DeferredHolder<Skill, SpreadingBlast> SPREADING_BLAST = SKILLS.register(SkillNames.SPREADING_BLAST, SpreadingBlast::new);
    public static final DeferredHolder<Skill, MatterWarp> MATTER_WARP = SKILLS.register(SkillNames.MATTER_WARP, MatterWarp::new);
    /**
     * Phase 2 - Aura and Toggle Skills
     */
    public static final DeferredHolder<Skill, FlowControl> FLOW_CONTROL = SKILLS.register(SkillNames.FLOW_CONTROL, FlowControl::new);
    public static final DeferredHolder<Skill, ElectricalContact> ELECTRICAL_CONTACT = SKILLS.register(SkillNames.ELECTRICAL_CONTACT, ElectricalContact::new);
    public static final DeferredHolder<Skill, TraceRing> TRACE_RING = SKILLS.register(SkillNames.TRACE_RING, TraceRing::new);
    public static final DeferredHolder<Skill, VectorReduction> VECTOR_REDUCTION = SKILLS.register(SkillNames.VECTOR_REDUCTION, VectorReduction::new);
    public static final DeferredHolder<Skill, BioelectricSurge> BIOELECTRIC_SURGE = SKILLS.register(SkillNames.BIOELECTRIC_SURGE, BioelectricSurge::new);
    public static final DeferredHolder<Skill, SpatialSynergy> SPATIAL_SYNERGY = SKILLS.register(SkillNames.SPATIAL_SYNERGY, SpatialSynergy::new);
    /**
     * Phase 3 - Charged and Context Skills
     */
    public static final DeferredHolder<Skill, DirectedShock> DIRECTED_SHOCK = SKILLS.register(SkillNames.DIRECTED_SHOCK, DirectedShock::new);
    public static final DeferredHolder<Skill, HyperAccelerate> HYPER_ACCELERATE = SKILLS.register(SkillNames.HYPER_ACCELERATE, HyperAccelerate::new);
    public static final DeferredHolder<Skill, MagnetMomentCharge> MAGNET_MOMENT_CHARGE = SKILLS.register(SkillNames.MAGNET_MOMENT_CHARGE, MagnetMomentCharge::new);
    public static final DeferredHolder<Skill, ThunderLance> THUNDER_LANCE = SKILLS.register(SkillNames.THUNDER_LANCE, ThunderLance::new);
    public static final DeferredHolder<Skill, ElectronBarrier> ELECTRON_BARRIER = SKILLS.register(SkillNames.ELECTRON_BARRIER, ElectronBarrier::new);
    public static final DeferredHolder<Skill, MiningBeam> MINING_BEAM = SKILLS.register(SkillNames.MINING_BEAM, MiningBeam::new);
    public static final DeferredHolder<Skill, ClipThrough> CLIP_THROUGH = SKILLS.register(SkillNames.CLIP_THROUGH, ClipThrough::new);
    public static final DeferredHolder<Skill, VisualTeleport> VISUAL_TELEPORT = SKILLS.register(SkillNames.VISUAL_TELEPORT, VisualTeleport::new);
    public static final DeferredHolder<Skill, CutThrough> CUT_THROUGH = SKILLS.register(SkillNames.CUT_THROUGH, CutThrough::new);
    /**
     * Phase 4 - Complex Skills
     */
    public static final DeferredHolder<Skill, LightningNova> LIGHTNING_NOVA = SKILLS.register(SkillNames.LIGHTNING_NOVA, LightningNova::new);
    public static final DeferredHolder<Skill, Cloudroom> CLOUDROOM = SKILLS.register(SkillNames.CLOUDROOM, Cloudroom::new);
    public static final DeferredHolder<Skill, BetaParticleStream> BETA_PARTICLE_STREAM = SKILLS.register(SkillNames.BETA_PARTICLE_STREAM, BetaParticleStream::new);
    public static final DeferredHolder<Skill, HomingBlast> HOMING_BLAST = SKILLS.register(SkillNames.HOMING_BLAST, HomingBlast::new);
    public static final DeferredHolder<Skill, JetStrike> JET_STRIKE = SKILLS.register(SkillNames.JET_STRIKE, JetStrike::new);
    public static final DeferredHolder<Skill, LightningStorm> LIGHTNING_STORM = SKILLS.register(SkillNames.LIGHTNING_STORM, LightningStorm::new);
    public static final DeferredHolder<Skill, Disarm> DISARM = SKILLS.register(SkillNames.DISARM, Disarm::new);
    public static final DeferredHolder<Skill, Shackle> SHACKLE = SKILLS.register(SkillNames.SHACKLE, Shackle::new);
    public static final DeferredHolder<Skill, PhantomFalling> PHANTOM_FALLING = SKILLS.register(SkillNames.PHANTOM_FALLING, PhantomFalling::new);
    /**
     * Phase 5 - Ultimate and Signature Skills
     */
    public static final DeferredHolder<Skill, IronSandArsenal> IRON_SAND_ARSENAL = SKILLS.register(SkillNames.IRON_SAND_ARSENAL, IronSandArsenal::new);
    public static final DeferredHolder<Skill, MagneticWeapon> MAGNETIC_WEAPON = SKILLS.register(SkillNames.MAGNETIC_WEAPON, MagneticWeapon::new);
    public static final DeferredHolder<Skill, Thunderclap> THUNDERCLAP = SKILLS.register(SkillNames.THUNDERCLAP, Thunderclap::new);
    public static final DeferredHolder<Skill, ChainFusion> CHAIN_FUSION = SKILLS.register(SkillNames.CHAIN_FUSION, ChainFusion::new);
    public static final DeferredHolder<Skill, Disintegrate> DISINTEGRATE = SKILLS.register(SkillNames.DISINTEGRATE, Disintegrate::new);
    public static final DeferredHolder<Skill, FlashBack> FLASH_BACK = SKILLS.register(SkillNames.FLASH_BACK, FlashBack::new);
    public static final DeferredHolder<Skill, CoordinateTeleport> COORDINATE_TELEPORT = SKILLS.register(SkillNames.COORDINATE_TELEPORT, CoordinateTeleport::new);
    public static final DeferredHolder<Skill, SpacialReplace> SPACIAL_REPLACE = SKILLS.register(SkillNames.SPACIAL_REPLACE, SpacialReplace::new);
    public static final DeferredHolder<Skill, SpacialExcision> SPACIAL_EXCISION = SKILLS.register(SkillNames.SPACIAL_EXCISION, SpacialExcision::new);
    /**
     * Level0 - Common Passive Skills
     */
    public static final DeferredHolder<Skill, Level0PassiveLv1> LEVEL0_PASSIVE_LV1 = SKILLS.register(SkillNames.LEVEL0_PASSIVE_LV1, Level0PassiveLv1::new);
    public static final DeferredHolder<Skill, Level0PassiveLv2> LEVEL0_PASSIVE_LV2 = SKILLS.register(SkillNames.LEVEL0_PASSIVE_LV2, Level0PassiveLv2::new);
    public static final DeferredHolder<Skill, Level0PassiveLv3> LEVEL0_PASSIVE_LV3 = SKILLS.register(SkillNames.LEVEL0_PASSIVE_LV3, Level0PassiveLv3::new);
    public static final DeferredHolder<Skill, Level0PassiveLv4> LEVEL0_PASSIVE_LV4 = SKILLS.register(SkillNames.LEVEL0_PASSIVE_LV4, Level0PassiveLv4::new);
    public static final DeferredHolder<Skill, Level0PassiveLv5> LEVEL0_PASSIVE_LV5 = SKILLS.register(SkillNames.LEVEL0_PASSIVE_LV5, Level0PassiveLv5::new);

    private Skills() {
    }
}
