package org.academy.api.client.resource;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;

import java.util.function.BiFunction;

public final class TextureResources {
    /**
     * Ability Developer
     */
    public static final ResourceLocation TEXTURE_PANEL_LEFT_BACK_TOP = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerleft.png");
    public static final ResourceLocation TEXTURE_PANEL_LEFT_BACK_MIDDLE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developermachine.png");
    public static final ResourceLocation TEXTURE_PANEL_RIGHT_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developerright.png");
    public static final ResourceLocation TEXTURE_PANEL_RIGHT_INFO = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerright.png");
    public static final ResourceLocation TEXTURE_PANEL_RIGHT_SKILL_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_panel_back.png");
    public static final ResourceLocation TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_back.png");
    /**
     * HUD
     */
    public static final ResourceLocation TEXTURE_COMPUTING_POWER_BAR = new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/computing_power_bar.png");
    public static final ResourceLocation TEXTURE_COMPUTING_POWER_BAR_BACKGROUND = new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/computing_power_bar_background.png");
    /**
     * Wind Gen
     */
    public static final ResourceLocation TEXTURE_WIND_GEN_MODEL = new ResourceLocation(AcademyCraft.MOD_ID, "textures/model/wind_gen.png");
    public static final ResourceLocation TEXTURE_WIND_GEN_UI = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/windgen/ui_windgen.png");
    public static final ResourceLocation TEXTURE_ICON_WIND_GEN_BASE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wind_base.png");
    public static final ResourceLocation TEXTURE_ICON_WIND_GEN_PILLAR = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wind_pillar.png");
    public static final ResourceLocation TEXTURE_ICON_WIND_GEN_TOP = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wind_top.png");
    /**
     * Wireless Node
     */
    public static final ResourceLocation TEXTURE_WIRELESS_NODE_UI = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/node/ui_node.png");
    public static final ResourceLocation TEXTURE_WIRELESS_NODE_STATE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/node/state_node.png");
    /**
     * Common
     */
    public static final ResourceLocation TEXTURE_INVENTORY = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/ui_inventory.png");
    public static final ResourceLocation TEXTURE_ELEMENT_LINE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/line.png");
    public static final ResourceLocation TEXTURE_ELEMENT_BACK_DARK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_dark.png");
    public static final ResourceLocation TEXTURE_ELEMENT_BACK_LIGHT = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_light.png");
    public static final ResourceLocation TEXTURE_WIRELESS_PANEL_VIEW_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_tonode.png");
    public static final ResourceLocation TEXTURE_ICON_NODE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_node.png");
    public static final ResourceLocation TEXTURE_ICON_CONNECTED = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_connected.png");
    public static final ResourceLocation TEXTURE_ICON_UNCONNECTED = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_unconnected.png");
    public static final ResourceLocation TEXTURE_ICON_INV = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_inv.png");
    public static final ResourceLocation TEXTURE_ICON_WIRELESS = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wireless.png");
    public static final ResourceLocation TEXTURE_HISTOGRAM = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/histogram.png");
    public static final ResourceLocation TEXTURE_BLEND_QUAD = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/blend_quad.png");
    public static final ResourceLocation TEXTURE_BUTTON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/button.png");
    public static final ResourceLocation TEXTURE_ICON_BOX = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_box.png");
    public static final ResourceLocation TEXTURE_CURSOR = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/cursor.png");
    /**
     * Meltdowner
     */
    public static final ResourceLocation TEXTURE_MELTDOWNER_RAY_GLOW = new ResourceLocation(AcademyCraft.MOD_ID,"textures/ability/meltdowner/ray/ray.png");
    public static final ResourceLocation TEXTURE_MELTDOWNER_HEAD_GLOW = new ResourceLocation(AcademyCraft.MOD_ID,"textures/ability/meltdowner/ray/head.png");
    public static final ResourceLocation TEXTURE_MELTDOWNER_TAIL_GLOW = new ResourceLocation(AcademyCraft.MOD_ID,"textures/ability/meltdowner/ray/tail.png");
    /**
     * Electromaster
     */
    public static final ResourceLocation TEXTURE_RAILGUN_ICON = new ResourceLocation(AcademyCraft.MOD_ID,"textures/ability/electromaster/skill/railgun/icon.png");
    public static final ResourceLocation TEXTURE_ARC_GENERATE_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/electromaster/skill/railgun/icon.png");
    /**
     * Accelerator
     */
    public static final ResourceLocation TEXTURE_VECTOR_REFLECTION_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/accelerator/skill/vector_reflection/icon.png");
    public static final ResourceLocation TEXTURE_BLOODFLOW_REVERSE_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/accelerator/skill/bloodflow_reverse/icon.png");
    public static final ResourceLocation TEXTURE_STORM_WING_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/accelerator/skill/storm_wing/icon.png");
    public static final ResourceLocation TEXTURE_PLASMA_GENERATION_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/accelerator/skill/plasma_generation/icon.png");
    public static final ResourceLocation TEXTURE_DIR_STRIKE_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/accelerator/skill/dir_strike/icon.png");

    public static final class RenderTypes {
        /**
         * Ability Developer
         */
        public static final BiFunction<String, ResourceLocation, RenderType> RENDER_TYPE_SKILL_ICON =
                (string, resourceLocation) ->
                        RenderUtil.getPositionColorTexRenderType(string, resourceLocation, false);
        public static final RenderType DEBUG = new RenderType.CompositeRenderType(
                "debug_aaa",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                64,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateUtil.DEBUG)
                        .setCullState(RenderStateUtil.NO_CULL)
                        .setWriteMaskState(RenderStateUtil.COLOR_WRITE)
                        .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_TOP = RenderUtil.getPositionColorTexRenderType(
                "panel_left_back_top", TEXTURE_PANEL_LEFT_BACK_TOP, false);
        public static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_MIDDLE = RenderUtil.getPositionColorTexRenderType(
                "panel_left_back_middle", TEXTURE_PANEL_LEFT_BACK_MIDDLE, false);
        public static final RenderType RENDER_TYPE_SKILL_PANEL_BACK = RenderUtil.getPositionTexRenderType(
                "skill_panel_back", TEXTURE_PANEL_RIGHT_SKILL_BACK, true);
        public static final RenderType RENDER_TYPE_SKILL_PANEL_INFO = RenderUtil.getPositionColorTexRenderType(
                "skill_panel_info", TEXTURE_PANEL_RIGHT_INFO, false);
        public static final RenderType RENDER_TYPE_PANEL_RIGHT_BACK = RenderUtil.getPositionColorTexRenderType(
                "panel_right_back", TEXTURE_PANEL_RIGHT_BACK, false);
        public static final RenderType RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK = RenderUtil.getPositionColorTexRenderType(
                "panel_right_skill_icon_back", TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK, true);
        /**
         * WindGen
         */
        public static final RenderType RENDER_TYPE_WIND_GEN_UI = RenderUtil.getPositionColorTexRenderTypeFull(
                "windgen_ui", TEXTURE_WIND_GEN_UI, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_BASE = RenderUtil.getPositionColorTexRenderTypeFull(
                "icon_wind_gen_base", TEXTURE_ICON_WIND_GEN_BASE, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_PILLAR = RenderUtil.getPositionColorTexRenderTypeFull(
                "icon_wind_gen_pillar", TEXTURE_ICON_WIND_GEN_PILLAR, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_TOP = RenderUtil.getPositionColorTexRenderTypeFull(
                "icon_wind_gen_top", TEXTURE_ICON_WIND_GEN_TOP, false
        );
        /**
         * Wireless Node
         */
        public static final RenderType RENDER_TYPE_WIRELESS_NODE_UI = RenderUtil.getPositionColorTexRenderType(
                "wireless_node_ui", TEXTURE_WIRELESS_NODE_UI, false
        );
        public static final RenderType RENDER_TYPE_WIRELESS_NODE_STATE = RenderUtil.getPositionTexRenderType(
                "wireless_node_state", TEXTURE_WIRELESS_NODE_STATE, false
        );
        /**
         * Common
         */
        public static final RenderType RENDER_TYPE_INVENTORY = RenderUtil.getPositionColorTexRenderType(
                "inventory", TEXTURE_INVENTORY, false
        );
        public static final RenderType RENDER_TYPE_ELEMENT_LINE = RenderUtil.getPositionColorTexRenderType(
                "element_line", TEXTURE_ELEMENT_LINE, true);
        public static final RenderType RENDER_TYPE_ELEMENT_BACK_DARK = RenderUtil.getPositionColorTexRenderType(
                "element_back_dark", TEXTURE_ELEMENT_BACK_DARK, false);
        public static final RenderType RENDER_TYPE_ELEMENT_BACK_LIGHT = RenderUtil.getPositionColorTexRenderType(
                "element_back_light", TEXTURE_ELEMENT_BACK_LIGHT, false);
        public static final RenderType RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON = RenderUtil.getPositionColorTexRenderType(
                "wireless_panel_view_icon", TEXTURE_WIRELESS_PANEL_VIEW_ICON, false);
        public static final RenderType RENDER_TYPE_ICON_NODE = RenderUtil.getPositionColorTexRenderType(
                "icon_node", TEXTURE_ICON_NODE, false);
        public static final RenderType RENDER_TYPE_ICON_CONNECTED = RenderUtil.getPositionColorTexRenderType(
                "icon_connected", TEXTURE_ICON_CONNECTED, false);
        public static final RenderType RENDER_TYPE_ICON_UNCONNECTED = RenderUtil.getPositionColorTexRenderType(
                "icon_unconnected", TEXTURE_ICON_UNCONNECTED, false);
        public static final RenderType RENDER_TYPE_ICON_INV = RenderUtil.getPositionColorTexRenderType(
                "icon_inv", TEXTURE_ICON_INV, false);
        public static final RenderType RENDER_TYPE_ICON_WIRELESS = RenderUtil.getPositionColorTexRenderType(
                "icon_wireless", TEXTURE_ICON_WIRELESS, false);
        public static final RenderType RENDER_TYPE_HISTOGRAM = RenderUtil.getPositionColorTexRenderType(
                "histogram", TEXTURE_HISTOGRAM, false
        );
        public static final RenderType RENDER_TYPE_BLEND_QUAD = RenderUtil.getPositionColorTexRenderTypeFull(
                "blend_quad", TEXTURE_BLEND_QUAD, true
        );
        public static final RenderType RENDER_TYPE_BUTTON = RenderUtil.getPositionColorTexRenderType(
                "button", TEXTURE_BUTTON, false
        );
        public static final RenderType RENDER_TYPE_ICON_BOX = RenderUtil.getPositionTexRenderType(
                "icon_box", TEXTURE_ICON_BOX, false
        );
        public static final RenderType RENDER_TYPE_CURSOR = RenderUtil.getPositionTexRenderType(
                "cursor", TEXTURE_CURSOR, true
        );
    }
}