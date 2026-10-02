package org.academy.api.client.resources;

import net.minecraft.resources.Identifier;

import static org.academy.AcademyCraft.academy;
import static org.academy.AcademyCraft.vanilla;

public final class R {
    private R() {
    }

    public static final class fonts {
        public static final Identifier source_sans_3_regular = academy("fonts/source-sans-3-regular");
        public static final Identifier wqy_microhei_modified = academy("fonts/wqy-microhei-modified");

        private fonts() {
        }
    }

    public static final class models {
        private models() {
        }

        public static final class block {
            public static final Identifier wind_gen_base_transforms = academy("models/block/wind_gen_base_transforms");

            private block() {
            }
        }
    }

    public static final class particles {
        public static final Identifier arc_medium = academy("particles/arc_medium");
        public static final Identifier arc_small = academy("particles/arc_small");
        public static final Identifier imag_phase_fluid = academy("particles/imag_phase_fluid");
        public static final Identifier imag_phase_leaves = academy("particles/imag_phase_leaves");

        private particles() {
        }
    }

    public static final class shaders {
        public static final Identifier position_tex = vanilla("core/position_tex");
        public static final Identifier position_color = vanilla("core/position_color");
        public static final Identifier position_tex_color = vanilla("core/position_tex_color");

        private shaders() {
        }

        public static final class core {
            public static final Identifier vfx_particle = academy("core/vfx_particle");
            public static final Identifier vfx_beam = academy("core/vfx_beam");
            public static final Identifier vfx_ring = academy("core/vfx_ring");
            public static final Identifier vfx_tex_billboard = academy("core/vfx_tex_billboard");
            public static final Identifier vfx_sky_strike_quad = academy("core/vfx_sky_strike_quad");
            public static final Identifier vfx_screen_flash = academy("core/vfx_screen_flash");
            public static final Identifier vfx_lightning = academy("core/vfx_lightning");
            public static final Identifier glow_blend = academy("core/glow_blend");
            public static final Identifier distortion_ring = academy("core/distortion_ring");
            public static final Identifier backdrop_sample = academy("core/backdrop_sample");
            public static final Identifier gaussian_blur = academy("core/gaussian_blur");
            public static final Identifier glow_circle = academy("core/glow_circle");
            public static final Identifier platinum_cosmic_wing = academy("core/platinum_cosmic_wing");
            public static final Identifier wing_ring_cosmic = academy("core/wing_ring_cosmic");
            public static final Identifier vfx_model_mesh = academy("core/vfx_model_mesh");
            public static final Identifier vfx_color_mesh = academy("core/vfx_color_mesh");
            public static final Identifier vfx_block_mesh = academy("core/vfx_block_mesh");
            public static final Identifier image = academy("core/image");
            public static final Identifier image_circle = academy("core/image_circle");
            public static final Identifier image_monochrome = academy("core/image_monochrome");
            public static final Identifier imgui = academy("core/imgui");
            public static final Identifier msdf_text = academy("core/msdf_text");
            public static final Identifier msdf_text_instanced = academy("core/msdf_text_instanced");
            public static final Identifier bitmap_text = academy("core/bitmap_text");
            public static final Identifier particle_additive = academy("core/particle_additive");
            public static final Identifier pos_color = academy("core/pos_color");
            public static final Identifier rounded_rect = academy("core/rounded_rect");
            public static final Identifier screen_blit = academy("core/screen_blit");
            public static final Identifier spatial_cut_depth = academy("core/spatial_cut_depth");
            public static final Identifier spatial_cut_mask = academy("core/spatial_cut_mask");
            public static final Identifier spatial_cut_line = academy("core/spatial_cut_line");
            public static final Identifier spatial_cut_glow = academy("core/spatial_cut_glow");
            public static final Identifier spatial_cut_material = academy("core/spatial_cut_material");
            public static final Identifier sdf_circle_glow = academy("core/sdf_circle_glow");
            public static final Identifier sdf_sharp_margin = academy("core/sdf_sharp_margin");
            public static final Identifier shockwave = academy("core/shockwave");
            public static final Identifier skill_progress = academy("core/skill_progress");
            public static final Identifier spatial_distortion = academy("core/spatial_distortion");
            public static final Identifier spatial_cut = academy("core/spatial_cut");
            public static final Identifier trail = academy("core/trail");
            public static final Identifier vfxgraph_particle = academy("core/vfxgraph_particle");
            public static final Identifier vfxgraph_fire = academy("core/vfxgraph_fire");
            public static final Identifier vfxgraph_smoke = academy("core/vfxgraph_smoke");
            public static final Identifier vfxgraph_mesh = academy("core/vfxgraph_mesh");
            public static final Identifier vfxgraph_simple = academy("core/vfxgraph_simple");
            public static final Identifier vfxgraph_arc = academy("core/vfxgraph_arc");
            public static final Identifier vfxgraph_surface = academy("core/vfxgraph_surface");

            private core() {
            }
        }

        public static final class include {
            public static final Identifier projection_utils = academy("include/projection_utils");

            private include() {
            }
        }
    }

    public static final class textures {
        public static final Identifier ICON_AEROMANIP = academy("textures/ability/aeromanip/icon.png");
        public static final Identifier ICON_DARKMATTER = academy("textures/ability/darkmatter/icon.png");
        public static final Identifier SP_BAR_VALUE = academy("textures/hud/sp_bar_value.png");
        public static final Identifier arc = academy("textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");
        public static final Identifier magnet_manipulation_icon = academy("textures/ability/electromaster/skill/magnet_manipulation/icon.png");
        public static final Identifier mine_detect_icon = academy("textures/ability/electromaster/skill/mine_detect/icon.png");
        public static final Identifier thunder_lance_icon = academy("textures/ability/electromaster/skill/thunder_lance/icon.png");
        public static final Identifier electromagnetic_shield_icon = academy("textures/ability/electromaster/skill/electromagnetic_shield/icon.png");
        public static final Identifier current_recharge_icon = academy("textures/ability/electromaster/skill/current_recharge/icon.png");
        public static final Identifier current_symbiosis_icon = academy("textures/ability/electromaster/skill/current_symbiosis/icon.png");
        public static final Identifier bioelectric_operation_icon = academy("textures/ability/electromaster/skill/bioelectric_operation/icon.png");
        public static final Identifier iron_sand_arsenal_effect = academy("textures/ability/electromaster/skill/iron_sand_arsenal/effect/iron_sand.png");
        public static final Identifier ball_lightning_icon = academy("textures/ability/electromaster/skill/ball_lightning/icon.png");
        public static final Identifier single_high_speed_electron_beam_icon = academy("textures/ability/meltdowner/skill/single_high_speed_electron_beam/icon.png");
        public static final Identifier scatter_bomb_icon = academy("textures/ability/meltdowner/skill/scatter_bomb/icon.png");
        public static final Identifier radiation_intensify_icon = academy("textures/ability/meltdowner/skill/radiation_intensify/icon.png");
        public static final Identifier mining_beam_icon = academy("textures/ability/meltdowner/skill/mining_beam/icon.png");
        public static final Identifier light_shield_icon = academy("textures/ability/meltdowner/skill/light_shield/icon.png");
        public static final Identifier light_shield_effect = academy("textures/ability/generic/skill/light_shield/effect/mdshield.png");
        public static final Identifier particle_wave_cannon_icon = academy("textures/ability/meltdowner/skill/particle_wave_cannon/icon.png");
        public static final Identifier jet_strike_icon = academy("textures/ability/meltdowner/skill/jet_strike/icon.png");
        public static final Identifier auto_cruise_beam_cannon_icon = academy("textures/ability/meltdowner/skill/auto_cruise_beam_cannon/icon.png");
        public static final Identifier threatening_teleport_icon = academy("textures/ability/teleport/skill/threatening_teleport/icon.png");
        public static final Identifier space_folding_theorem_icon = academy("textures/ability/teleport/skill/space_folding_theorem/icon.png");
        public static final Identifier self_teleport_icon = academy("textures/ability/teleport/skill/self_teleport/icon.png");
        public static final Identifier piercing_teleportation_icon = academy("textures/ability/teleport/skill/piercing_teleportation/icon.png");
        public static final Identifier flesh_ripping_icon = academy("textures/ability/teleport/skill/flesh_ripping/icon.png");
        public static final Identifier location_teleport_icon = academy("textures/ability/teleport/skill/location_teleport/icon.png");
        public static final Identifier quick_location_teleport_icon = academy("textures/ability/teleport/skill/quick_location_teleport/icon.png");
        public static final Identifier area_teleport_select_icon = academy("textures/ability/teleport/skill/area_teleport_select/icon.png");
        public static final Identifier flashing_icon = academy("textures/ability/teleport/skill/flashing/icon.png");
        public static final Identifier defensive_teleport_icon = academy("textures/ability/teleport/skill/defensive_teleport/icon.png");
        public static final Identifier chunk_leap_icon = academy("textures/ability/teleport/skill/chunk_leap/icon.png");
        public static final Identifier teleport_cursor = academy("textures/ability/teleport/effect/teleport_cursor.png");
        public static final Identifier darkmatter_shaping_icon = academy("textures/ability/darkmatter/skill/darkmatter_shaping/icon.png");
        public static final Identifier darkmatter_disassemble_icon = academy("textures/ability/darkmatter/skill/darkmatter_disassemble/icon.png");
        public static final Identifier darkmatter_phase_tuning_icon = academy("textures/ability/darkmatter/skill/darkmatter_phase_tuning/icon.png");
        public static final Identifier darkmatter_cut_icon = academy("textures/ability/darkmatter/skill/darkmatter_cut/icon.png");
        public static final Identifier darkmatter_cut_slash_effect_1 = academy("textures/ability/darkmatter/skill/darkmatter_cut/effect/darkmatter_cut_slash_1.png");
        public static final Identifier darkmatter_cut_slash_effect_2 = academy("textures/ability/darkmatter/skill/darkmatter_cut/effect/darkmatter_cut_slash_2.png");
        public static final Identifier darkmatter_cut_slash_effect_3 = academy("textures/ability/darkmatter/skill/darkmatter_cut/effect/darkmatter_cut_slash_3.png");
        public static final Identifier darkmatter_cut_slash_effect_4 = academy("textures/ability/darkmatter/skill/darkmatter_cut/effect/darkmatter_cut_slash_4.png");
        public static final Identifier darkmatter_radiation_icon = academy("textures/ability/darkmatter/skill/darkmatter_radiation/icon.png");
        public static final Identifier darkmatter_repair_icon = academy("textures/ability/darkmatter/skill/darkmatter_repair/icon.png");
        public static final Identifier darkmatter_creation_icon = academy("textures/ability/darkmatter/skill/darkmatter_creation/icon.png");
        public static final Identifier darkmatter_beetle = academy("textures/entity/darkmatter_beetle.png");
        public static final Identifier darkmatter_six_wings_icon = academy("textures/ability/darkmatter/skill/darkmatter_six_wings/icon.png");
        public static final Identifier darkmatter_six_wings_effect = academy("textures/ability/darkmatter/skill/darkmatter_six_wings/effect/darkmatter_six_wings.png");
        public static final Identifier reflection_filter_icon = academy("textures/ability/accelerator/skill/reflection_filter/icon.png");
        public static final Identifier vector_blast_icon = academy("textures/ability/accelerator/skill/vector_blast/icon.png");
        public static final Identifier kinetic_energy_applied_icon = academy("textures/ability/accelerator/skill/kinetic_energy_applied/icon.png");
        public static final Identifier kinetic_throw_icon = academy("textures/ability/accelerator/skill/kinetic_throw/icon.png");
        public static final Identifier black_wing_icon = academy("textures/ability/accelerator/skill/black_wing/icon.png");
        public static final Identifier white_wing_icon = academy("textures/ability/accelerator/skill/white_wing/icon.png");
        public static final Identifier platinum_wing_icon = academy("textures/ability/accelerator/skill/platinum_wing/icon.png");
        public static final Identifier crossing_the_abyss_icon = academy("textures/ability/accelerator/skill/crossing_the_abyss/icon.png");
        public static final Identifier plasma_generation_effect = academy("textures/ability/accelerator/skill/plasma_generation/effect/plasma.png");
        public static final Identifier plasma_generation_cloud = academy("textures/ability/accelerator/skill/plasma_generation/effect/white_smoke_hq.png");
        public static final Identifier dir_strike_icon = academy("textures/ability/accelerator/skill/dir_strike/icon.png");
        public static final Identifier storm_wing = academy("textures/ability/accelerator/skill/storm_wing/effect/tornado_ring.png");
        public static final Identifier black_wing = academy("textures/ability/accelerator/skill/black_wing/effect/tornado_ring.png");
        public static final Identifier white_wing = academy("textures/ability/accelerator/skill/white_wing/effect/tornado_ring.png");
        public static final Identifier white_wing_ascension = academy("textures/ability/accelerator/skill/white_wing/effect/ascension_ring.png");
        public static final Identifier platinum_wing_starfield = academy("textures/ability/accelerator/skill/platinum_wing/effect/starfield.png");
        public static final Identifier airflow_jet_icon = academy("textures/ability/aeromanip/skill/airflow_jet/icon.png");
        public static final Identifier air_cushion_icon = academy("textures/ability/aeromanip/skill/air_cushion/icon.png");
        public static final Identifier flow_sense_icon = academy("textures/ability/aeromanip/skill/flow_sense/icon.png");
        public static final Identifier atmosphere_shield_icon = academy("textures/ability/aeromanip/skill/atmosphere_shield/icon.png");
        public static final Identifier breathing_film_icon = academy("textures/ability/aeromanip/skill/breathing_film/icon.png");
        public static final Identifier pneumatic_grasp_icon = academy("textures/ability/aeromanip/skill/pneumatic_grasp/icon.png");
        public static final Identifier tailwind_field_icon = academy("textures/ability/aeromanip/skill/tailwind_field/icon.png");
        public static final Identifier laminar_cutter_icon = academy("textures/ability/aeromanip/skill/laminar_cutter/icon.png");
        public static final Identifier vortex_pull_icon = academy("textures/ability/aeromanip/skill/vortex_pull/icon.png");
        public static final Identifier atmosphere_blast_gun_icon = academy("textures/ability/aeromanip/skill/atmosphere_blast_gun/icon.png");
        public static final Identifier wind_corridor_icon = academy("textures/ability/aeromanip/skill/wind_corridor/icon.png");
        public static final Identifier high_speed_jet_icon = wind_corridor_icon;
        public static final Identifier pressure_lock_icon = academy("textures/ability/aeromanip/skill/pressure_lock/icon.png");
        public static final Identifier flight_icon = academy("textures/ability/aeromanip/skill/flight/icon.png");
        public static final Identifier vacuum_domain_icon = academy("textures/ability/aeromanip/skill/vacuum_domain/icon.png");
        public static final Identifier atmospheric_dominion_icon = academy("textures/ability/aeromanip/skill/atmospheric_dominion/icon.png");
        public static final Identifier adiabatic_compression_icon = atmospheric_dominion_icon;
        public static final Identifier brain_domain_development_icon = academy("textures/ability/level0/skill/brain_domain_development/icon.png");
        public static final Identifier multiple_brain_domain_segmentation_icon = academy("textures/ability/level0/skill/multiple_brain_domain_segmentation/icon.png");
        public static final Identifier parallel_thought_computation_icon = academy("textures/ability/level0/skill/parallel_thought_computation/icon.png");
        public static final Identifier complete_consciousness_analysis_icon = academy("textures/ability/level0/skill/complete_consciousness_analysis/icon.png");
        public static final Identifier absolute_self_control_icon = academy("textures/ability/level0/skill/absolute_self_control/icon.png");
        public static final Identifier endurance_training_icon = academy("textures/ability/level0/skill/endurance_training/icon.png");
        public static final Identifier physical_training_icon = academy("textures/ability/level0/skill/physical_training/icon.png");
        public static final Identifier output_control_icon = academy("textures/ability/level0/skill/output_control/icon.png");
        public static final Identifier condition_any1 = academy("textures/ability/condition/any1.png");
        public static final Identifier condition_any2 = academy("textures/ability/condition/any2.png");
        public static final Identifier condition_any3 = academy("textures/ability/condition/any3.png");
        public static final Identifier condition_any4 = academy("textures/ability/condition/any4.png");
        public static final Identifier condition_any5 = academy("textures/ability/condition/any5.png");
        public static final Identifier IMAG_PHASE_DOWSING_ROD = academy("textures/model/imag_phase_dowsing_rod.png");
        public static final Identifier OMNI_CRAFTING_TABLE = academy("textures/model/omni_crafting_table.png");
        public static final Identifier CAT_ENGINE = academy("textures/item/cat_engine.png");

        private textures() {
        }

        public static final class abilities {
            private abilities() {
            }

            public static final class condition {
                public static final Identifier any1 = academy("textures/ability/condition/any1.png");
                public static final Identifier any2 = academy("textures/ability/condition/any2.png");
                public static final Identifier any3 = academy("textures/ability/condition/any3.png");
                public static final Identifier any4 = academy("textures/ability/condition/any4.png");
                public static final Identifier any5 = academy("textures/ability/condition/any5.png");

                private condition() {
                }
            }
        }

        public static final class ability {
            private ability() {
            }

            public static final class accelerator {
                public static final Identifier icon = academy("textures/ability/accelerator/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/accelerator/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/accelerator/icon_overlay.png");

                private accelerator() {
                }

                public static final class skill {
                    private skill() {
                    }

                    public static final class bloodflow_reverse {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/bloodflow_reverse/icon.png");

                        private bloodflow_reverse() {
                        }
                    }

                    public static final class dir_strike {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/dir_strike/icon.png");

                        private dir_strike() {
                        }
                    }

                    public static final class directed_shock {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/directed_shock/icon.png");

                        private directed_shock() {
                        }
                    }

                    public static final class plasma_generation {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/plasma_generation/icon.png");

                        private plasma_generation() {
                        }
                    }

                    public static final class storm_wing {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/storm_wing/icon.png");

                        private storm_wing() {
                        }

                        public static final class effect {
                            public static final Identifier tornado_ring = academy("textures/ability/accelerator/skill/storm_wing/effect/tornado_ring.png");

                            private effect() {
                            }
                        }
                    }

                    public static final class vec_accel {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/vec_accel/icon.png");

                        private vec_accel() {
                        }
                    }

                    public static final class vector_deviation {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/vector_deviation/icon.png");

                        private vector_deviation() {
                        }
                    }

                    public static final class vector_reflection {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/vector_reflection/icon.png");

                        private vector_reflection() {
                        }
                    }
                }
            }

            public static final class condition {
                public static final Identifier any1 = academy("textures/ability/condition/any1.png");
                public static final Identifier any2 = academy("textures/ability/condition/any2.png");
                public static final Identifier any3 = academy("textures/ability/condition/any3.png");
                public static final Identifier any4 = academy("textures/ability/condition/any4.png");
                public static final Identifier any5 = academy("textures/ability/condition/any5.png");

                private condition() {
                }
            }

            public static final class electromaster {
                public static final Identifier icon = academy("textures/ability/electromaster/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/electromaster/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/electromaster/icon_overlay.png");

                private electromaster() {
                }

                public static final class skill {
                    private skill() {
                    }

                    public static final class arc_generate {
                        public static final Identifier icon = academy("textures/ability/electromaster/skill/arc_generate/icon.png");

                        private arc_generate() {
                        }

                        public static final class effect {
                            public static final Identifier line_segment = academy("textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");

                            private effect() {
                            }
                        }
                    }

                    public static final class iron_sand_arsenal {
                        public static final Identifier icon = academy("textures/ability/electromaster/skill/iron_sand_arsenal/icon.png");

                        private iron_sand_arsenal() {
                        }
                    }

                    public static final class sky_strike {
                        private sky_strike() {
                        }

                        public static final class effect {
                            public static final Identifier lightning_column = academy("textures/ability/electromaster/skill/sky_strike/effect/lightning_column.png");
                            public static final Identifier lightning_ribbon = academy("textures/ability/electromaster/skill/sky_strike/effect/lightning_ribbon.png");
                            public static final Identifier impact_shockwave_ring = academy("textures/ability/electromaster/skill/sky_strike/effect/impact_shockwave_ring.png");
                            public static final Identifier impact_flash = academy("textures/ability/electromaster/skill/sky_strike/effect/impact_flash.png");

                            private effect() {
                            }
                        }
                    }

                    public static final class railgun {
                        public static final Identifier icon = academy("textures/ability/electromaster/skill/railgun/icon.png");

                        private railgun() {
                        }
                    }

                    public static final class thunderclap {
                        public static final Identifier icon = academy("textures/ability/electromaster/skill/thunderclap/icon.png");

                        private thunderclap() {
                        }
                    }
                }
            }

            public static final class generic {
                private generic() {
                }

                public static final class effect {
                    public static final Identifier glow_circle = academy("textures/ability/generic/effect/glow_circle.png");
                    public static final Identifier smokes = academy("textures/ability/generic/effect/smokes.png");
                    public static final Identifier sparkle_blurred = academy("textures/ability/generic/effect/sparkle_blurred.png");
                    public static final Identifier white_smoke_hq = academy("textures/ability/generic/effect/white_smoke_hq.png");

                    private effect() {
                    }
                }
            }

            public static final class level0 {
                public static final Identifier icon = academy("textures/ability/level0/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/level0/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/level0/icon_overlay.png");

                private level0() {
                }

                public static final class skill {
                    private skill() {
                    }

                    public static final class brain_domain_development {
                        public static final Identifier icon = academy("textures/ability/level0/skill/brain_domain_development/icon.png");

                        private brain_domain_development() {
                        }
                    }

                    public static final class multiple_brain_domain_segmentation {
                        public static final Identifier icon = academy("textures/ability/level0/skill/multiple_brain_domain_segmentation/icon.png");

                        private multiple_brain_domain_segmentation() {
                        }
                    }

                    public static final class parallel_thought_computation {
                        public static final Identifier icon = academy("textures/ability/level0/skill/parallel_thought_computation/icon.png");

                        private parallel_thought_computation() {
                        }
                    }

                    public static final class complete_consciousness_analysis {
                        public static final Identifier icon = academy("textures/ability/level0/skill/complete_consciousness_analysis/icon.png");

                        private complete_consciousness_analysis() {
                        }
                    }

                    public static final class absolute_self_control {
                        public static final Identifier icon = academy("textures/ability/level0/skill/absolute_self_control/icon.png");

                        private absolute_self_control() {
                        }
                    }

                    public static final class endurance_training {
                        public static final Identifier icon = academy("textures/ability/level0/skill/endurance_training/icon.png");

                        private endurance_training() {
                        }
                    }

                    public static final class physical_training {
                        public static final Identifier icon = academy("textures/ability/level0/skill/physical_training/icon.png");

                        private physical_training() {
                        }
                    }

                    public static final class output_control {
                        public static final Identifier icon = academy("textures/ability/level0/skill/output_control/icon.png");

                        private output_control() {
                        }
                    }
                }
            }

            public static final class meltdowner {
                public static final Identifier icon = academy("textures/ability/meltdowner/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/meltdowner/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/meltdowner/icon_overlay.png");

                private meltdowner() {
                }

                public static final class ray {
                    public static final Identifier head = academy("textures/ability/meltdowner/ray/head.png");
                    public static final Identifier hellflare_steam = academy("textures/ability/meltdowner/ray/hellflare_steam.png");
                    public static final Identifier ray = academy("textures/ability/meltdowner/ray/ray.png");
                    public static final Identifier tail = academy("textures/ability/meltdowner/ray/tail.png");

                    private ray() {
                    }
                }
            }

            public static final class mentalout {
                public static final Identifier icon = academy("textures/ability/mentalout/icon.png");

                private mentalout() {
                }

                public static final class skill {
                    private skill() {
                    }

                    public static final class impression_manipulation {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/impression_manipulation/icon.png");

                        private impression_manipulation() {
                        }
                    }

                    public static final class mental_intervention {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/mental_intervention/icon.png");

                        private mental_intervention() {
                        }
                    }

                    public static final class mental_intrusion {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/mental_intrusion/icon.png");

                        private mental_intrusion() {
                        }
                    }

                    public static final class mental_takeover {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/mental_takeover/icon.png");

                        private mental_takeover() {
                        }
                    }

                    public static final class mental_stupor {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/mental_stupor/icon.png");

                        private mental_stupor() {
                        }
                    }

                    public static final class target_misidentification {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/target_misidentification/icon.png");

                        private target_misidentification() {
                        }
                    }

                    public static final class sensory_distortion {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/sensory_distortion/icon.png");

                        private sensory_distortion() {
                        }
                    }

                    public static final class precision_operation {
                        public static final Identifier icon = academy("textures/ability/mentalout/skill/precision_operation/icon.png");

                        private precision_operation() {
                        }
                    }

                    public static final class wide_area_interference {
                        public static final Identifier icon = precision_operation.icon;

                        private wide_area_interference() {
                        }
                    }
                }
            }

            public static final class teleport {
                public static final Identifier icon = academy("textures/ability/teleport/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/teleport/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/teleport/icon_overlay.png");

                private teleport() {
                }
            }
        }

        public static final class gui {
            private gui() {
            }

            public static final class app {
                private app() {
                }

                public static final class abilitysettings {
                    public static final Identifier ability_settings = academy("textures/gui/app/abilitysettings/ability_settings.png");

                    private abilitysettings() {
                    }
                }

                public static final class music {
                    public static final Identifier cycle = academy("textures/gui/app/music/cycle.png");
                    public static final Identifier icon = academy("textures/gui/app/music/icon.png");
                    public static final Identifier next = academy("textures/gui/app/music/next.png");
                    public static final Identifier now_playing = academy("textures/gui/app/music/now_playing.png");
                    public static final Identifier pause = academy("textures/gui/app/music/pause.png");
                    public static final Identifier play = academy("textures/gui/app/music/play.png");
                    public static final Identifier previous = academy("textures/gui/app/music/previous.png");
                    public static final Identifier random_play = academy("textures/gui/app/music/random_play.png");
                    public static final Identifier single_cycle = academy("textures/gui/app/music/single_cycle.png");
                    public static final Identifier volume = academy("textures/gui/app/music/volume.png");

                    private music() {
                    }
                }

                public static final class tutorial {
                    public static final Identifier icon = academy("textures/gui/app/tutorial/icon.png");

                    private tutorial() {
                    }
                }
            }

            public static final class developer {
                public static final Identifier button = academy("textures/gui/developer/button.png");
                public static final Identifier button_learn = academy("textures/gui/developer/button_learn.png");
                public static final Identifier effect_developer_background = academy("textures/gui/developer/effect_developer_background.png");
                public static final Identifier line = academy("textures/gui/developer/line.png");
                public static final Identifier parent_background = academy("textures/gui/developer/parent_background.png");
                public static final Identifier parent_background_developerleft = academy("textures/gui/developer/parent_background_developerleft.png");
                public static final Identifier parent_background_developermachine = academy("textures/gui/developer/parent_background_developermachine.png");
                public static final Identifier parent_background_developerright = academy("textures/gui/developer/parent_background_developerright.png");
                public static final Identifier skill_back = academy("textures/gui/developer/skill_back.png");
                public static final Identifier skill_outline = academy("textures/gui/developer/skill_outline.png");
                public static final Identifier skill_panel_back = academy("textures/gui/developer/skill_panel_back.png");
                public static final Identifier skill_radial_mask = academy("textures/gui/developer/skill_radial_mask.png");
                public static final Identifier skill_view_outline = academy("textures/gui/developer/skill_view_outline.png");
                public static final Identifier skill_view_outline_glow = academy("textures/gui/developer/skill_view_outline_glow.png");
                public static final Identifier ui_developerleft = academy("textures/gui/developer/ui_developerleft.png");
                public static final Identifier ui_developerright = academy("textures/gui/developer/ui_developerright.png");

                private developer() {
                }
            }

            public static final class element {
                public static final Identifier app_back = academy("textures/gui/element/app_back.png");
                public static final Identifier element_background300x32 = academy("textures/gui/element/element_background300x32.png");
                public static final Identifier element_background_light = academy("textures/gui/element/element_background_light.png");
                public static final Identifier histogram = academy("textures/gui/element/histogram.png");
                public static final Identifier line = academy("textures/gui/element/line.png");
                public static final Identifier logo_tech = academy("textures/gui/element/logo_tech.png");
                public static final Identifier ui_gen = academy("textures/gui/element/ui_gen.png");
                public static final Identifier ui_inventory = academy("textures/gui/element/ui_inventory.png");
                /**
                 * White disc, tinted per entity category to draw map markers as small dots.
                 */
                public static final Identifier map_marker_dot = academy("textures/gui/map/marker_dot.png");

                private element() {
                }
            }

            public static final class icon {
                public static final Identifier add = academy("textures/gui/icon/add.png");
                public static final Identifier arrow = academy("textures/gui/icon/arrow.png");
                public static final Identifier close = academy("textures/gui/icon/close.png");
                public static final Identifier icon_accelerator = academy("textures/gui/icon/icon_accelerator.png");
                public static final Identifier icon_box = academy("textures/gui/icon/icon_box.png");
                public static final Identifier icon_connected = academy("textures/gui/icon/icon_connected.png");
                public static final Identifier icon_cycle = academy("textures/gui/icon/icon_cycle.png");
                public static final Identifier icon_electromaster = academy("textures/gui/icon/icon_electromaster.png");
                public static final Identifier icon_inv = academy("textures/gui/icon/icon_inv.png");
                public static final Identifier icon_meltdowner = academy("textures/gui/icon/icon_meltdowner.png");
                public static final Identifier icon_nocategory = academy("textures/gui/icon/icon_nocategory.png");
                public static final Identifier icon_node = academy("textures/gui/icon/icon_node.png");
                public static final Identifier icon_random = academy("textures/gui/icon/icon_random.png");
                public static final Identifier icon_settings = academy("textures/gui/icon/icon_settings.png");
                public static final Identifier icon_teleporter = academy("textures/gui/icon/icon_teleporter.png");
                public static final Identifier icon_tonode = academy("textures/gui/icon/icon_tonode.png");
                public static final Identifier icon_unconnected = academy("textures/gui/icon/icon_unconnected.png");
                public static final Identifier icon_wireless = academy("textures/gui/icon/icon_wireless.png");
                public static final Identifier menu = academy("textures/gui/icon/menu.png");
                public static final Identifier more = academy("textures/gui/icon/more.png");
                public static final Identifier refresh = academy("textures/gui/icon/refresh.png");
                public static final Identifier remove = academy("textures/gui/icon/remove.png");
                public static final Identifier search = academy("textures/gui/icon/search.png");

                private icon() {
                }
            }

            public static final class node {
                public static final Identifier state_node = academy("textures/gui/node/state_node.png");
                public static final Identifier ui_node = academy("textures/gui/node/ui_node.png");

                private node() {
                }
            }

            public static final class omni_crafting {
                public static final Identifier ui_omni_crafting = academy("textures/gui/omni_crafting/ui_omni_crafting.png");

                private omni_crafting() {
                }
            }

            public static final class solar_gen {
                public static final Identifier icon_solar_gen_night = academy("textures/gui/solar_gen/icon_solar_gen_night.png");
                public static final Identifier icon_solar_gen_rainy = academy("textures/gui/solar_gen/icon_solar_gen_rainy.png");
                public static final Identifier icon_solar_gen_sunny = academy("textures/gui/solar_gen/icon_solar_gen_sunny.png");

                private solar_gen() {
                }
            }

            public static final class terminal {
                public static final Identifier icon = academy("textures/gui/terminal/icon.png");

                private terminal() {
                }
            }

            public static final class wind_gen {
                public static final Identifier icon_wind_base = academy("textures/gui/wind_gen/icon_wind_base.png");
                public static final Identifier icon_wind_pillar = academy("textures/gui/wind_gen/icon_wind_pillar.png");
                public static final Identifier icon_wind_top = academy("textures/gui/wind_gen/icon_wind_top.png");

                private wind_gen() {
                }
            }
        }

        public static final class hud {
            public static final Identifier cp_bar_background = academy("textures/hud/cp_bar_background.png");
            public static final Identifier cp_bar_value = academy("textures/hud/cp_bar_value.png");

            private hud() {
            }
        }

        public static final class model {
            public static final Identifier ability_developer = academy("textures/model/ability_developer.png");
            public static final Identifier ability_control_tablet = academy("textures/model/ability_control_tablet.png");
            public static final Identifier cleaning_robot = academy("textures/model/cleaning_robot.png");
            public static final Identifier omni_crafting_table = academy("textures/model/omni_crafting_table.png");
            public static final Identifier solar_gen = academy("textures/model/solar_gen.png");
            public static final Identifier wind_gen = academy("textures/model/wind_gen.png");
            public static final Identifier wind_gen_top = academy("textures/model/wind_gen_top.png");
            public static final Identifier wireless_node = academy("textures/model/wireless_node.png");

            private model() {
            }
        }

        public static final class block {
            public static final Identifier wind_gen_pillar = academy("textures/block/wind_gen_pillar.png");

            private block() {
            }
        }

        public static final class item {
            public static final Identifier cat_engine = academy("textures/item/cat_engine.png");

            private item() {
            }
        }
    }

    public static final class ui {
        private ui() {
        }

        public static final class wireless_panel {
            public static final float panel_width = 176.0f;
            public static final float panel_height = 187.0f;
            public static final float margin_horizontal = 12.0f;
            public static final float margin_vertical = 10.0f;
            public static final float spacing_major = 8.0f;
            public static final float spacing_minor = 4.0f;
            public static final float list_item_height = 18.0f;
            public static final float scrollbar_width = 5.0f;

            private wireless_panel() {
            }
        }

        public static final class location_teleport {
            public static final float panel_width = 420f;
            public static final float panel_height = 236f;
            public static final float content_width = 396f;
            public static final float content_margin = 12f;
            public static final float control_height = 20f;
            public static final float row_height = 18f;
            public static final float marks_height = 94f;
            public static final float title_margin_top = 8f;
            public static final float divider_margin_top = 24f;
            public static final float name_margin_top = 32f;
            public static final float coordinates_margin_top = 58f;
            public static final float mark_actions_margin_top = 84f;
            public static final float marks_margin_top = 108f;
            public static final float bottom_actions_margin_top = 208f;
            public static final float action_gap = 8f;
            public static final float coordinate_gap = 4f;
            public static final float quick_action_width = 50f;
            public static final float defensive_action_width = 50f;
            public static final float teleport_action_width = 24f;
            public static final float remove_action_width = 18f;
            public static final float text_inset = 6f;
            public static final float quick_rail_width = 2f;
            public static final float defensive_bracket_width = 4f;
            public static final float scrollbar_width = 5f;
            public static final float scrollbar_gap = 2f;
            public static final float scroll_speed = 18f;
            public static final float panel_alpha = 0.12f;
            public static final int name_max_length = 64;
            public static final int coordinate_max_length = 12;
            public static final int border_color = 0xFF7680DE;
            public static final int section_color = 0x14000000;
            public static final int control_color = 0x0C000000;
            public static final int input_color = 0x201F1F1F;
            public static final int input_focused_color = 0x305A5A5A;
            public static final int row_color = 0x18FFFFFF;
            public static final int row_alternate_color = 0x10FFFFFF;
            public static final int row_hover_color = 0x28FFFFFF;
            public static final int row_selected_color = 0x30FFFFFF;
            public static final int border_dim_color = 0x60FFFFFF;
            public static final int text_color = 0xFFFFFFFF;
            public static final int dim_color = 0xBFFFFFFF;
            public static final int teleport_color = 0xFF25C4FF;
            public static final int danger_color = 0xFFFF6C00;
            public static final int scroll_track_color = 0x28000000;

            private location_teleport() {
            }
        }

        public static final class terminal_hud {
            public static final int background_color = 0x40000000;
            public static final int primary_color = 0xFFFFFFFF;
            public static final int control_base_color = 0x20000000;
            public static final int control_hover_color = 0x40000000;
            public static final int control_active_color = 0x50000000;
            public static final float main_width = 150f;
            public static final float unfolded_main_width = 384f;
            public static final float main_height = 200f;
            public static final int apps_per_row = 3;

            private terminal_hud() {
            }
        }

        public static final class ability_developer {
            public static final float panel_main_width = 400f;
            public static final float panel_main_height = 187f;
            public static final float panel_left_width = 108.5f;
            public static final float panel_right_width = 278f;
            public static final float tree_area_width = 257f;
            public static final float tree_area_height = 139f;
            public static final long cover_anim_ms = 300L;
            public static final float blur_max_radius = 8f;
            public static final long console_char_delay_ms = 10L;
            public static final float console_reveal_fade_len = 12f;
            public static final float max_du_skills = 10f;

            private ability_developer() {
            }
        }

        public static final class music_player {
            public static final float volume_scale = 0.35f;
            public static final float vinyl_size = 88f;
            public static final long vinyl_rotation_ms = 5000L;
            public static final float info_area_width = 228f;
            public static final float info_area_height = 56f;
            public static final float list_width = 100f;
            public static final float progress_width = 128f;
            public static final float progress_height = 6f;
            public static final float room_progress_width = 96f;
            public static final float volume_bar_width = 48f;
            public static final float volume_bar_height = 4f;
            public static final float members_width = 76f;
            public static final int search_max_length = 64;
            public static final float button_size = 16f;
            public static final float row_height = 14f;

            private music_player() {
            }
        }

        public static final class darkmatter_creation {
            public static final float panel_width = 540f;
            public static final float panel_height = 320f;
            public static final float panel_min_width = 300f;
            public static final float panel_min_height = 220f;
            public static final float panel_margin = 12f;
            public static final float panel_alpha = 0.43f;
            public static final float title_height = 18f;
            public static final float tab_height = 19f;
            public static final float tab_gap = 3f;
            public static final float slot_button_width = 34f;
            public static final float slot_button_height = 18f;
            public static final float action_button_height = 20f;
            public static final float save_button_width = 92f;
            public static final float summon_button_width = 100f;
            public static final float preview_width = 166f;
            public static final int accent = 0xFF55C8E8;
            public static final int control = 0x45101820;
            public static final int control_hover = 0x70465A64;
            public static final int control_active = 0x9855C8E8;
            public static final int rail_idle = 0x55FFFFFF;
            public static final int danger = 0xA0502028;
            public static final int danger_base = 0x60402028;
            public static final int danger_pressed = 0xD0783038;
            public static final int rule_soft = 0x60FFFFFF;
            public static final int divider = 0x28FFFFFF;
            public static final int seek_track = 0x50101820;
            public static final int tooltip_bg = 0xD9101010;
            public static final int tooltip_text = 0xFFFFFFFF;
            public static final int tooltip_description = 0xFF9AA4AA;

            private darkmatter_creation() {
            }
        }

        public static final class tutorial {
            public static final float width = 384f;
            public static final float height = 200f;
            public static final float nav_width = 80f;
            public static final float preview_width = 122f;
            public static final float font_body = 6f;
            public static final float font_subtitle = 8f;
            public static final float recipe_slot_size = 18f;
            public static final float recipe_slot_gap = 2f;
            public static final int progression_blue = 0xFF1177D6;
            public static final int row_fill = 0x28000000;
            public static final int rule_strong = 0xBFFFFFFF;
            public static final int rule_medium = 0xA0FFFFFF;
            public static final int rule_soft = 0x60FFFFFF;
            public static final int rule_faint = 0x70FFFFFF;
            public static final int plane_preview = 0x18000000;

            private tutorial() {
            }
        }
    }
}
