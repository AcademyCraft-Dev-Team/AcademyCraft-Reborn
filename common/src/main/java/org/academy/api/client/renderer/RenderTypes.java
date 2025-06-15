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
    public static final BiFunction<String, ResourceLocation, RenderType> RENDER_TYPE_SKILL_ICON =
            (string, resourceLocation) ->
                    RenderUtil.getPositionColorTexRenderType(string, resourceLocation, false);
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
    public static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_TOP = RenderUtil.getPositionColorTexRenderType(
            "panel_left_back_top", TextureResources.TEXTURE_PANEL_LEFT_BACK_TOP, false);
    public static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_MIDDLE = RenderUtil.getPositionColorTexRenderType(
            "panel_left_back_middle", TextureResources.TEXTURE_PANEL_LEFT_BACK_MIDDLE, false);
    public static final RenderType RENDER_TYPE_SKILL_PANEL_BACK = RenderUtil.getPositionTexRenderType(
            "skill_panel_back", TextureResources.TEXTURE_PANEL_RIGHT_SKILL_BACK, true);
    public static final RenderType RENDER_TYPE_SKILL_PANEL_INFO = RenderUtil.getPositionTexRenderType(
            "skill_panel_info", TextureResources.TEXTURE_PANEL_RIGHT_INFO, false);
    public static final RenderType RENDER_TYPE_PANEL_RIGHT_BACK = RenderUtil.getPositionColorTexRenderType(
            "panel_right_back", TextureResources.TEXTURE_PANEL_RIGHT_BACK, false);
    public static final RenderType RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK = RenderUtil.getPositionColorTexRenderType(
            "panel_right_skill_icon_back", TextureResources.TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK, true);
    /**
     * WindGen
     */
    public static final RenderType RENDER_TYPE_WIND_GEN_UI = RenderUtil.getPositionColorTexRenderTypeFull(
            "windgen_ui", TextureResources.TEXTURE_WIND_GEN_UI, false
    );
    public static final RenderType RENDER_TYPE_ICON_WIND_GEN_BASE = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wind_gen_base", TextureResources.TEXTURE_ICON_WIND_GEN_BASE, false
    );
    public static final RenderType RENDER_TYPE_ICON_WIND_GEN_PILLAR = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wind_gen_pillar", TextureResources.TEXTURE_ICON_WIND_GEN_PILLAR, false
    );
    public static final RenderType RENDER_TYPE_ICON_WIND_GEN_TOP = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_wind_gen_top", TextureResources.TEXTURE_ICON_WIND_GEN_TOP, false
    );
    /**
     * Wireless Node
     */
    public static final RenderType RENDER_TYPE_WIRELESS_NODE_UI = RenderUtil.getPositionColorTexRenderType(
            "wireless_node_ui", TextureResources.TEXTURE_WIRELESS_NODE_UI, false
    );
    public static final RenderType RENDER_TYPE_WIRELESS_NODE_STATE = RenderUtil.getPositionTexRenderType(
            "wireless_node_state", TextureResources.TEXTURE_WIRELESS_NODE_STATE, false
    );
    /**
     * Common
     */
    public static final RenderType RENDER_TYPE_INVENTORY = RenderUtil.getPositionColorTexRenderType(
            "inventory", TextureResources.TEXTURE_INVENTORY, false
    );
    public static final RenderType RENDER_TYPE_ELEMENT_LINE = RenderUtil.getPositionColorTexRenderType(
            "element_line", TextureResources.TEXTURE_ELEMENT_LINE, true);
    public static final RenderType RENDER_TYPE_ELEMENT_BACK_LIGHT = RenderUtil.getPositionColorTexRenderType(
            "element_back_light", TextureResources.TEXTURE_ELEMENT_BACK_LIGHT, false);
    public static final RenderType RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON = RenderUtil.getPositionColorTexRenderType(
            "wireless_panel_view_icon", TextureResources.TEXTURE_WIRELESS_PANEL_VIEW_ICON, false);
    public static final RenderType RENDER_TYPE_ICON_NODE = RenderUtil.getPositionColorTexRenderType(
            "icon_node", TextureResources.TEXTURE_ICON_NODE, false);
    public static final RenderType RENDER_TYPE_ICON_CONNECTED = RenderUtil.getPositionColorTexRenderType(
            "icon_connected", TextureResources.TEXTURE_ICON_CONNECTED, false);
    public static final RenderType RENDER_TYPE_ICON_UNCONNECTED = RenderUtil.getPositionColorTexRenderType(
            "icon_unconnected", TextureResources.TEXTURE_ICON_UNCONNECTED, false);
    public static final RenderType RENDER_TYPE_ICON_INV = RenderUtil.getPositionColorTexRenderType(
            "icon_inv", TextureResources.TEXTURE_ICON_INV, false);
    public static final RenderType RENDER_TYPE_ICON_WIRELESS = RenderUtil.getPositionColorTexRenderType(
            "icon_wireless", TextureResources.TEXTURE_ICON_WIRELESS, false);
    public static final RenderType RENDER_TYPE_HISTOGRAM = RenderUtil.getPositionColorTexRenderType(
            "histogram", TextureResources.TEXTURE_HISTOGRAM, false
    );
    public static final RenderType RENDER_TYPE_SDF_SHARP_QUAD = new RenderType.CompositeRenderType(
            "sdf_sharp_quad", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(() -> Shaders.sdfSharpQuadWithMarginShader))
                    .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );
    public static final RenderType RENDER_TYPE_BUTTON = RenderUtil.getPositionColorTexRenderType(
            "button", TextureResources.TEXTURE_BUTTON, false
    );
    public static final RenderType RENDER_TYPE_ICON_BOX = RenderUtil.getPositionColorTexRenderTypeFull(
            "icon_box", TextureResources.TEXTURE_ICON_BOX, false
    );
    public static final RenderType RENDER_TYPE_CURSOR = RenderUtil.getPositionColorTexRenderType(
            "cursor", TextureResources.TEXTURE_CURSOR, true
    );
    /**
     * Data Terminal
     */
    public static final RenderType RENDER_TYPE_ICON_DATA_TERMINAL = RenderUtil.getPositionColorTexRenderType(
            "icon_data_terminal", TextureResources.TEXTURE_ICON_DATA_TERMINAL, false
    );
    public static final RenderType RENDER_TYPE_APP_BACK = RenderUtil.getPositionColorTexRenderType(
            "app_back", TextureResources.TEXTURE_APP_BACK, false
    );
    public static final RenderType RENDER_TYPE_APP_MEDIA_PLAYER = RenderUtil.getPositionColorTexRenderType(
            "app_media_player", TextureResources.TEXTURE_APP_MEDIA_PLAYER, false
    );
    public static final RenderType RENDER_TYPE_APP_MISAKA_CLOUD = RenderUtil.getPositionColorTexRenderType(
            "app_misaka_cloud", TextureResources.TEXTURE_APP_MISAKA_CLOUD, false
    );
    public static final RenderType RENDER_TYPE_APP_SETTINGS = RenderUtil.getPositionColorTexRenderType(
            "app_settings", TextureResources.TEXTURE_APP_SETTINGS, false
    );
    public static final RenderType RENDER_TYPE_CIRCLE_GLOW = new RenderType.CompositeRenderType(
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
}
