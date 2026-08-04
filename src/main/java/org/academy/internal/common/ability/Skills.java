package org.academy.internal.common.ability;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.accelerator.skills.lv1.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.ability.accelerator.skills.lv2.DirStrike;
import org.academy.internal.common.ability.accelerator.skills.lv2.VectorAccel;
import org.academy.internal.common.ability.accelerator.skills.lv3.HyperAccelerate;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.ability.accelerator.skills.lv5.BloodflowReverse;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.CrossingTheAbyss;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.academy.internal.common.ability.aeromanip.skills.AirflowJet;
import org.academy.internal.common.ability.aeromanip.skills.AtmosphereShield;
import org.academy.internal.common.ability.aeromanip.skills.AtmosphereBlastGun;
import org.academy.internal.common.ability.aeromanip.skills.BreathingFilm;
import org.academy.internal.common.ability.aeromanip.skills.Flight;
import org.academy.internal.common.ability.aeromanip.skills.VacuumDomain;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv1.ElectricalContact;
import org.academy.internal.common.ability.electromaster.skills.lv1.PulseCharge;
import org.academy.internal.common.ability.electromaster.skills.lv2.LightningNova;
import org.academy.internal.common.ability.electromaster.skills.lv2.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv2.MineDetect;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentSymbiosis;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.ability.electromaster.skills.lv4.BioelectricOperation;
import org.academy.internal.common.ability.electromaster.skills.lv4.ElectromagneticShield;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.ability.electromaster.skills.lv4.LightningStorm;
import org.academy.internal.common.ability.electromaster.skills.lv5.BallLightning;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.academy.internal.common.ability.level0.skills.*;
import org.academy.internal.common.ability.meltdowner.skills.RadiationIntensify;
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ScatterBomb;
import org.academy.internal.common.ability.meltdowner.skills.lv3.Cloudroom;
import org.academy.internal.common.ability.meltdowner.skills.lv3.LightShield;
import org.academy.internal.common.ability.meltdowner.skills.lv4.JetStrike;
import org.academy.internal.common.ability.meltdowner.skills.lv4.ParticleWaveCannon;
import org.academy.internal.common.ability.meltdowner.skills.lv5.ChainFusion;
import org.academy.internal.common.ability.meltdowner.skills.lv5.AutoCruiseBeamCannon;
import org.academy.internal.common.ability.meltdowner.skills.lv5.Disintegrate;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;
import org.academy.internal.common.ability.teleport.skills.lv1.SpaceFoldingTheorem;
import org.academy.internal.common.ability.teleport.skills.lv1.ThreateningTeleport;
import org.academy.internal.common.ability.teleport.skills.lv2.Disarm;
import org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy;
import org.academy.internal.common.ability.teleport.skills.lv3.CutThrough;
import org.academy.internal.common.ability.teleport.skills.lv3.FleshRipping;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv3.Shackle;
import org.academy.internal.common.ability.teleport.skills.lv4.QuickLocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSelect;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSetup;
import org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportStart;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;
import org.academy.internal.common.ability.teleport.skills.lv5.Flashing;
import org.academy.internal.common.ability.teleport.skills.lv5.DefensiveTeleport;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterRepair;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterCreation;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterSixWings;

public final class Skills {
    public static final DeferredRegister<Skill> SKILLS = DeferredRegister.create(Registries.Keys.SKILLS, AcademyCraft.MOD_ID);
    /**
     * Aeromanip
     */
    public static final DeferredHolder<Skill, AirflowJet> AIRFLOW_JET = SKILLS.register(SkillNames.AIRFLOW_JET, AirflowJet::new);
    public static final DeferredHolder<Skill, AtmosphereShield> ATMOSPHERE_SHIELD = SKILLS.register(SkillNames.ATMOSPHERE_SHIELD, AtmosphereShield::new);
    public static final DeferredHolder<Skill, BreathingFilm> BREATHING_FILM = SKILLS.register(SkillNames.BREATHING_FILM, BreathingFilm::new);
    public static final DeferredHolder<Skill, AtmosphereBlastGun> ATMOSPHERE_BLAST_GUN = SKILLS.register(SkillNames.ATMOSPHERE_BLAST_GUN, AtmosphereBlastGun::new);
    public static final DeferredHolder<Skill, Flight> FLIGHT = SKILLS.register(SkillNames.FLIGHT, Flight::new);
    public static final DeferredHolder<Skill, VacuumDomain> VACUUM_DOMAIN = SKILLS.register(SkillNames.VACUUM_DOMAIN, VacuumDomain::new);
    /**
     * Accelerator
     */
    public static final DeferredHolder<Skill, VectorReflection> VECTOR_REFLECTION = SKILLS.register(SkillNames.VECTOR_REFLECTION, VectorReflection::new);
    public static final DeferredHolder<Skill, ReflectionFilter> REFLECTION_FILTER = SKILLS.register(SkillNames.REFLECTION_FILTER, ReflectionFilter::new);
    public static final DeferredHolder<Skill, VectorBlast> VECTOR_BLAST = SKILLS.register(SkillNames.VECTOR_BLAST, VectorBlast::new);
    public static final DeferredHolder<Skill, VectorAccel> VECTOR_ACCEL = SKILLS.register(SkillNames.VECTOR_ACCEL, VectorAccel::new);
    public static final DeferredHolder<Skill, VectorReduction> VECTOR_REDUCTION = SKILLS.register(SkillNames.VECTOR_REDUCTION, VectorReduction::new);
    public static final DeferredHolder<Skill, HyperAccelerate> HYPER_ACCELERATE = SKILLS.register(SkillNames.HYPER_ACCELERATE, HyperAccelerate::new);
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
    public static final DeferredHolder<Skill, CutThrough> CUT_THROUGH = SKILLS.register(SkillNames.CUT_THROUGH, CutThrough::new);
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
    public static final DeferredHolder<Skill, PulseCharge> PULSE_CHARGE = SKILLS.register(SkillNames.PULSE_CHARGE, PulseCharge::new);
    public static final DeferredHolder<Skill, Disarm> DISARM = SKILLS.register(SkillNames.DISARM, Disarm::new);
    public static final DeferredHolder<Skill, Shackle> SHACKLE = SKILLS.register(SkillNames.SHACKLE, Shackle::new);
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
    public static final DeferredHolder<Skill, ChainFusion> CHAIN_FUSION = SKILLS.register(SkillNames.CHAIN_FUSION, ChainFusion::new);
    public static final DeferredHolder<Skill, Disintegrate> DISINTEGRATE = SKILLS.register(SkillNames.DISINTEGRATE, Disintegrate::new);
    public static final DeferredHolder<Skill, AutoCruiseBeamCannon> AUTO_CRUISE_BEAM_CANNON = SKILLS.register(SkillNames.AUTO_CRUISE_BEAM_CANNON, AutoCruiseBeamCannon::new);
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
