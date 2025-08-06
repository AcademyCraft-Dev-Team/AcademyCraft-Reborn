package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.client.renderer.Shaders;

import java.util.OptionalDouble;
import java.util.function.Function;

import static net.minecraft.client.renderer.RenderStateShard.*;

public final class RenderTypes {
    private RenderTypes() {}

    public static final RenderType ABILITY_DEVELOPER = RenderType.entityTranslucent(TextureResources.MODEL_ABILITY_DEVELOPER);
    public static final RenderType CAT_ENGINE = RenderType.entityTranslucent(TextureResources.CAT_ENGINE);
    public static final RenderType CLEANING_ROBOT = RenderType.entitySolid(TextureResources.CLEANING_ROBOT);

    public static final RenderType INVENTORY = RenderUtil.getPositionColorTexRenderType(
            "inventory", TextureResources.INVENTORY, false
    );
    public static final RenderType ELEMENT_LINE = RenderUtil.getPositionColorTexRenderType(
            "element_line", TextureResources.UI_LINE, true);
    public static final RenderType ELEMENT_BACK_LIGHT = RenderUtil.getPositionColorTexRenderType(
            "element_back_light", TextureResources.UI_BACKGROUND_LIGHT, false);
    public static final RenderType BUTTON = RenderUtil.getPositionColorTexRenderType(
            "button", TextureResources.UI_BUTTON_LEARN, false
    );
    public static final RenderType ICON_BOX = RenderUtil.getPositionColorTexRenderType(
            "icon_box", TextureResources.HUD_SKILL_FRAME, false
    );
    public static final RenderType HISTOGRAM = RenderUtil.getPositionColorTexRenderType(
            "histogram", TextureResources.HISTOGRAM, false
    );
    public static final RenderType APP_BACK = RenderUtil.getPositionColorTexRenderType(
            "app_back", TextureResources.APP_BACK, false
    );

    public static final RenderType CP_BAR_VALUE = RenderUtil.getPositionColorTexRenderType(
            "cp_bar_value", TextureResources.CP_BAR_VALUE, false
    );
    public static final RenderType CP_BAR = RenderUtil.getPositionColorTexRenderType(
            "cp_bar", TextureResources.CP_BAR, false
    );
    public static final RenderType CP_BAR_BACKGROUND = RenderUtil.getPositionColorTexRenderType(
            "cp_bar_background", TextureResources.CP_BAR_BACKGROUND, false
    );

    public static RenderType getSkillIcon(String skillName, ResourceLocation resourceLocation) {
        return RenderUtil.getPositionColorTexRenderType("skill_icon_" + skillName.toLowerCase().replace(":", "_"), resourceLocation, false);
    }
    public static final RenderType ICON_NODE = RenderUtil.getPositionColorTexRenderType(
            "icon_node", TextureResources.ICON_NODE, false);
    public static final RenderType ICON_CONNECTED = RenderUtil.getPositionColorTexRenderType(
            "icon_connected", TextureResources.ICON_CONNECTED, false);
    public static final RenderType ICON_UNCONNECTED = RenderUtil.getPositionColorTexRenderType(
            "icon_unconnected", TextureResources.ICON_UNCONNECTED, false);
    public static final RenderType ICON_INV = RenderUtil.getPositionColorTexRenderType(
            "icon_inv", TextureResources.ICON_INV, false);
    public static final RenderType ICON_WIRELESS = RenderUtil.getPositionColorTexRenderType(
            "icon_wireless", TextureResources.ICON_WIRELESS, false);
    public static final RenderType ICON_DATA_TERMINAL = RenderUtil.getPositionColorTexRenderType(
            "icon_data_terminal", TextureResources.ICON_DATA_TERMINAL, false
    );
    public static final RenderType ICON_MUSIC_PLAYER = RenderUtil.getPositionColorTexRenderType(
            "icon_music_player", TextureResources.ICON_MUSIC_PLAYER, false
    );
    public static final RenderType ICON_CYCLE = RenderUtil.getPositionColorTexRenderType(
            "icon_cycle", TextureResources.ICON_CYCLE, false
    );
    public static final RenderType ICON_RANDOM = RenderUtil.getPositionColorTexRenderType(
            "icon_random", TextureResources.ICON_RANDOM, false
    );
    public static final RenderType ICON_SINGLE_CYCLE = RenderUtil.getPositionColorTexRenderType(
            "icon_single_cycle", TextureResources.ICON_SINGLE_CYCLE, false
    );
    public static final RenderType ICON_SETTINGS = RenderUtil.getPositionColorTexRenderType(
            "icon_settings", TextureResources.ICON_SETTINGS, false
    );
    public static final RenderType WIRELESS_PANEL_VIEW_ICON = RenderUtil.getPositionColorTexRenderType(
            "wireless_panel_view_icon", TextureResources.ICON_OPEN_WIRELESS_PANEL, false);

    public static final RenderType PANEL_LEFT_BACK_TOP = RenderUtil.getPositionColorTexRenderType(
            "panel_left_back_top", TextureResources.UI_DEVELOPER_PANEL_LEFT, false);
    public static final RenderType PANEL_LEFT_BACK_MIDDLE = RenderUtil.getPositionColorTexRenderType(
            "panel_left_back_middle", TextureResources.PANEL_LEFT_BACK_MIDDLE, false);
    public static final RenderType SKILL_PANEL_BACK = RenderUtil.getPositionColorTexRenderType(
            "skill_panel_back", TextureResources.UI_DEVELOPER_SKILL_AREA_BG, true);
    public static final RenderType SKILL_PANEL_INFO = RenderUtil.getPositionColorTexRenderType(
            "skill_panel_info", TextureResources.UI_DEVELOPER_PANEL_RIGHT, false);
    public static final RenderType PANEL_RIGHT_BACK = RenderUtil.getPositionColorTexRenderType(
            "panel_right_back", TextureResources.PANEL_RIGHT_BACK, false);
    public static final RenderType PANEL_RIGHT_SKILL_ICON_BACK = RenderUtil.getPositionColorTexRenderType(
            "panel_right_skill_icon_back", TextureResources.UI_DEVELOPER_SKILL_ICON_BG, true);

    public static final RenderType WIND_GEN_UI = RenderUtil.getPositionColorTexRenderType(
            "windgen_ui", TextureResources.WIND_GEN_UI, false);
    public static final RenderType ICON_WIND_GEN_BASE = RenderUtil.getPositionColorTexRenderType(
            "icon_wind_gen_base", TextureResources.ICON_WIND_GEN_BASE, false);
    public static final RenderType ICON_WIND_GEN_PILLAR = RenderUtil.getPositionColorTexRenderType(
            "icon_wind_gen_pillar", TextureResources.ICON_WIND_GEN_PILLAR, false);
    public static final RenderType ICON_WIND_GEN_TOP = RenderUtil.getPositionColorTexRenderType(
            "icon_wind_gen_top", TextureResources.ICON_WIND_GEN_TOP, false);

    public static final RenderType WIRELESS_NODE_UI = RenderUtil.getPositionColorTexRenderType(
            "wireless_node_ui", TextureResources.WIRELESS_NODE_UI, false);
    public static final RenderType WIRELESS_NODE_STATE = RenderUtil.getPositionColorTexRenderType(
            "wireless_node_state", TextureResources.WIRELESS_NODE_STATE, false);

    public static final RenderType OMNI_CRAFTING_UI = RenderUtil.getPositionColorTexRenderType(
            "omni_crafting_ui", TextureResources.OMNI_CRAFTING_UI, false);

    public static final RenderType GLOW_CIRCLE = RenderType.create(
            "glow_circle", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 64, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateUtil.GLOW_CIRCLE)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false));

    public static final RenderType SDF_SHARP_QUAD = RenderType.create(
            "sdf_sharp_quad", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> Shaders.SDF_SHARP_QUAD_WITH_MARGIN))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false));

    public static final Function<OptionalDouble, RenderType> LINES = Util.memoize(optionalDouble -> RenderType.create(
            "academy:lines", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES, 256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(optionalDouble))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)));

    public static final RenderType CIRCLE_GLOW = RenderType.create(
            "sdf_circle_glow", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 16, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> Shaders.SDF_CIRCLE_GLOW))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));
}