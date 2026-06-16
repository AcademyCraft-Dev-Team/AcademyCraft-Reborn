package org.academy.api.client;

import net.minecraft.resources.Identifier;

import static org.academy.AcademyCraft.academy;
import static org.academy.AcademyCraft.vanilla;

public final class Resource {
    /**
     * Normally, vert and frag share the same name.
     */
    public static final class Shaders {
        /**
         * Vanilla
         */
        public static final Identifier POSITION_TEX = vanilla("core/position_tex");
        public static final Identifier POSITION_COLOR = vanilla("core/position_color");
        public static final Identifier POSITION_TEX_COLOR = vanilla("core/position_tex_color");
        public static final Identifier POSITION_COLOR_LIGHTMAP = vanilla("core/position_color_lightmap");
        /**
         * AcademyCraft
         */
        public static final Identifier IMAGE = academy("core/image");
        public static final Identifier SCREEN_BLIT = academy("core/screen_blit");
        public static final Identifier DISTORTION_RING = academy("core/distortion_ring");
        public static final Identifier DISTORTION_TUBE = academy("core/distortion_tube");

        /**
         * Vert only.
         */
        public static final class Vertex {

            private Vertex() {
            }
        }

        /**
         * Frag only.
         */
        public static final class Fragment {
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

            private Fragment() {
            }
        }

        private Shaders() {
        }
    }

    public static final class Textures {
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
        public static final Identifier ARC = academy("textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");
        public static final Identifier RAILGUN_ICON = academy("textures/ability/electromaster/skill/railgun/icon.png");
        public static final Identifier ARC_GENERATE_ICON = academy("textures/ability/electromaster/skill/railgun/icon.png");
        /**
         * Accelerator
         */
        public static final Identifier VECTOR_REFLECTION_ICON = academy("textures/ability/accelerator/skill/vector_reflection/icon.png");
        public static final Identifier BLOODFLOW_REVERSE_ICON = academy("textures/ability/accelerator/skill/bloodflow_reverse/icon.png");
        public static final Identifier STORM_WING_ICON = academy("textures/ability/accelerator/skill/storm_wing/icon.png");
        public static final Identifier PLASMA_GENERATION_ICON = academy("textures/ability/accelerator/skill/plasma_generation/icon.png");
        public static final Identifier DIR_STRIKE_ICON = academy("textures/ability/accelerator/skill/dir_strike/icon.png");
        public static final Identifier STORM_WING = academy("textures/ability/accelerator/skill/storm_wing/effect/tornado_ring.png");
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
        /**
         * Settings
         */
        public static final Identifier ICON_SETTINGS = academy("textures/gui/icon/icon_settings.png");

        private Textures() {
        }
    }

    public static final class Models {
        public static final Identifier COIN_ITEM_MODEL_ID = academy("builtin/coin");
    }

    private Resource() {
    }
}
