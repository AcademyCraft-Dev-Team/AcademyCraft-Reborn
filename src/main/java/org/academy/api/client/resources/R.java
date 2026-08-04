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
        public static final class block {
            public static final Identifier wind_gen_base_transforms = academy("models/block/wind_gen_base_transforms");

            private block() {
            }
        }

        private models() {
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
        /**
         * Vanilla
         */
        public static final Identifier position_tex = vanilla("core/position_tex");
        public static final Identifier position_color = vanilla("core/position_color");
        public static final Identifier position_tex_color = vanilla("core/position_tex_color");
        public static final Identifier position_color_lightmap = vanilla("core/position_color_lightmap");
        public static final Identifier POSITION_TEX = position_tex;
        public static final Identifier POSITION_COLOR = position_color;
        public static final Identifier POSITION_TEX_COLOR = position_tex_color;
        public static final Identifier POSITION_COLOR_LIGHTMAP = position_color_lightmap;
        public static final Identifier SCREEN_BLIT = academy("core/screen_blit");
        public static final Identifier DISTORTION_RING = academy("core/distortion_ring");

        public static final class core {
            public static final Identifier aura_field = academy("core/aura_field");
            public static final Identifier bloom_blend = academy("core/bloom_blend");
            public static final Identifier distortion_ring = academy("core/distortion_ring");
            public static final Identifier gaussian_blur = academy("core/gaussian_blur");
            public static final Identifier glow_circle = academy("core/glow_circle");
            public static final Identifier BLOOM_BLEND = academy("core/bloom_blend");
            public static final Identifier GAUSSIAN_BLUR = academy("core/gaussian_blur");
            public static final Identifier GLOW_CIRCLE = academy("core/glow_circle");
            public static final Identifier SDF_CIRCLE_GLOW = academy("core/sdf_circle_glow");
            public static final Identifier SDF_SHARP_MARGIN = academy("core/sdf_sharp_margin");
            public static final Identifier IMAGE_CIRCLE = academy("core/image_circle");
            public static final Identifier MSDF_TEXT = academy("core/msdf_text");
            public static final Identifier POS_COLOR = academy("core/pos_color");
            public static final Identifier PARTICLE_ADDITIVE = academy("core/particle_additive");
            public static final Identifier SHOCKWAVE = academy("core/shockwave");
            public static final Identifier TRAIL = academy("core/trail");
            public static final Identifier AURA_FIELD = academy("core/aura_field");
            public static final Identifier SPATIAL_DISTORTION = academy("core/spatial_distortion");
            public static final Identifier SKILL_PROGRESS = academy("core/skill_progress");
            public static final Identifier IMAGE_MONOCHROME = academy("core/image_monochrome");
            public static final Identifier PLATINUM_COSMIC_WING = academy("core/platinum_cosmic_wing");
            /**
             * AcademyCraft
             */
            public static final Identifier image = academy("core/image");
            public static final Identifier image_circle = academy("core/image_circle");
            public static final Identifier image_monochrome = academy("core/image_monochrome");
            public static final Identifier imgui = academy("core/imgui");
            public static final Identifier msdf_text = academy("core/msdf_text");
            public static final Identifier msdf_text_instanced = academy("core/msdf_text_instanced");
            public static final Identifier particle_additive = academy("core/particle_additive");
            public static final Identifier pos_color = academy("core/pos_color");
            public static final Identifier screen_blit = academy("core/screen_blit");
            public static final Identifier sdf_circle_glow = academy("core/sdf_circle_glow");
            public static final Identifier sdf_sharp_margin = academy("core/sdf_sharp_margin");
            public static final Identifier shockwave = academy("core/shockwave");
            public static final Identifier skill_progress = academy("core/skill_progress");
            public static final Identifier spatial_distortion = academy("core/spatial_distortion");
            public static final Identifier trail = academy("core/trail");

            private core() {
            }
        }

        public static final class include {
            public static final Identifier projection_utils = academy("include/projection_utils");

            private include() {
            }
        }

        private shaders() {
        }
    }

    public static final class textures {
        public static final class abilities {
            public static final class condition {
                public static final Identifier any1 = academy("textures/abilities/condition/any1.png");
                public static final Identifier any2 = academy("textures/abilities/condition/any2.png");
                public static final Identifier any3 = academy("textures/abilities/condition/any3.png");
                public static final Identifier any4 = academy("textures/abilities/condition/any4.png");
                public static final Identifier any5 = academy("textures/abilities/condition/any5.png");

                private condition() {
                }
            }

            private abilities() {
            }
        }

        public static final class ability {
            public static final class accelerator {
                public static final Identifier icon = academy("textures/ability/accelerator/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/accelerator/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/accelerator/icon_overlay.png");

                public static final class skill {
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
                        public static final class effect {
                            public static final Identifier tornado_ring = academy("textures/ability/accelerator/skill/storm_wing/effect/tornado_ring.png");

                            private effect() {
                            }
                        }

                        public static final Identifier icon = academy("textures/ability/accelerator/skill/storm_wing/icon.png");

                        private storm_wing() {
                        }
                    }

                    public static final class vec_accel {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/vec_accel/icon.png");

                        private vec_accel() {
                        }
                    }

                    public static final class vector_reduction {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/vector_reduction/icon.png");

                        private vector_reduction() {
                        }
                    }

                    public static final class vector_reflection {
                        public static final Identifier icon = academy("textures/ability/accelerator/skill/vector_reflection/icon.png");

                        private vector_reflection() {
                        }
                    }

                    private skill() {
                    }
                }

                private accelerator() {
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

                public static final class skill {
                    public static final class arc_generate {
                        public static final class effect {
                            public static final Identifier line_segment = academy("textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");

                            private effect() {
                            }
                        }

                        public static final Identifier icon = academy("textures/ability/electromaster/skill/arc_generate/icon.png");

                        private arc_generate() {
                        }
                    }

                    public static final class iron_sand_arsenal {
                        public static final Identifier icon = academy("textures/ability/electromaster/skill/iron_sand_arsenal/icon.png");

                        private iron_sand_arsenal() {
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

                    private skill() {
                    }
                }

                private electromaster() {
                }
            }

            public static final class generic {
                public static final class effect {
                    public static final Identifier glow_circle = academy("textures/ability/generic/effect/glow_circle.png");
                    public static final Identifier smokes = academy("textures/ability/generic/effect/smokes.png");
                    public static final Identifier sparkle_blurred = academy("textures/ability/generic/effect/sparkle_blurred.png");
                    public static final Identifier white_smoke_hq = academy("textures/ability/generic/effect/white_smoke_hq.png");

                    private effect() {
                    }
                }

                private generic() {
                }
            }

            public static final class level0 {
                public static final Identifier icon = academy("textures/ability/level0/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/level0/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/level0/icon_overlay.png");

                public static final class skill {
                    public static final class level0_passive_lv1 {
                        public static final Identifier icon = academy("textures/ability/level0/skill/level0_passive_lv1/icon.png");

                        private level0_passive_lv1() {
                        }
                    }

                    public static final class level0_passive_lv2 {
                        public static final Identifier icon = academy("textures/ability/level0/skill/level0_passive_lv2/icon.png");

                        private level0_passive_lv2() {
                        }
                    }

                    public static final class level0_passive_lv3 {
                        public static final Identifier icon = academy("textures/ability/level0/skill/level0_passive_lv3/icon.png");

                        private level0_passive_lv3() {
                        }
                    }

                    public static final class level0_passive_lv4 {
                        public static final Identifier icon = academy("textures/ability/level0/skill/level0_passive_lv4/icon.png");

                        private level0_passive_lv4() {
                        }
                    }

                    public static final class level0_passive_lv5 {
                        public static final Identifier icon = academy("textures/ability/level0/skill/level0_passive_lv5/icon.png");

                        private level0_passive_lv5() {
                        }
                    }

                    private skill() {
                    }
                }

                private level0() {
                }
            }

            public static final class meltdowner {
                public static final Identifier icon = academy("textures/ability/meltdowner/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/meltdowner/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/meltdowner/icon_overlay.png");

                public static final class ray {
                    public static final Identifier head = academy("textures/ability/meltdowner/ray/head.png");
                    public static final Identifier hellflare_steam = academy("textures/ability/meltdowner/ray/hellflare_steam.png");
                    public static final Identifier ray = academy("textures/ability/meltdowner/ray/ray.png");
                    public static final Identifier tail = academy("textures/ability/meltdowner/ray/tail.png");

                    private ray() {
                    }
                }

                private meltdowner() {
                }
            }

            public static final class teleport {
                public static final Identifier icon = academy("textures/ability/teleport/icon.png");
                public static final Identifier icon_glow = academy("textures/ability/teleport/icon_glow.png");
                public static final Identifier icon_overlay = academy("textures/ability/teleport/icon_overlay.png");

                private teleport() {
                }
            }

            private ability() {
            }
        }

        public static final class gui {
            public static final class app {
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

                private app() {
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

                private element() {
                }
            }

            public static final class icon {
                public static final Identifier add = academy("textures/gui/icon/add.png");
                public static final Identifier arrow_foward = academy("textures/gui/icon/arrow_foward.png");
                public static final Identifier arrow_back = academy("textures/gui/icon/arrow_back.png");
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

            private gui() {
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

        /**
         * Ability Developer
         */
        public static final Identifier MODEL_ABILITY_DEVELOPER = academy("textures/model/ability_developer.png");
        public static final Identifier UI_DEVELOPER_PANEL_LEFT = academy("textures/gui/developer/ui_developerleft.png");
        public static final Identifier PANEL_LEFT_BACK_MIDDLE = academy("textures/gui/developer/parent_background_developermachine.png");
        public static final Identifier PANEL_RIGHT_BACK = academy("textures/gui/developer/parent_background_developerright.png");
        public static final Identifier UI_DEVELOPER_PANEL_RIGHT = academy("textures/gui/developer/ui_developerright.png");
        public static final Identifier UI_DEVELOPER_SKILL_AREA_BG = academy("textures/gui/developer/skill_panel_back.png");
        public static final Identifier UI_DEVELOPER_SKILL_ICON_BG = academy("textures/gui/developer/skill_back.png");
        public static final Identifier ICON_ACCELERATOR = academy("textures/gui/icon/icon_accelerator.png");
        public static final Identifier ICON_ELECTROMASTER = academy("textures/gui/icon/icon_electromaster.png");
        public static final Identifier ICON_MELTDOWNER = academy("textures/gui/icon/icon_meltdowner.png");
        public static final Identifier ICON_NOCATEGORY = academy("textures/gui/icon/icon_nocategory.png");
        public static final Identifier ICON_TELEPORTER = academy("textures/gui/icon/icon_teleporter.png");
        public static final Identifier ICON_AEROMANIP = academy("textures/ability/aeromanip/icon.png");
        public static final Identifier ICON_DARKMATTER = academy("textures/ability/darkmatter/icon.png");
        /**
         * HUD
         */
        public static final Identifier CP_BAR_VALUE = academy("textures/hud/cp_bar_value.png");
        public static final Identifier CP_BAR_BACKGROUND = academy("textures/hud/cp_bar_background.png");
        public static final Identifier SP_BAR_VALUE = academy("textures/hud/sp_bar_value.png");
        /**
         * Wind Gen
         */
        public static final Identifier MODEL_WIND_GEN = academy("textures/model/wind_gen.png");
        public static final Identifier MODEL_WIND_GEN_TOP = academy("textures/model/wind_gen_top.png");
        public static final Identifier BLOCK_WIND_GEN_PILLAR = academy("textures/block/wind_gen_pillar.png");
        public static final Identifier ICON_WIND_GEN_BASE = academy("textures/gui/wind_gen/icon_wind_base.png");
        public static final Identifier ICON_WIND_GEN_PILLAR = academy("textures/gui/wind_gen/icon_wind_pillar.png");
        public static final Identifier ICON_WIND_GEN_TOP = academy("textures/gui/wind_gen/icon_wind_top.png");
        /**
         * Wireless Node
         */
        public static final Identifier WIRELESS_NODE_MODEL = academy("textures/model/wireless_node.png");
        public static final Identifier WIRELESS_NODE_UI = academy("textures/gui/node/ui_node.png");
        public static final Identifier WIRELESS_NODE_STATE = academy("textures/gui/node/state_node.png");
        /**
         * Omni Crafting Table
         */
        public static final Identifier OMNI_CRAFTING_UI = academy("textures/gui/omni_crafting/ui_omni_crafting.png");
        /**
         * Solar Gen
         */
        public static final Identifier SOLAR_GEN_MODEL = academy("textures/model/solar_gen.png");
        public static final Identifier ICON_SOLAR_GEN_NIGHT = academy("textures/gui/solar_gen/icon_solar_gen_night.png");
        public static final Identifier ICON_SOLAR_GEN_RAINY = academy("textures/gui/solar_gen/icon_solar_gen_rainy.png");
        public static final Identifier ICON_SOLAR_GEN_SUNNY = academy("textures/gui/solar_gen/icon_solar_gen_sunny.png");
        /**
         * Common
         */
        public static final Identifier ARROW_BACK = academy("textures/gui/icon/arrow_back.png");
        public static final Identifier LOGO_TECH = academy("textures/gui/element/logo_tech.png");
        public static final Identifier ELEMENT_LINE = academy("textures/gui/element/line.png");
        public static final Identifier UI_INVENTORY = academy("textures/gui/element/ui_inventory.png");
        public static final Identifier UI_GEN = academy("textures/gui/element/ui_gen.png");
        public static final Identifier ICON_OPEN_WIRELESS_PANEL = academy("textures/gui/icon/icon_tonode.png");
        public static final Identifier ICON_NODE = academy("textures/gui/icon/icon_node.png");
        public static final Identifier ICON_CONNECTED = academy("textures/gui/icon/icon_connected.png");
        public static final Identifier ICON_UNCONNECTED = academy("textures/gui/icon/icon_unconnected.png");
        public static final Identifier ICON_INV = academy("textures/gui/icon/icon_inv.png");
        public static final Identifier ICON_WIRELESS = academy("textures/gui/icon/icon_wireless.png");
        public static final Identifier ICON_BOX = academy("textures/gui/icon/icon_box.png");
        public static final Identifier ICON_CLOSE = academy("textures/gui/icon/icon_close.png");
        /**
         * Terminal
         */
        public static final Identifier ICON_TERMINAL = academy("textures/gui/terminal/icon.png");
        public static final Identifier APP_BACK = academy("textures/gui/element/app_back.png");
        /**
         * Electromaster
         */
        public static final Identifier arc = academy("textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");
        public static final Identifier arc_generate_icon = academy("textures/ability/electromaster/skill/arc_generate/icon.png");
        public static final Identifier magnet_manipulation_icon = academy("textures/ability/electromaster/skill/magnet_manipulation/icon.png");
        public static final Identifier mine_detect_icon = academy("textures/ability/electromaster/skill/mine_detect/icon.png");
        public static final Identifier thunder_lance_icon = academy("textures/ability/electromaster/skill/thunder_lance/icon.png");
        public static final Identifier electromagnetic_shield_icon = academy("textures/ability/electromaster/skill/electromagnetic_shield/icon.png");
        public static final Identifier current_recharge_icon = academy("textures/ability/electromaster/skill/current_recharge/icon.png");
        public static final Identifier current_symbiosis_icon = academy("textures/ability/electromaster/skill/current_symbiosis/icon.png");
        public static final Identifier bioelectric_operation_icon = academy("textures/ability/electromaster/skill/bioelectric_operation/icon.png");
        public static final Identifier railgun_icon = academy("textures/ability/electromaster/skill/railgun/icon.png");
        public static final Identifier thunderclap_icon = academy("textures/ability/electromaster/skill/thunderclap/icon.png");
        public static final Identifier iron_sand_arsenal_icon = academy("textures/ability/electromaster/skill/iron_sand_arsenal/icon.png");
        /**
         * Meltdowner
         */
        public static final Identifier single_high_speed_electron_beam_icon = academy("textures/ability/meltdowner/skill/single_high_speed_electron_beam/icon.png");
        public static final Identifier scatter_bomb_icon = academy("textures/ability/meltdowner/skill/scatter_bomb/icon.png");
        public static final Identifier radiation_intensify_icon = academy("textures/ability/meltdowner/skill/radiation_intensify/icon.png");
        public static final Identifier mining_beam_icon = academy("textures/ability/meltdowner/skill/mining_beam/icon.png");
        public static final Identifier light_shield_icon = academy("textures/ability/meltdowner/skill/light_shield/icon.png");
        public static final Identifier light_shield_effect = academy("textures/ability/generic/skill/light_shield/effect/mdshield.png");
        public static final Identifier particle_wave_cannon_icon = academy("textures/ability/meltdowner/skill/particle_wave_cannon/icon.png");
        public static final Identifier jet_strike_icon = academy("textures/ability/meltdowner/skill/jet_strike/icon.png");
        public static final Identifier auto_cruise_beam_cannon_icon = academy("textures/ability/meltdowner/skill/auto_cruise_beam_cannon/icon.png");
        /**
         * Teleport
         */
        public static final Identifier threatening_teleport_icon = academy("textures/ability/teleport/skill/threatening_teleport/icon.png");
        public static final Identifier space_folding_theorem_icon = academy("textures/ability/teleport/skill/space_folding_theorem/icon.png");
        public static final Identifier self_teleport_icon = academy("textures/ability/teleport/skill/self_teleport/icon.png");
        public static final Identifier cut_through_icon = academy("textures/ability/teleport/skill/cut_through/icon.png");
        public static final Identifier flesh_ripping_icon = academy("textures/ability/teleport/skill/flesh_ripping/icon.png");
        public static final Identifier location_teleport_icon = academy("textures/ability/teleport/skill/location_teleport/icon.png");
        public static final Identifier quick_location_teleport_icon = academy("textures/ability/teleport/skill/quick_location_teleport/icon.png");
        public static final Identifier area_teleport_select_icon = academy("textures/ability/teleport/skill/area_teleport_select/icon.png");
        public static final Identifier area_teleport_setup_icon = academy("textures/ability/teleport/skill/area_teleport_setup/icon.png");
        public static final Identifier area_teleport_start_icon = academy("textures/ability/teleport/skill/area_teleport_start/icon.png");
        public static final Identifier flashing_icon = academy("textures/ability/teleport/skill/flashing/icon.png");
        public static final Identifier defensive_teleport_icon = academy("textures/ability/teleport/skill/defensive_teleport/icon.png");
        /**
         * Darkmatter
         */
        public static final Identifier darkmatter_shaping_icon = academy("textures/ability/darkmatter/skill/darkmatter_shaping/icon.png");
        public static final Identifier darkmatter_disassemble_icon = academy("textures/ability/darkmatter/skill/darkmatter_disassemble/icon.png");
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
        /**
         * Accelerator
         */
        public static final Identifier vector_reflection_icon = academy("textures/ability/accelerator/skill/vector_reflection/icon.png");
        public static final Identifier reflection_filter_icon = academy("textures/ability/accelerator/skill/reflection_filter/icon.png");
        public static final Identifier vector_blast_icon = academy("textures/ability/accelerator/skill/vector_blast/icon.png");
        public static final Identifier kinetic_energy_applied_icon = academy("textures/ability/accelerator/skill/kinetic_energy_applied/icon.png");
        public static final Identifier bloodflow_reverse_icon = academy("textures/ability/accelerator/skill/bloodflow_reverse/icon.png");
        public static final Identifier storm_wing_icon = academy("textures/ability/accelerator/skill/storm_wing/icon.png");
        public static final Identifier black_wing_icon = academy("textures/ability/accelerator/skill/black_wing/icon.png");
        public static final Identifier white_wing_icon = academy("textures/ability/accelerator/skill/white_wing/icon.png");
        public static final Identifier platinum_wing_icon = academy("textures/ability/accelerator/skill/platinum_wing/icon.png");
        public static final Identifier crossing_the_abyss_icon = academy("textures/ability/accelerator/skill/crossing_the_abyss/icon.png");
        public static final Identifier plasma_generation_icon = academy("textures/ability/accelerator/skill/plasma_generation/icon.png");
        public static final Identifier plasma_generation_effect = academy("textures/ability/accelerator/skill/plasma_generation/effect/plasma.png");
        public static final Identifier plasma_generation_cloud = academy("textures/ability/accelerator/skill/plasma_generation/effect/white_smoke_hq.png");
        public static final Identifier dir_strike_icon = academy("textures/ability/accelerator/skill/dir_strike/icon.png");
        public static final Identifier storm_wing = academy("textures/ability/accelerator/skill/storm_wing/effect/tornado_ring.png");
        public static final Identifier black_wing = academy("textures/ability/accelerator/skill/black_wing/effect/tornado_ring.png");
        public static final Identifier white_wing = academy("textures/ability/accelerator/skill/white_wing/effect/tornado_ring.png");
        public static final Identifier platinum_wing_starfield = academy("textures/ability/accelerator/skill/platinum_wing/effect/starfield.png");
        public static final Identifier vec_accel_icon = academy("textures/ability/accelerator/skill/vec_accel/icon.png");
        public static final Identifier vector_reduction_icon = academy("textures/ability/accelerator/skill/vector_reduction/icon.png");
        /**
         * Aeromanip
         */
        public static final Identifier airflow_jet_icon = academy("textures/ability/aeromanip/skill/airflow_jet/icon.png");
        public static final Identifier atmosphere_shield_icon = academy("textures/ability/aeromanip/skill/atmosphere_shield/icon.png");
        public static final Identifier breathing_film_icon = academy("textures/ability/aeromanip/skill/breathing_film/icon.png");
        public static final Identifier atmosphere_blast_gun_icon = academy("textures/ability/aeromanip/skill/atmosphere_blast_gun/icon.png");
        public static final Identifier flight_icon = academy("textures/ability/aeromanip/skill/flight/icon.png");
        public static final Identifier vacuum_domain_icon = academy("textures/ability/aeromanip/skill/vacuum_domain/icon.png");
        /**
         * Level0
         */
        public static final Identifier level0_passive_lv1_icon = academy("textures/ability/level0/skill/level0_passive_lv1/icon.png");
        public static final Identifier level0_passive_lv2_icon = academy("textures/ability/level0/skill/level0_passive_lv2/icon.png");
        public static final Identifier level0_passive_lv3_icon = academy("textures/ability/level0/skill/level0_passive_lv3/icon.png");
        public static final Identifier level0_passive_lv4_icon = academy("textures/ability/level0/skill/level0_passive_lv4/icon.png");
        public static final Identifier level0_passive_lv5_icon = academy("textures/ability/level0/skill/level0_passive_lv5/icon.png");
        /**
         * Condition
         */
        public static final Identifier condition_any1 = academy("textures/ability/condition/any1.png");
        public static final Identifier condition_any2 = academy("textures/ability/condition/any2.png");
        public static final Identifier condition_any3 = academy("textures/ability/condition/any3.png");
        public static final Identifier condition_any4 = academy("textures/ability/condition/any4.png");
        public static final Identifier condition_any5 = academy("textures/ability/condition/any5.png");
        /**
         * Imag Phase Dowsing Rod
         */
        public static final Identifier IMAG_PHASE_DOWSING_ROD = academy("textures/model/imag_phase_dowsing_rod.png");
        /**
         * Omni Crafting Table
         */
        public static final Identifier OMNI_CRAFTING_TABLE = academy("textures/model/omni_crafting_table.png");
        /**
         * Cat Engine
         */
        public static final Identifier CAT_ENGINE = academy("textures/item/cat_engine.png");
        /**
         * Cleaning Robot
         */
        public static final Identifier CLEANING_ROBOT = academy("textures/model/cleaning_robot.png");
        /**
         * Settings
         */
        public static final Identifier ICON_SETTINGS = academy("textures/gui/icon/icon_settings.png");
        /**
         * Music Player
         */
        private static final String MUSIC = "textures/gui/app/music/";
        public static final Identifier ICON_NOW_PLAYING = academy(MUSIC + "now_playing.png");
        public static final Identifier ICON_MUSIC_PLAYER = academy(MUSIC + "icon.png");
        public static final Identifier ICON_CYCLE = academy(MUSIC + "cycle.png");
        public static final Identifier ICON_RANDOM_PLAY = academy(MUSIC + "random_play.png");
        public static final Identifier ICON_SINGLE_CYCLE = academy(MUSIC + "single_cycle.png");
        public static final Identifier ICON_NEXT = academy(MUSIC + "next.png");
        public static final Identifier ICON_PREV = academy(MUSIC + "previous.png");
        public static final Identifier ICON_PAUSE = academy(MUSIC + "pause.png");
        public static final Identifier ICON_PLAY = academy(MUSIC + "play.png");
        public static final Identifier ICON_VOLUME = academy(MUSIC + "volume.png");

        private textures() {
        }
    }
}
