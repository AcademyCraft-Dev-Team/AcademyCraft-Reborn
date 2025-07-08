package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.client.renderer.Shaders;

import java.util.function.BiFunction;

public final class RenderTypes {
    /**
     * Ability Developer
     */
    public static final RenderType ABILITY_DEVELOPER = RenderType.entityTranslucent(TextureResources.ABILITY_DEVELOPER);
    public static final BiFunction<String, ResourceLocation, RenderType> SKILL_ICON =
            (string, resourceLocation) ->
                    RenderUtil.getPositionColorTexRenderTypeFull(string, resourceLocation, false);
    public static final RenderType GLOW_CIRCLE = new RenderType.CompositeRenderType(
            "glow_circle",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            64,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateUtil.GLOW_CIRCLE)
                    .setCullState(RenderStateUtil.NO_CULL)
                    .setWriteMaskState(RenderStateUtil.COLOR_WRITE)
                    .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );
    public static final RenderType PANEL_LEFT_BACK_TOP = RenderUtil.getPositionColorTexRenderTypeFull(
            "panel_left_back_top", TextureResources.PANEL_LEFT_BACK_TOP, false);
    public static final RenderType PANEL_LEFT_BACK_MIDDLE = RenderUtil.getPositionColorTexRenderTypeFull(
            "panel_left_back_middle", TextureResources.PANEL_LEFT_BACK_MIDDLE, false);
    public static final RenderType SKILL_PANEL_BACK = RenderUtil.getPositionColorTexRenderType(
            "skill_panel_back", TextureResources.PANEL_RIGHT_SKILL_BACK, true);
    public static final RenderType SKILL_PANEL_INFO = RenderUtil.getPositionColorTexRenderType(
            "skill_panel_info", TextureResources.PANEL_RIGHT_INFO, false);
    public static final RenderType PANEL_RIGHT_BACK = RenderUtil.getPositionColorTexRenderTypeFull(
            "panel_right_back", TextureResources.PANEL_RIGHT_BACK, false);
    public static final RenderType PANEL_RIGHT_SKILL_ICON_BACK = RenderUtil.getPositionColorTexRenderTypeFull(
            "panel_right_skill_icon_back", TextureResources.PANEL_RIGHT_SKILL_ICON_BACK, true);
    /**
     * WindGen
     */
    public static final RenderType WIND_GEN_UI = RenderUtil.getPositionColorTexRenderTypeFull(
            "windgen_ui", TextureResources.WIND_GEN_UI, false
    );
    public static final RenderType ICON_WIND_GEN_BASE = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wind_gen_base", TextureResources.ICON_WIND_GEN_BASE, false
    );
    public static final RenderType ICON_WIND_GEN_PILLAR = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wind_gen_pillar", TextureResources.ICON_WIND_GEN_PILLAR, false
    );
    public static final RenderType ICON_WIND_GEN_TOP = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wind_gen_top", TextureResources.ICON_WIND_GEN_TOP, false
    );
    /**
     * Wireless Node
     */
    public static final RenderType WIRELESS_NODE_UI = RenderUtil.getPositionColorTexRenderTypeFull(
            "wireless_node_ui", TextureResources.WIRELESS_NODE_UI, false
    );
    public static final RenderType WIRELESS_NODE_STATE = RenderUtil.getPositionColorTexRenderTypeFull(
            "wireless_node_state", TextureResources.WIRELESS_NODE_STATE, false
    );
    /**
     * Omni Crafting Table
     */
    public static final RenderType OMNI_CRAFTING_UI = RenderUtil.getPositionColorTexRenderTypeFull(
            "omni_crafting_ui", TextureResources.OMNI_CRAFTING_UI, false
    );
    /**
     * Common
     */
    public static final RenderType INVENTORY = RenderUtil.getPositionColorTexRenderTypeFull(
            "inventory", TextureResources.INVENTORY, false
    );
    public static final RenderType ELEMENT_LINE = RenderUtil.getPositionColorTexRenderTypeFull(
            "element_line", TextureResources.ELEMENT_LINE, true);
    public static final RenderType ELEMENT_BACK_LIGHT = RenderUtil.getPositionColorTexRenderTypeFull(
            "element_back_light", TextureResources.ELEMENT_BACK_LIGHT, false);
    public static final RenderType WIRELESS_PANEL_VIEW_ICON = RenderUtil.getPositionColorTexRenderTypeFull(
            "wireless_panel_view_icon", TextureResources.WIRELESS_PANEL_VIEW_ICON, false);
    public static final RenderType ICON_NODE = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_node", TextureResources.ICON_NODE, false);
    public static final RenderType ICON_CONNECTED = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_connected", TextureResources.ICON_CONNECTED, false);
    public static final RenderType ICON_UNCONNECTED = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_unconnected", TextureResources.ICON_UNCONNECTED, false);
    public static final RenderType ICON_INV = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_inv", TextureResources.ICON_INV, false);
    public static final RenderType ICON_WIRELESS = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wireless", TextureResources.ICON_WIRELESS, false);
    public static final RenderType HISTOGRAM = RenderUtil.getPositionColorTexRenderTypeFull(
            "histogram", TextureResources.HISTOGRAM, false
    );
    public static final RenderType SDF_SHARP_QUAD = new RenderType.CompositeRenderType(
            "sdf_sharp_quad", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(() -> Shaders.sdfSharpQuadWithMarginShader))
                    .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );
    public static final RenderType BUTTON = RenderUtil.getPositionColorTexRenderTypeFull(
            "button", TextureResources.BUTTON, false
    );
    public static final RenderType ICON_BOX = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_box", TextureResources.ICON_BOX, false
    );
    public static final RenderType CURSOR = RenderUtil.getPositionColorTexRenderTypeFull(
            "cursor", TextureResources.CURSOR, true
    );
    /**
     * Data Terminal
     */
    public static final RenderType ICON_DATA_TERMINAL = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_data_terminal", TextureResources.ICON_DATA_TERMINAL, false
    );
    public static final RenderType APP_BACK = RenderUtil.getPositionColorTexRenderTypeFull(
            "app_back", TextureResources.APP_BACK, false
    );
    public static final RenderType APP_MEDIA_PLAYER = RenderUtil.getPositionColorTexRenderTypeFull(
            "app_media_player", TextureResources.APP_MEDIA_PLAYER, false
    );
    public static final RenderType APP_MISAKA_CLOUD = RenderUtil.getPositionColorTexRenderTypeFull(
            "app_misaka_cloud", TextureResources.APP_MISAKA_CLOUD, false
    );
    public static final RenderType APP_SETTINGS = RenderUtil.getPositionColorTexRenderTypeFull(
            "app_settings", TextureResources.APP_SETTINGS, false
    );
    public static final RenderType CIRCLE_GLOW = new RenderType.CompositeRenderType(
            "sdf_circle_glow",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 16,
            false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(() -> Shaders.sdfCircleGlowShader))
                    .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateUtil.COLOR_WRITE)
                    .createCompositeState(false)
    );
    /**
     * Cat Engine
     */
    public static final RenderType CAT_ENGINE = RenderType.entityTranslucent(TextureResources.CAT_ENGINE);
    /**
     * Cleaning Robot
     */
    public static final RenderType CLEANING_ROBOT = RenderType.entityTranslucent(TextureResources.CLEANING_ROBOT);
}