package org.academy.api.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.Resource;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.render.UIContext;
import org.academy.api.client.gui.screen.IAnimationScreen;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.input.InputSystem;

import java.util.*;

public final class HUDManager implements IAnimationScreen {
    public static final HUDManager INSTANCE = new HUDManager();

    public static final String KEY_SKILL_UP = "hud_skill_up";
    public static final String KEY_SKILL_DOWN = "hud_skill_down";

    public static final UIContext UI_RENDER_CONTEXT = new UIContext();
    public static final List<HUDRenderer> HUD_RENDERERS = new ArrayList<>();
    public static final RenderTarget RENDER_TARGET;
    public static final FrameLayoutWidget ROOT;
    public static final ImageWidget CP_BAR_BACKGROUND_WIDGET;
    public static final ImageWidget CP_BAR_WIDGET;
    private static final CPBarValueWidget CP_BAR_VALUE_WIDGET;
    public static final ImageWidget ABILITY_ICON_WIDGET;
  //  public static final LinearLayoutContainer SKILL_LIST_CONTAINER;

    private static final float CP_BAR_WIDTH = 192.8F;
    private static final float CP_BAR_HEIGHT = 29.400002F;
    private static final float ABILITY_ICON_SIZE = 8;
    private static final float ABILITY_ICON_RIGHT_MARGIN = 10;
    private static final float ABILITY_ICON_TOP_MARGIN = 12;

    private static final float SKILL_LIST_RIGHT_MARGIN = 5.0F;
    private static final float DEFAULT_SKILL_WIDGET_WIDTH = 80.0F;
    private static final float DEFAULT_SKILL_WIDGET_HEIGHT = 20.0F;
    private static final float SELECTED_SKILL_WIDTH = 100.0F;
    private static final float SELECTED_SKILL_HEIGHT = 25.0F;
    private static final float SELECTED_SKILL_X_OFFSET = 20.0F;

    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    private static boolean initialized = false;
    private static int selectedSkillIndex = 0;
    private static float currentAlpha = 0.0F;
    private static float smoothProgress = 0.0F;

    static {
        var window = Minecraft.getInstance().getWindow();
        RENDER_TARGET = new TextureTarget(null, window.getWidth(), window.getHeight(), true);

        ROOT = new FrameLayoutWidget();
        ROOT.setAlpha(0.0F);

        CP_BAR_BACKGROUND_WIDGET = new ImageWidget(Resource.Textures.CP_BAR_BACKGROUND);
        CP_BAR_BACKGROUND_WIDGET.setSampler(FilterMode.NEAREST, false);
        ROOT.addChild("cp_bar_bg", CP_BAR_BACKGROUND_WIDGET);

        CP_BAR_WIDGET = new ImageWidget(Resource.Textures.CP_BAR);
        CP_BAR_WIDGET.setSampler(FilterMode.LINEAR, true);
        ROOT.addChild("cp_bar", CP_BAR_WIDGET);

        CP_BAR_VALUE_WIDGET = new CPBarValueWidget(0.0F, 0.0F, 0.0F, 0.0F);
        ROOT.addChild("cp_bar_value", CP_BAR_VALUE_WIDGET);

        ABILITY_ICON_WIDGET = new ImageWidget((Identifier) null);
        ROOT.addChild("ability_icon", ABILITY_ICON_WIDGET);
/*
        SKILL_LIST_CONTAINER = new LinearLayoutContainer(0.0F, 0.0F, 0.0F, 0.0F, Orientation.VERTICAL);
        SKILL_LIST_CONTAINER.setSpacing(2.0F);
        ROOT.addChild("skill_list", SKILL_LIST_CONTAINER);*/
    }

    private HUDManager() {
    }

    public static void init() {
        if (initialized)
            return;

        initialized = true;

        var upKeys = new LinkedHashSet<Integer>();
        upKeys.add(265);
        InputSystem.addKeyBinding(KEY_SKILL_UP,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(upKeys, 0, new LinkedHashSet<>())),
                HUDManager::selectPreviousSkill);

        var downKeys = new LinkedHashSet<Integer>();
        downKeys.add(264);
        InputSystem.addKeyBinding(KEY_SKILL_DOWN,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(downKeys, 0, new LinkedHashSet<>())),
                HUDManager::selectNextSkill);

        NeoForge.EVENT_BUS.register(HUDManager.class);
    }

    public static void registerHUDRenderer(HUDRenderer renderer) {
        if (!HUD_RENDERERS.contains(renderer))
            HUD_RENDERERS.add(renderer);
    }

    private static float getAnimationFactor(float partialTick) {
        return partialTick * 0.5f;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!initialized)
            return;

        var colorTexture = RENDER_TARGET.getColorTexture();
        var depthTexture = RENDER_TARGET.getDepthTexture();
        var colorTextureView = RENDER_TARGET.getColorTextureView();
        if (colorTexture == null || depthTexture == null || colorTextureView == null)
            return;

        var partialTick = event.getPartialTick().getGameTimeDeltaTicks();

        {
            var targetAlpha = AbilitySystemClient.isActiveHUD() ? 1.0f : 0.0f;
            currentAlpha = Mth.lerp(partialTick, currentAlpha, targetAlpha);
            ROOT.setAlpha(currentAlpha);

            var r = 1.0f;
            var g = 0.68235296F;
            var b = 0.26666668F;
            var finalR = Mth.lerp(currentAlpha, r, 1.0f);
            var finalG = Mth.lerp(currentAlpha, g, 1.0f);
            var finalB = Mth.lerp(currentAlpha, b, 1.0f);
            CP_BAR_WIDGET.setColor(finalR, finalG, finalB);
        }

        {
            var computingPower = AbilitySystemClient.getComputingPower();
            var maximumComputingPower = AbilitySystemClient.getMaxComputingPower();
            var progress =computingPower / maximumComputingPower;
            if (Float.isNaN(progress) || Float.isInfinite(progress)) progress = 0;
            smoothProgress = Mth.lerp(partialTick, smoothProgress, progress);
            if (Float.isNaN(smoothProgress)) {
                smoothProgress = 0.0f;
            }
        }

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0);

        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var mouse = mc.mouseHandler;
        var mouseX = mouse.getScaledXPos(window);
        var mouseY = mouse.getScaledYPos(window);

        layoutWidgets((float) window.getGuiScaledWidth(), (float) window.getGuiScaledHeight());
      //  UI_RENDER_CONTEXT.renderFrame(ROOT, RENDER_TARGET, mouseX, mouseY, partialTick);

        for (var renderer : HUD_RENDERERS)
            renderer.render(mouseX, mouseY, partialTick);

        var guiGraphics = event.getGuiGraphics();
        guiGraphics.submitBlit(RenderPipelines.GUI_TEXTURED, colorTextureView,
                RenderSystem.getSamplerCache().getSampler(
                        AddressMode.REPEAT, AddressMode.REPEAT,
                        FilterMode.NEAREST, FilterMode.LINEAR,
                        false
                ), 0, 0,
                guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0, 1, 1, 0, -1);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!initialized || Minecraft.getInstance().player == null)
            return;

        updateSkillWidgetsList();
        ROOT.tick();
    }

    public static void resize(int width, int height) {
        RENDER_TARGET.resize(width, height);
    }

    private static void layoutWidgets(float screenWidth, float screenHeight) {
        /*ROOT.setWidth(screenWidth);
        ROOT.setHeight(screenHeight);

        {
            CP_BAR_BACKGROUND_WIDGET.setWidth(CP_BAR_WIDTH).setHeight(CP_BAR_HEIGHT).setX(screenWidth - CP_BAR_WIDTH);
            CP_BAR_WIDGET.setWidth(CP_BAR_WIDTH).setHeight(CP_BAR_HEIGHT).setX(screenWidth - CP_BAR_WIDTH);
            CP_BAR_WIDGET.setZ(1);
            CP_BAR_VALUE_WIDGET.setWidth(screenWidth).setHeight(CP_BAR_HEIGHT);
            CP_BAR_VALUE_WIDGET.setZ(1);
        }

        {
            ABILITY_ICON_WIDGET.setWidth(ABILITY_ICON_SIZE).setHeight(ABILITY_ICON_SIZE);
            ABILITY_ICON_WIDGET.setX(screenWidth - ABILITY_ICON_RIGHT_MARGIN - ABILITY_ICON_SIZE);
            ABILITY_ICON_WIDGET.setY(ABILITY_ICON_TOP_MARGIN);
            ABILITY_ICON_WIDGET.setZ(1);

            var category = AbilitySystemClient.getCategory();
            var key = category.getKey();
            var iconPath = AcademyCraft.custom(key.getNamespace(), "textures/ability/" + key.getPath() + "/icon_overlay.png");
            ABILITY_ICON_WIDGET.setTexture(iconPath);
        }

        {
            var children = SKILL_LIST_CONTAINER.getChildren().values();
            var totalListHeight = children.stream().mapToDouble(Widget::getHeight).sum();
            totalListHeight += Math.max(0, children.size() - 1) * SKILL_LIST_CONTAINER.getSpacing();
            SKILL_LIST_CONTAINER.setY((float) ((screenHeight - totalListHeight) / 2.0F));
        }*/
    }

    private static void updateSkillWidgetsList() {
/*        var learnedSkills = AbilitySystemClient.LEARNED_SKILLS;
        var currentWidgets = new HashMap<Skill, SkillWidget>();

        SKILL_LIST_CONTAINER.getChildren().values().stream()
                .filter(SkillWidget.class::isInstance)
                .map(SkillWidget.class::cast)
                .forEach(skillWidget -> currentWidgets.put(skillWidget.skill, skillWidget));

        var skillsToRemove = new ArrayList<Skill>();
        for (var skill : currentWidgets.keySet())
            if (!learnedSkills.contains(skill))
                skillsToRemove.add(skill);

        skillsToRemove.forEach(skill -> {
            var widget = currentWidgets.get(skill);
            SKILL_LIST_CONTAINER.removeChild(widget.getName());
        });

        var skillsToAdd = new ArrayList<>(learnedSkills);
        skillsToAdd.removeAll(currentWidgets.keySet());
        skillsToAdd.forEach(skill -> {
            var widget = new SkillWidget(skill);
            SKILL_LIST_CONTAINER.addChild("skill_" + skill.getKey().toString().replace(":", "_"), widget);
        });

        if (selectedSkillIndex >= SKILL_LIST_CONTAINER.getChildren().size())
            selectedSkillIndex = 0;

        updateSelectionState(false);*/
    }

    private static void selectNextSkill() {
/*        if (!SKILL_LIST_CONTAINER.getChildren().isEmpty() && AbilitySystemClient.isActiveHUD()) {
            updateSelectionState(false);
            selectedSkillIndex = (selectedSkillIndex + 1) % SKILL_LIST_CONTAINER.getChildren().size();
            updateSelectionState(true);
        }*/
    }

    private static void selectPreviousSkill() {
/*        if (!SKILL_LIST_CONTAINER.getChildren().isEmpty() && AbilitySystemClient.isActiveHUD()) {
            updateSelectionState(false);
            selectedSkillIndex = (selectedSkillIndex - 1 + SKILL_LIST_CONTAINER.getChildren().size()) % SKILL_LIST_CONTAINER.getChildren().size();
            updateSelectionState(true);
        }*/
    }
/*
    private static void updateSelectionState(boolean selected) {
        var children = new ArrayList<>(SKILL_LIST_CONTAINER.getChildren().values());
        if (selectedSkillIndex < children.size()) {
            var widget = (SkillWidget) children.get(selectedSkillIndex);
            widget.animateSelection(selected);
        }
    }*/

    @Override
    public List<Animator> getScreenAnimations() {
        return screenAnimations;
    }

    @Override
    public Map<Widget, List<Animator>> getTrackedAnimations() {
        return trackedAnimations;
    }

    private static class CPBarValueWidget extends ImageWidget {
        public CPBarValueWidget(float x, float y, float width, float height) {
            super(Resource.Textures.CP_BAR_VALUE);
            setSampler(FilterMode.LINEAR, true);
        }

       /* public void render(RenderContext context) {
            var finalAlpha = getAlpha() * context.getAccumulatedAlpha();
            context.pose().translate(0, 0, 1);

            var r = CP_BAR_WIDGET.getRed();
            var g = CP_BAR_WIDGET.getGreen();
            var b = CP_BAR_WIDGET.getBlue();
            var rootAlpha = ROOT.getAlpha();

            resolveAndPrepareTexture();

            if (textureView == null) return;

            var command = new ImageDrawCommand(textureView, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) {
                public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
                    var barWidthOffset = 148.54579F * (1.0F - smoothProgress);
                    var leftSafeZoneWidth = 3.2000003F;

                    var leftTopOffset = barWidthOffset + leftSafeZoneWidth;
                    var leftBottomOffset = leftTopOffset + CP_BAR_HEIGHT;

                    var rightTopX = getWidth();
                    var leftTopX = rightTopX - CP_BAR_WIDTH + leftTopOffset;
                    var leftBottomX = rightTopX - CP_BAR_WIDTH + leftBottomOffset;

                    var leftTopUv = 1.0F - (CP_BAR_WIDTH - leftTopOffset) / CP_BAR_WIDTH;
                    var leftBottomUv = 1.0F - (CP_BAR_WIDTH - leftBottomOffset) / CP_BAR_WIDTH;

                    var leftAlpha = finalAlpha * rootAlpha;
                    leftAlpha *= leftAlpha;
                    leftAlpha *= leftAlpha;

                    consumer.addVertex(pose, leftTopX, 0.0F, 0.0F).setUv(leftTopUv, 0.0F).setColor(r, g, b, leftAlpha);
                    consumer.addVertex(pose, leftBottomX, CP_BAR_HEIGHT, 0.0F).setUv(leftBottomUv, 1.0F).setColor(r, g, b, leftAlpha);
                    consumer.addVertex(pose, rightTopX, CP_BAR_HEIGHT, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, finalAlpha);
                    consumer.addVertex(pose, rightTopX, 0.0F, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, finalAlpha);
                }
            };
            context.submit(command);
        }*/
    }
/*
    private static class SkillWidget extends PanelWidget {
        private final Skill skill;
        private final FillWidget back;
        private final AutoScaleLabelWidget label;
        private final ImageWidget icon;

        public SkillWidget(Skill newSkill) {
            super(0.0F, 0.0F, DEFAULT_SKILL_WIDGET_WIDTH, DEFAULT_SKILL_WIDGET_HEIGHT);
            skill = newSkill;
            setName("skill_" + newSkill.getKey().toString().replace(":", "_"));

            var padding = 2.0F;
            var iconSize = 16.0F;

            back = new FillWidget(0.0F, 0.0F, getWidth(), getHeight(), 1610612736);
            label = new AutoScaleLabelWidget(skill.getTranslatedName(), padding, 0.0F, getWidth() - iconSize - padding * 3.0F);
            label.setScale(0.7F);
            icon = new ImageWidget(getWidth() - padding - iconSize, padding, iconSize, iconSize, skill.getKey());
            icon.setWidthScale(0.8F);
            icon.setHeightScale(0.8F);

            addChild("back", back);
            addChild("label", label);
            addChild("icon", icon);
        }

        public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
          *//*  back.setWidth(getWidth());
            back.setHeight(getHeight());
            back.setColor(back.getColor() & 16777215 | (int) (112.0F * getAbsoluteAlpha() * context.getAccumulatedAlpha()) << 24);
            label.setColor(label.getColor() & 16777215 | (int) (255.0F * getAbsoluteAlpha() * context.getAccumulatedAlpha()) << 24);

            var currentHeightRatio = getHeight() / DEFAULT_SKILL_WIDGET_HEIGHT;
            var padding = 2.0F * currentHeightRatio;
            var iconSize = getHeight() - padding * 2.0F;

            label.setX(padding);
            label.setWidth(getWidth() - iconSize - padding * 3.0F);
            label.setY((getHeight() - label.getHeight()) / 2.0F);

            icon.setX(getWidth() - padding - iconSize);
            icon.setY(padding);
            icon.setWidth(iconSize);
            icon.setHeight(iconSize);

            super.render(context, mouseX, mouseY, partialTick);*//*
        }

        public void animateSelection(boolean selected) {
*//*            INSTANCE.playTrackedAnimation(this, ObjectAnimator.ofFloat(alpha ->
                    setAlpha(alpha * (selected ? 1.0F : 0.65F) + (1.0F - alpha) * getAlpha()), 0.0F, 1.0F).setDuration(450L));

            var screenWidth = (float) Minecraft.getInstance().getWindow().getGuiScaledWidth();
            var baseX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;

            float targetWidth;
            float targetHeight;
            float targetX;
            if (selected) {
                targetWidth = SELECTED_SKILL_WIDTH;
                targetHeight = SELECTED_SKILL_HEIGHT;
                targetX = baseX - SELECTED_SKILL_X_OFFSET;
            } else {
                targetWidth = DEFAULT_SKILL_WIDGET_WIDTH;
                targetHeight = DEFAULT_SKILL_WIDGET_HEIGHT;
                targetX = baseX;
            }

            INSTANCE.playTrackedAnimation(this, ObjectAnimator.ofFloat(this::setWidth, getWidth(), targetWidth)
                    .setDuration(450L).setInterpolator(EasingFunctions.EASE_OUT_BACK));
            INSTANCE.playTrackedAnimation(this, ObjectAnimator.ofFloat(this::setHeight, getHeight(), targetHeight)
                    .setDuration(450L).setInterpolator(EasingFunctions.EASE_OUT_BACK));
            INSTANCE.playTrackedAnimation(this, ObjectAnimator.ofFloat(this::setX, getX(), targetX)
                    .setDuration(450L).setInterpolator(EasingFunctions.EASE_OUT_BACK));*//*
        }
    }*/
}