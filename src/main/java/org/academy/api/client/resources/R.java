package org.academy.api.client.resources;

import net.minecraft.resources.Identifier;

import static org.academy.AcademyCraft.academy;
import static org.academy.AcademyCraft.vanilla;

public final class R {
    private R() {
    }

    /**
     * Normally, vert and frag share the same name.
     */
    public static final class shaders {
        /**
         * Vanilla
         */
        public static final Identifier POSITION_TEX = vanilla("core/position_tex");
        public static final Identifier POSITION_COLOR = vanilla("core/position_color");
        public static final Identifier POSITION_TEX_COLOR = vanilla("core/position_tex_color");
        public static final Identifier POSITION_COLOR_LIGHTMAP = vanilla("core/position_color_lightmap");
        public static final Identifier SCREEN_BLIT = academy("core/screen_blit");
        public static final Identifier DISTORTION_RING = academy("core/distortion_ring");

        private shaders() {
        }

        public static final class core {
            public static final Identifier imgui = academy("core/imgui");
            public static final Identifier msdf_text_instanced = academy("core/msdf_text_instanced");
            public static final Identifier BLOOM_BLEND = academy("core/bloom_blend");
            public static final Identifier GAUSSIAN_BLUR = academy("core/gaussian_blur");
            public static final Identifier GLOW_CIRCLE = academy("core/glow_circle");
            public static final Identifier SDF_CIRCLE_GLOW = academy("core/sdf_circle_glow");
            public static final Identifier SDF_SHARP_MARGIN = academy("core/sdf_sharp_margin");
            public static final Identifier IMAGE_CIRCLE = academy("core/image_circle");
            public static final Identifier MSDF_TEXT = academy("core/msdf_text");
            public static final Identifier HELLFLARE_STEAM = academy("core/hellflare_steam");
            public static final Identifier POS_COLOR = academy("core/pos_color");
            public static final Identifier PARTICLE_ADDITIVE = academy("core/particle_additive");
            public static final Identifier SHOCKWAVE = academy("core/shockwave");
            public static final Identifier TRAIL = academy("core/trail");
            public static final Identifier AURA_FIELD = academy("core/aura_field");
            public static final Identifier SPATIAL_DISTORTION = academy("core/spatial_distortion");
            public static final Identifier SKILL_PROGRESS = academy("core/skill_progress");
            public static final Identifier IMAGE_MONOCHROME = academy("core/image_monochrome");
            /**
             * AcademyCraft
             */
            public static final Identifier image = academy("core/image");

            private core() {
            }
        }
    }

    public static final class textures {
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
        /**
         * HUD
         */
        public static final Identifier CP_BAR_VALUE = academy("textures/hud/cp_bar_value.png");
        public static final Identifier CP_BAR_BACKGROUND = academy("textures/hud/cp_bar_background.png");
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
        public static final Identifier railgun_icon = academy("textures/ability/electromaster/skill/railgun/icon.png");
        public static final Identifier thunderclap_icon = academy("textures/ability/electromaster/skill/thunderclap/icon.png");
        public static final Identifier iron_sand_arsenal_icon = academy("textures/ability/electromaster/skill/iron_sand_arsenal/icon.png");
        /**
         * Accelerator
         */
        public static final Identifier vector_reflection_icon = academy("textures/ability/accelerator/skill/vector_reflection/icon.png");
        public static final Identifier bloodflow_reverse_icon = academy("textures/ability/accelerator/skill/bloodflow_reverse/icon.png");
        public static final Identifier storm_wing_icon = academy("textures/ability/accelerator/skill/storm_wing/icon.png");
        public static final Identifier plasma_generation_icon = academy("textures/ability/accelerator/skill/plasma_generation/icon.png");
        public static final Identifier dir_strike_icon = academy("textures/ability/accelerator/skill/dir_strike/icon.png");
        public static final Identifier storm_wing = academy("textures/ability/accelerator/skill/storm_wing/effect/tornado_ring.png");
        public static final Identifier directed_shock_icon = academy("textures/ability/accelerator/skill/directed_shock/icon.png");
        public static final Identifier vec_accel_icon = academy("textures/ability/accelerator/skill/vec_accel/icon.png");
        public static final Identifier vector_reduction_icon = academy("textures/ability/accelerator/skill/vector_reduction/icon.png");
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
