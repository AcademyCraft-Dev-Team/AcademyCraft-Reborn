package org.academy.api.client;

import net.minecraft.resources.ResourceLocation;

import static org.academy.AcademyCraft.academy;
import static org.academy.AcademyCraft.vanilla;

public final class Resource {
    /**
     * Normally, vert & frag share the same name.
     */
    public static final class Shaders {
        /**
         * Vanilla
         */
        public static final ResourceLocation POS_TEX = vanilla("core/position_tex");
        public static final ResourceLocation POS_COLOR = vanilla("core/position_color");
        public static final ResourceLocation POS_TEX_COLOR = vanilla("core/position_tex_color");
        public static final ResourceLocation POS_COLOR_LIGHTMAP = vanilla("core/position_color_lightmap");
        /**
         * AcademyCraft
         */
        public static final ResourceLocation SCREEN_BLIT = academy("core/screen_blit");
        public static final ResourceLocation DISTORTION_RING = academy("core/distortion_ring");

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
            public static final ResourceLocation BLOOM_BLEND = academy("core/bloom_blend");
            public static final ResourceLocation GAUSSIAN_BLUR = academy("core/gaussian_blur");
            public static final ResourceLocation MASKED_BLUR = academy(("core/masked_blur"));
            public static final ResourceLocation GLOW_CIRCLE = academy("core/glow_circle");
            public static final ResourceLocation SDF_CIRCLE_GLOW = academy("core/sdf_circle_glow");
            public static final ResourceLocation SDF_SHARP_MARGIN = academy("core/sdf_sharp_margin");


            private Fragment() {
            }
        }

        private Shaders() {
        }
    }

    public static final class Textures {
        public static final ResourceLocation ARC = academy("textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");
        public static final ResourceLocation ELEMENT_LINE = academy("textures/gui/element/line.png");
        /**
         * Ability Developer
         */
        public static final ResourceLocation MODEL_ABILITY_DEVELOPER = academy("textures/model/ability_developer.png");
        public static final ResourceLocation UI_DEVELOPER_PANEL_LEFT = academy("textures/gui/developer/ui_developerleft.png");
        public static final ResourceLocation PANEL_LEFT_BACK_MIDDLE = academy("textures/gui/developer/parent_background_developermachine.png");
        public static final ResourceLocation PANEL_RIGHT_BACK = academy("textures/gui/developer/parent_background_developerright.png");
        public static final ResourceLocation UI_DEVELOPER_PANEL_RIGHT = academy("textures/gui/developer/ui_developerright.png");
        public static final ResourceLocation UI_DEVELOPER_SKILL_AREA_BG = academy("textures/gui/developer/skill_panel_back.png");
        public static final ResourceLocation UI_DEVELOPER_SKILL_ICON_BG = academy("textures/gui/developer/skill_back.png");
        /**
         * HUD
         */
        public static final ResourceLocation CP_BAR = academy("textures/hud/cp_bar.png");
        public static final ResourceLocation CP_BAR_VALUE = academy("textures/hud/cp_bar_value.png");
        public static final ResourceLocation CP_BAR_BACKGROUND = academy("textures/hud/cp_bar_background.png");
        /**
         * Wind Gen
         */
        public static final ResourceLocation MODEL_WIND_GEN = academy("textures/model/wind_gen.png");
        public static final ResourceLocation MODEL_WIND_GEN_TOP = academy("textures/model/wind_gen_top.png");
        public static final ResourceLocation BLOCK_WIND_GEN_PILLAR =  academy("textures/block/wind_gen_pillar.png");
        public static final ResourceLocation ICON_WIND_GEN_BASE = academy("textures/gui/wind_gen/icon_wind_base.png");
        public static final ResourceLocation ICON_WIND_GEN_PILLAR = academy("textures/gui/wind_gen/icon_wind_pillar.png");
        public static final ResourceLocation ICON_WIND_GEN_TOP = academy("textures/gui/wind_gen/icon_wind_top.png");
        /**
         * Wireless Node
         */
        public static final ResourceLocation WIRELESS_NODE_MODEL = academy("textures/model/wireless_node.png");
        public static final ResourceLocation WIRELESS_NODE_UI = academy("textures/gui/node/ui_node.png");
        public static final ResourceLocation WIRELESS_NODE_STATE = academy("textures/gui/node/state_node.png");
        /**
         * Omni Crafting Table
         */
        public static final ResourceLocation OMNI_CRAFTING_UI = academy("textures/gui/omni_crafting/ui_omni_crafting.png");
        /**
         * Solar Gen
         */
        public static final ResourceLocation SOLAR_GEN_MODEL = academy("textures/model/solar_gen.png");
        public static final ResourceLocation ICON_SOLAR_GEN_NIGHT = academy("textures/gui/solar_gen/icon_solar_gen_night.png");
        public static final ResourceLocation ICON_SOLAR_GEN_RAINY = academy("textures/gui/solar_gen/icon_solar_gen_rainy.png");
        public static final ResourceLocation ICON_SOLAR_GEN_SUNNY = academy("textures/gui/solar_gen/icon_solar_gen_sunny.png");
        /**
         * Common
         */
        public static final ResourceLocation UI_INVENTORY = academy("textures/gui/element/ui_inventory.png");
        public static final ResourceLocation UI_GEN = academy("textures/gui/element/ui_gen.png");
        public static final ResourceLocation UI_BACKGROUND_LIGHT = academy("textures/gui/element/element_background_light.png");
        public static final ResourceLocation ICON_OPEN_WIRELESS_PANEL = academy("textures/gui/icon/icon_tonode.png");
        public static final ResourceLocation ICON_NODE = academy("textures/gui/icon/icon_node.png");
        public static final ResourceLocation ICON_CONNECTED = academy("textures/gui/icon/icon_connected.png");
        public static final ResourceLocation ICON_UNCONNECTED = academy("textures/gui/icon/icon_unconnected.png");
        public static final ResourceLocation ICON_INV = academy("textures/gui/icon/icon_inv.png");
        public static final ResourceLocation ICON_WIRELESS = academy("textures/gui/icon/icon_wireless.png");
        public static final ResourceLocation UI_BUTTON_LEARN = academy("textures/gui/element/button.png");
        public static final ResourceLocation HUD_SKILL_FRAME = academy("textures/gui/icon/icon_box.png");
        /**
         * Data Terminal
         */
        public static final ResourceLocation ICON_DATA_TERMINAL = academy("textures/gui/icon/icon_data_terminal.png");
        public static final ResourceLocation APP_BACK = academy("textures/gui/element/app_back.png");
        /**
         * Electromaster
         */
        public static final ResourceLocation RAILGUN_ICON = academy("textures/ability/electromaster/skill/railgun/icon.png");
        public static final ResourceLocation ARC_GENERATE_ICON = academy("textures/ability/electromaster/skill/railgun/icon.png");
        /**
         * Accelerator
         */
        public static final ResourceLocation VECTOR_REFLECTION_ICON = academy("textures/ability/accelerator/skill/vector_reflection/icon.png");
        public static final ResourceLocation BLOODFLOW_REVERSE_ICON = academy("textures/ability/accelerator/skill/bloodflow_reverse/icon.png");
        public static final ResourceLocation STORM_WING_ICON = academy("textures/ability/accelerator/skill/storm_wing/icon.png");
        public static final ResourceLocation PLASMA_GENERATION_ICON = academy("textures/ability/accelerator/skill/plasma_generation/icon.png");
        public static final ResourceLocation DIR_STRIKE_ICON = academy("textures/ability/accelerator/skill/dir_strike/icon.png");
        /**
         * Imag Phase Dowsing Rod
         */
        public static final ResourceLocation IMAG_PHASE_DOWSING_ROD = academy("textures/model/imag_phase_dowsing_rod.png");
        /**
         * Omni Crafting Table
         */
        public static final ResourceLocation OMNI_CRAFTING_TABLE = academy("textures/model/omni_crafting_table.png");
        /**
         * Cat Engine
         */
        public static final ResourceLocation CAT_ENGINE = academy("textures/item/cat_engine.png");
        /**
         * Cleaning Robot
         */
        public static final ResourceLocation CLEANING_ROBOT = academy("textures/model/cleaning_robot.png");
        /**
         * Music Player
         */
        public static final ResourceLocation ICON_MUSIC_PLAYER = academy("textures/gui/icon/icon_music_player.png");
        public static final ResourceLocation ICON_CYCLE = academy("textures/gui/icon/icon_cycle.png");
        public static final ResourceLocation ICON_RANDOM = academy("textures/gui/icon/icon_random.png");
        public static final ResourceLocation ICON_SINGLE_CYCLE = academy("textures/gui/icon/icon_single_cycle.png");
        /**
         * Settings
         */
        public static final ResourceLocation ICON_SETTINGS = academy("textures/gui/icon/icon_settings.png");

        private Textures() {
        }
    }

    public static final class Models {
        public static final ResourceLocation COIN_ITEM_MODEL_ID = academy("builtin/coin");
    }

    private Resource() {
    }
}