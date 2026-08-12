package org.academy.internal.common.ability;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorAccel;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirStrike;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.skills.lv5.*;
import org.academy.internal.common.ability.aeromanip.skills.lv1.AirCushion;
import org.academy.internal.common.ability.aeromanip.skills.lv1.AirflowJet;
import org.academy.internal.common.ability.aeromanip.skills.lv1.FlowSense;
import org.academy.internal.common.ability.aeromanip.skills.lv2.BreathingFilm;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;
import org.academy.internal.common.ability.aeromanip.skills.lv2.TailwindField;
import org.academy.internal.common.ability.aeromanip.skills.lv3.AtmosphereShield;
import org.academy.internal.common.ability.aeromanip.skills.lv3.LaminarCutter;
import org.academy.internal.common.ability.aeromanip.skills.lv3.VortexPull;
import org.academy.internal.common.ability.aeromanip.skills.lv4.AtmosphereBlastGun;
import org.academy.internal.common.ability.aeromanip.skills.lv4.PressureLock;
import org.academy.internal.common.ability.aeromanip.skills.lv4.WindCorridor;
import org.academy.internal.common.ability.aeromanip.skills.lv5.AtmosphericDominion;
import org.academy.internal.common.ability.aeromanip.skills.lv5.Flight;
import org.academy.internal.common.ability.aeromanip.skills.lv5.VacuumDomain;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.lv3.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterRepair;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv1.ElectricalContact;
import org.academy.internal.common.ability.electromaster.skills.lv2.LightningNova;
import org.academy.internal.common.ability.electromaster.skills.lv2.ThunderLance;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentRecharge;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentSymbiosis;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv3.MineDetect;
import org.academy.internal.common.ability.electromaster.skills.lv4.BioelectricOperation;
import org.academy.internal.common.ability.electromaster.skills.lv4.ElectromagneticShield;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.ability.electromaster.skills.lv4.Railgun;
import org.academy.internal.common.ability.electromaster.skills.lv5.BallLightning;
import org.academy.internal.common.ability.electromaster.skills.lv5.LightningStorm;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.academy.internal.common.ability.level0.skills.*;
import org.academy.internal.common.ability.meltdowner.skills.lv1.RadiationIntensify;
import org.academy.internal.common.ability.meltdowner.skills.lv1.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ScatterBomb;
import org.academy.internal.common.ability.meltdowner.skills.lv3.Cloudroom;
import org.academy.internal.common.ability.meltdowner.skills.lv3.LightShield;
import org.academy.internal.common.ability.meltdowner.skills.lv4.JetStrike;
import org.academy.internal.common.ability.meltdowner.skills.lv4.ParticleWaveCannon;
import org.academy.internal.common.ability.meltdowner.skills.lv5.AutoCruiseBeamCannon;
import org.academy.internal.common.ability.meltdowner.skills.lv5.Disintegrate;
import org.academy.internal.common.ability.mentalout.skills.lv1.MentalIntervention;
import org.academy.internal.common.ability.mentalout.skills.lv1.MentalIntrusion;
import org.academy.internal.common.ability.mentalout.skills.lv1.TargetMisidentification;
import org.academy.internal.common.ability.mentalout.skills.lv2.MentalStupor;
import org.academy.internal.common.ability.mentalout.skills.lv2.SensoryDistortion;
import org.academy.internal.common.ability.mentalout.skills.lv3.CommandPositioning;
import org.academy.internal.common.ability.mentalout.skills.lv3.ImpressionManipulation;
import org.academy.internal.common.ability.mentalout.skills.lv4.MentalTakeover;
import org.academy.internal.common.ability.mentalout.skills.lv5.PrecisionOperation;
import org.academy.internal.common.ability.teleport.skills.lv1.SpaceFoldingTheorem;
import org.academy.internal.common.ability.teleport.skills.lv1.ThreateningTeleport;
import org.academy.internal.common.ability.teleport.skills.lv2.Disarm;
import org.academy.internal.common.ability.teleport.skills.lv2.PiercingTeleportation;
import org.academy.internal.common.ability.teleport.skills.lv2.SelfTeleport;
import org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy;
import org.academy.internal.common.ability.teleport.skills.lv3.FleshRipping;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv3.Shackle;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSelect;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSetup;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportStart;
import org.academy.internal.common.ability.teleport.skills.lv4.QuickLocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv5.DefensiveTeleport;
import org.academy.internal.common.ability.teleport.skills.lv5.Flashing;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;

public final class Skills {
    public static final DeferredRegister<Skill> SKILLS = DeferredRegister.create(Registries.Keys.SKILLS, AcademyCraft.MOD_ID);
    /**
     * Aeromanip
     */
    public static final DeferredHolder<Skill, AirflowJet> AIRFLOW_JET = SKILLS.register(SkillNames.AIRFLOW_JET, AirflowJet::new);
    public static final DeferredHolder<Skill, AirCushion> AIR_CUSHION = SKILLS.register(SkillNames.AIR_CUSHION, AirCushion::new);
    public static final DeferredHolder<Skill, FlowSense> FLOW_SENSE = SKILLS.register(SkillNames.FLOW_SENSE, FlowSense::new);
    public static final DeferredHolder<Skill, AtmosphereShield> ATMOSPHERE_SHIELD = SKILLS.register(SkillNames.ATMOSPHERE_SHIELD, AtmosphereShield::new);
    public static final DeferredHolder<Skill, BreathingFilm> BREATHING_FILM = SKILLS.register(SkillNames.BREATHING_FILM, BreathingFilm::new);
    public static final DeferredHolder<Skill, PneumaticGrasp> PNEUMATIC_GRASP = SKILLS.register(SkillNames.PNEUMATIC_GRASP, PneumaticGrasp::new);
    public static final DeferredHolder<Skill, TailwindField> TAILWIND_FIELD = SKILLS.register(SkillNames.TAILWIND_FIELD, TailwindField::new);
    public static final DeferredHolder<Skill, LaminarCutter> LAMINAR_CUTTER = SKILLS.register(SkillNames.LAMINAR_CUTTER, LaminarCutter::new);
    public static final DeferredHolder<Skill, VortexPull> VORTEX_PULL = SKILLS.register(SkillNames.VORTEX_PULL, VortexPull::new);
    public static final DeferredHolder<Skill, AtmosphereBlastGun> ATMOSPHERE_BLAST_GUN = SKILLS.register(SkillNames.ATMOSPHERE_BLAST_GUN, AtmosphereBlastGun::new);
    public static final DeferredHolder<Skill, WindCorridor> WIND_CORRIDOR = SKILLS.register(SkillNames.WIND_CORRIDOR, WindCorridor::new);
    public static final DeferredHolder<Skill, PressureLock> PRESSURE_LOCK = SKILLS.register(SkillNames.PRESSURE_LOCK, PressureLock::new);
    public static final DeferredHolder<Skill, Flight> FLIGHT = SKILLS.register(SkillNames.FLIGHT, Flight::new);
    public static final DeferredHolder<Skill, VacuumDomain> VACUUM_DOMAIN = SKILLS.register(SkillNames.VACUUM_DOMAIN, VacuumDomain::new);
    public static final DeferredHolder<Skill, AtmosphericDominion> ATMOSPHERIC_DOMINION = SKILLS.register(SkillNames.ATMOSPHERIC_DOMINION, AtmosphericDominion::new);
    /**
     * Accelerator
     */
    public static final DeferredHolder<Skill, VectorReflection> VECTOR_REFLECTION = SKILLS.register(SkillNames.VECTOR_REFLECTION, VectorReflection::new);
    public static final DeferredHolder<Skill, ReflectionFilter> REFLECTION_FILTER = SKILLS.register(SkillNames.REFLECTION_FILTER, ReflectionFilter::new);
    public static final DeferredHolder<Skill, VectorBlast> VECTOR_BLAST = SKILLS.register(SkillNames.VECTOR_BLAST, VectorBlast::new);
    public static final DeferredHolder<Skill, VectorAccel> VECTOR_ACCEL = SKILLS.register(SkillNames.VECTOR_ACCEL, VectorAccel::new);
    public static final DeferredHolder<Skill, VectorDeviation> VECTOR_DEVIATION = SKILLS.register(SkillNames.VECTOR_DEVIATION, VectorDeviation::new);
    public static final DeferredHolder<Skill, KineticEnergyApplied> KINETIC_ENERGY_APPLIED = SKILLS.register(SkillNames.KINETIC_ENERGY_APPLIED, KineticEnergyApplied::new);
    public static final DeferredHolder<Skill, DirStrike> DIR_STRIKE = SKILLS.register(SkillNames.DIR_STRIKE, DirStrike::new);
    public static final DeferredHolder<Skill, BloodflowReverse> BLOODFLOW_REVERSE = SKILLS.register(SkillNames.BLOODFLOW_REVERSE, BloodflowReverse::new);
    public static final DeferredHolder<Skill, BlackWing> BLACK_WING = SKILLS.register(SkillNames.BLACK_WING, BlackWing::new);
    public static final DeferredHolder<Skill, WhiteWing> WHITE_WING = SKILLS.register(SkillNames.WHITE_WING, WhiteWing::new);
    public static final DeferredHolder<Skill, PlatinumWing> PLATINUM_WING = SKILLS.register(SkillNames.PLATINUM_WING, PlatinumWing::new);
    public static final DeferredHolder<Skill, CrossingTheAbyss> CROSSING_THE_ABYSS = SKILLS.register(SkillNames.CROSSING_THE_ABYSS, CrossingTheAbyss::new);
    /**
     * Electromaster
     */
    public static final DeferredHolder<Skill, ArcGenerate> ARC_GENERATE = SKILLS.register(SkillNames.ARC_GENERATE, ArcGenerate::new);
    public static final DeferredHolder<Skill, MagnetManipulation> MAGNET_MANIPULATION = SKILLS.register(SkillNames.MAGNET_MANIPULATION, MagnetManipulation::new);
    public static final DeferredHolder<Skill, MineDetect> MINE_DETECT = SKILLS.register(SkillNames.MINE_DETECT, MineDetect::new);
    public static final DeferredHolder<Skill, MagneticWeapon> MAGNETIC_WEAPON = SKILLS.register(SkillNames.MAGNETIC_WEAPON, MagneticWeapon::new);
    public static final DeferredHolder<Skill, CurrentSymbiosis> CURRENT_SYMBIOSIS = SKILLS.register(SkillNames.CURRENT_SYMBIOSIS, CurrentSymbiosis::new);
    public static final DeferredHolder<Skill, BioelectricOperation> BIOELECTRIC_OPERATION = SKILLS.register(SkillNames.BIOELECTRIC_OPERATION, BioelectricOperation::new);
    public static final DeferredHolder<Skill, ElectromagneticShield> ELECTROMAGNETIC_SHIELD = SKILLS.register(SkillNames.ELECTROMAGNETIC_SHIELD, ElectromagneticShield::new);
    /**
     * Phase 5 - Ultimate and Signature Skills
     */
    public static final DeferredHolder<Skill, IronSandArsenal> IRON_SAND_ARSENAL = SKILLS.register(SkillNames.IRON_SAND_ARSENAL, IronSandArsenal::new);
    public static final DeferredHolder<Skill, ThunderLance> THUNDER_LANCE = SKILLS.register(SkillNames.THUNDER_LANCE, ThunderLance::new);
    public static final DeferredHolder<Skill, Railgun> RAILGUN = SKILLS.register(SkillNames.RAILGUN, Railgun::new);
    public static final DeferredHolder<Skill, BallLightning> BALL_LIGHTNING = SKILLS.register(SkillNames.BALL_LIGHTNING, BallLightning::new);
    /**
     * Meltdowner
     */
    public static final DeferredHolder<Skill, SingleHighSpeedElectronBeam> SINGLE_HIGH_SPEED_ELECTRON_BEAM = SKILLS.register(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM, SingleHighSpeedElectronBeam::new);
    public static final DeferredHolder<Skill, ScatterBomb> SCATTER_BOMB = SKILLS.register(SkillNames.SCATTER_BOMB, ScatterBomb::new);
    public static final DeferredHolder<Skill, RadiationIntensify> RADIATION_INTENSIFY = SKILLS.register(SkillNames.RADIATION_INTENSIFY, RadiationIntensify::new);
    /**
     * Teleport
     */
    public static final DeferredHolder<Skill, ThreateningTeleport> THREATENING_TELEPORT = SKILLS.register(SkillNames.THREATENING_TELEPORT, ThreateningTeleport::new);
    public static final DeferredHolder<Skill, SpaceFoldingTheorem> SPACE_FOLDING_THEOREM = SKILLS.register(SkillNames.SPACE_FOLDING_THEOREM, SpaceFoldingTheorem::new);
    public static final DeferredHolder<Skill, SelfTeleport> SELF_TELEPORT = SKILLS.register(SkillNames.SELF_TELEPORT, SelfTeleport::new);
    public static final DeferredHolder<Skill, SpatialSynergy> SPATIAL_SYNERGY = SKILLS.register(SkillNames.SPATIAL_SYNERGY, SpatialSynergy::new);
    public static final DeferredHolder<Skill, PiercingTeleportation> PIERCING_TELEPORTATION = SKILLS.register(SkillNames.PIERCING_TELEPORTATION, PiercingTeleportation::new);
    public static final DeferredHolder<Skill, FleshRipping> FLESH_RIPPING = SKILLS.register(SkillNames.FLESH_RIPPING, FleshRipping::new);
    public static final DeferredHolder<Skill, LocationTeleport> LOCATION_TELEPORT = SKILLS.register(SkillNames.LOCATION_TELEPORT, LocationTeleport::new);
    public static final DeferredHolder<Skill, QuickLocationTeleport> QUICK_LOCATION_TELEPORT = SKILLS.register(SkillNames.QUICK_LOCATION_TELEPORT, QuickLocationTeleport::new);
    public static final DeferredHolder<Skill, AreaTeleportSelect> AREA_TELEPORT_SELECT = SKILLS.register(SkillNames.AREA_TELEPORT_SELECT, AreaTeleportSelect::new);
    public static final DeferredHolder<Skill, AreaTeleportSetup> AREA_TELEPORT_SETUP = SKILLS.register(SkillNames.AREA_TELEPORT_SETUP, AreaTeleportSetup::new);
    public static final DeferredHolder<Skill, AreaTeleportStart> AREA_TELEPORT_START = SKILLS.register(SkillNames.AREA_TELEPORT_START, AreaTeleportStart::new);
    public static final DeferredHolder<Skill, Flashing> FLASHING = SKILLS.register(SkillNames.FLASHING, Flashing::new);
    public static final DeferredHolder<Skill, DefensiveTeleport> DEFENSIVE_TELEPORT = SKILLS.register(SkillNames.DEFENSIVE_TELEPORT, DefensiveTeleport::new);
    /**
     * Darkmatter
     */
    public static final DeferredHolder<Skill, DarkmatterShaping> DARKMATTER_SHAPING = SKILLS.register(SkillNames.DARKMATTER_SHAPING, DarkmatterShaping::new);
    public static final DeferredHolder<Skill, DarkmatterDisassemble> DARKMATTER_DISASSEMBLE = SKILLS.register(SkillNames.DARKMATTER_DISASSEMBLE, DarkmatterDisassemble::new);
    public static final DeferredHolder<Skill, DarkmatterCut> DARKMATTER_CUT = SKILLS.register(SkillNames.DARKMATTER_CUT, DarkmatterCut::new);
    public static final DeferredHolder<Skill, DarkmatterRadiation> DARKMATTER_RADIATION = SKILLS.register(SkillNames.DARKMATTER_RADIATION, DarkmatterRadiation::new);
    public static final DeferredHolder<Skill, DarkmatterRepair> DARKMATTER_REPAIR = SKILLS.register(SkillNames.DARKMATTER_REPAIR, DarkmatterRepair::new);
    public static final DeferredHolder<Skill, DarkmatterCreation> DARKMATTER_CREATION = SKILLS.register(SkillNames.DARKMATTER_CREATION, DarkmatterCreation::new);
    public static final DeferredHolder<Skill, DarkmatterSixWings> DARKMATTER_SIX_WINGS = SKILLS.register(SkillNames.DARKMATTER_SIX_WINGS, DarkmatterSixWings::new);
    public static final DeferredHolder<Skill, SpacialExcision> SPACIAL_EXCISION = SKILLS.register(SkillNames.SPACIAL_EXCISION, SpacialExcision::new);
    public static final DeferredHolder<Skill, CurrentRecharge> CURRENT_RECHARGE = SKILLS.register(SkillNames.CURRENT_RECHARGE, CurrentRecharge::new);
    public static final DeferredHolder<Skill, Disarm> DISARM = SKILLS.register(SkillNames.DISARM, Disarm::new);
    public static final DeferredHolder<Skill, Shackle> SHACKLE = SKILLS.register(SkillNames.SHACKLE, Shackle::new);
    /**
     * Mentalout
     */
    public static final DeferredHolder<Skill, MentalIntervention> MENTAL_INTERVENTION =
            SKILLS.register(SkillNames.MENTAL_INTERVENTION, MentalIntervention::new);
    public static final DeferredHolder<Skill, TargetMisidentification> TARGET_MISIDENTIFICATION =
            SKILLS.register(SkillNames.TARGET_MISIDENTIFICATION, TargetMisidentification::new);
    public static final DeferredHolder<Skill, MentalStupor> MENTAL_STUPOR =
            SKILLS.register(SkillNames.MENTAL_STUPOR, MentalStupor::new);
    public static final DeferredHolder<Skill, ImpressionManipulation> IMPRESSION_MANIPULATION =
            SKILLS.register(SkillNames.IMPRESSION_MANIPULATION, ImpressionManipulation::new);
    public static final DeferredHolder<Skill, MentalIntrusion> MENTAL_INTRUSION =
            SKILLS.register(SkillNames.MENTAL_INTRUSION, MentalIntrusion::new);
    public static final DeferredHolder<Skill, MentalTakeover> MENTAL_TAKEOVER =
            SKILLS.register(SkillNames.MENTAL_TAKEOVER, MentalTakeover::new);
    public static final DeferredHolder<Skill, SensoryDistortion> SENSORY_DISTORTION =
            SKILLS.register(SkillNames.SENSORY_DISTORTION, SensoryDistortion::new);
    public static final DeferredHolder<Skill, CommandPositioning> COMMAND_POSITIONING =
            SKILLS.register(SkillNames.COMMAND_POSITIONING, CommandPositioning::new);
    public static final DeferredHolder<Skill, PrecisionOperation> PRECISION_OPERATION =
            SKILLS.register(SkillNames.PRECISION_OPERATION, PrecisionOperation::new);
    /**
     * Phase 2 - Aura and Toggle Skills
     */
    public static final DeferredHolder<Skill, StormWing> STORM_WING = SKILLS.register(SkillNames.STORM_WING, StormWing::new);
    public static final DeferredHolder<Skill, PlasmaGeneration> PLASMA_GENERATION = SKILLS.register(SkillNames.PLASMA_GENERATION, PlasmaGeneration::new);
    public static final DeferredHolder<Skill, ElectricalContact> ELECTRICAL_CONTACT = SKILLS.register(SkillNames.ELECTRICAL_CONTACT, ElectricalContact::new);
    /**
     * Phase 4 - Complex Skills
     */
    public static final DeferredHolder<Skill, LightningNova> LIGHTNING_NOVA = SKILLS.register(SkillNames.LIGHTNING_NOVA, LightningNova::new);
    public static final DeferredHolder<Skill, LightningStorm> LIGHTNING_STORM = SKILLS.register(SkillNames.LIGHTNING_STORM, LightningStorm::new);
    public static final DeferredHolder<Skill, Thunderclap> THUNDERCLAP = SKILLS.register(SkillNames.THUNDERCLAP, Thunderclap::new);
    public static final DeferredHolder<Skill, MiningBeam> MINING_BEAM = SKILLS.register(SkillNames.MINING_BEAM, MiningBeam::new);
    public static final DeferredHolder<Skill, LightShield> LIGHT_SHIELD = SKILLS.register(SkillNames.LIGHT_SHIELD, LightShield::new);
    public static final DeferredHolder<Skill, ParticleWaveCannon> PARTICLE_WAVE_CANNON = SKILLS.register(SkillNames.PARTICLE_WAVE_CANNON, ParticleWaveCannon::new);
    public static final DeferredHolder<Skill, Cloudroom> CLOUDROOM = SKILLS.register(SkillNames.CLOUDROOM, Cloudroom::new);
    public static final DeferredHolder<Skill, JetStrike> JET_STRIKE = SKILLS.register(SkillNames.JET_STRIKE, JetStrike::new);
    public static final DeferredHolder<Skill, Disintegrate> DISINTEGRATE = SKILLS.register(SkillNames.DISINTEGRATE, Disintegrate::new);
    public static final DeferredHolder<Skill, AutoCruiseBeamCannon> AUTO_CRUISE_BEAM_CANNON = SKILLS.register(SkillNames.AUTO_CRUISE_BEAM_CANNON, AutoCruiseBeamCannon::new);
    /**
     * Level0 - Common Passive Skills
     */
    public static final DeferredHolder<Skill, BrainDomainDevelopment> BRAIN_DOMAIN_DEVELOPMENT = SKILLS.register(SkillNames.BRAIN_DOMAIN_DEVELOPMENT, BrainDomainDevelopment::new);
    public static final DeferredHolder<Skill, MultipleBrainDomainSegmentation> MULTIPLE_BRAIN_DOMAIN_SEGMENTATION = SKILLS.register(SkillNames.MULTIPLE_BRAIN_DOMAIN_SEGMENTATION, MultipleBrainDomainSegmentation::new);
    public static final DeferredHolder<Skill, ParallelThoughtComputation> PARALLEL_THOUGHT_COMPUTATION = SKILLS.register(SkillNames.PARALLEL_THOUGHT_COMPUTATION, ParallelThoughtComputation::new);
    public static final DeferredHolder<Skill, CompleteConsciousnessAnalysis> COMPLETE_CONSCIOUSNESS_ANALYSIS = SKILLS.register(SkillNames.COMPLETE_CONSCIOUSNESS_ANALYSIS, CompleteConsciousnessAnalysis::new);
    public static final DeferredHolder<Skill, AbsoluteSelfControl> ABSOLUTE_SELF_CONTROL = SKILLS.register(SkillNames.ABSOLUTE_SELF_CONTROL, AbsoluteSelfControl::new);
    public static final DeferredHolder<Skill, EnduranceTraining> ENDURANCE_TRAINING = SKILLS.register(SkillNames.ENDURANCE_TRAINING, EnduranceTraining::new);
    public static final DeferredHolder<Skill, PhysicalTraining> PHYSICAL_TRAINING = SKILLS.register(SkillNames.PHYSICAL_TRAINING, PhysicalTraining::new);

    private Skills() {
    }
}
