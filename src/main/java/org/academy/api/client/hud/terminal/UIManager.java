package org.academy.api.client.hud.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.*;
import org.academy.api.client.gui.util.GlyphCommandGenerator;
import org.academy.api.client.gui.widget.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UIManager implements IAnimationScreen, AutoCloseable {
    private final AbstractContainerWidget rootContainer;
    private final Config config;

    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    public UIManager(Config config) {
        this.config = config;
        this.rootContainer = new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F);
        var window = Minecraft.getInstance().getWindow();
        initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public void initGui(int width, int height) {
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();

        var menuBar = buildTopMenuBar();
        rootContainer.addChild("top_menu_bar", menuBar);

        var dock = buildDockBar(width, height);
        rootContainer.addChild("app_dock", dock);

        rootContainer.addChild("app_window", new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F));

        var cursorWidget = new CursorWidget(config.layout.cursorWidgetSize);
        cursorWidget.setZ(100);
        rootContainer.addChild(cursorWidget.getName(), cursorWidget);

        var menuBarFinalY = menuBar.getY();
        menuBar.setY(-menuBar.getHeight());
        playAnimation(ObjectAnimator.ofFloat(menuBar::setY, menuBar.getY(), menuBarFinalY).setDuration(400L).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));
        playAnimation(ObjectAnimator.ofFloat(menuBar::setAlpha, 0.0F, 1.0F).setDuration(400L));

        var dockFinalY = dock.getY();
        dock.setY(rootContainer.getHeight());
        playAnimation(ObjectAnimator.ofFloat(dock::setY, dock.getY(), dockFinalY).setDuration(400L).setInterpolator(EasingFunctions.EASE_OUT_BACK));
        playAnimation(ObjectAnimator.ofFloat(dock::setAlpha, 0.0F, 1.0F).setDuration(400L));
    }

    private PanelWidget buildDockBar(int width, int height) {
        var barWidth = 196;
        var barHeight = 32;

        var bar = new PanelWidget((width - barWidth) / 2f, height - 2f * barHeight, barWidth, barHeight);
        bar.setName("app_dock");
        bar.setAlpha(0.0f);

        var back = new FillWidget(0.0F, 0.0F, barWidth, barHeight, 0x40000000);
        bar.addChild("back", back);

        var scrollPanel = new ScrollPanelWidget(2, 2, barWidth - 4, barHeight - 4, Orientation.HORIZONTAL);
        scrollPanel.setZ(1);
        bar.addChild("scroll_panel", scrollPanel);

        var x = 0f;
        for (var app : AppManager.getApps()) {
            var icon = new ImageButtonWidget(x, (scrollPanel.getHeight() - 22) / 2f, 22, 22, app.getIcon(), () -> openAppInWindow(app)) {
                @Override
                public void render(@NotNull WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
                    super.render(context, mouseX, mouseY, partialTick);
                    if (isHovered()) {
                        var font = Minecraft.getInstance().font;
                        var appName = Component.literal(app.getName());
                        var textWidth = font.width(appName) * 0.5f;

                        context.pose().pushPose();
                        context.pose().translate(-(textWidth - width) / 2f, 0, 0);
                        context.pose().scale(0.5f, 0.5f, 1);
                        {
                            var commands = GlyphCommandGenerator.generate(font, appName.getVisualOrderText(), 0, 0, 0xFFFFFFFF, false);
                            for (var command : commands) {
                                context.submit(command);
                            }
                        }
                        context.pose().popPose();
                    }
                }
            };
            scrollPanel.addChild("app_icon" + app.getName(), icon);
            x += 22 + 2;
        }

        return bar;
    }

    private Widget buildTopMenuBar() {
        var layout = config.layout;
        var barWidth = 150.0F * layout.scale;
        var barHeight = 20.0F * layout.scale;
        var padding = 3.0F * layout.scale;
        var screenWidth = rootContainer.getWidth();
        var menuBarX = screenWidth - barWidth - 20f * layout.scale;
        var menuBarY = 20f * layout.scale;

        var menuBar = new PanelWidget(menuBarX, menuBarY, barWidth, barHeight);
        menuBar.setName("top_menu_bar");
        menuBar.setAlpha(0.0F);

        var back = new FillWidget(0.0F, 0.0F, barWidth, barHeight, 0x40000000);
        menuBar.addChild("back", back);

        var iconSize = barHeight - padding * 2.0F;
        var icon = new ImageWidget(padding, padding, iconSize, iconSize, Resource.Textures.ICON_DATA_TERMINAL);
        icon.setZ(1);
        menuBar.addChild("icon", icon);

        var player = Minecraft.getInstance().player;
        var playerName = player != null ? player.getGameProfile().getName() : "N/A";
        var playerNameLabel = new LabelWidget(Component.literal(playerName), padding, padding, barWidth - padding * 2.0F, iconSize) {
            @Override
            public void tick() {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    setText(player.getGameProfile().getName());
                }
            }
        };
        playerNameLabel.setZ(1);
        playerNameLabel.setAlignment(LabelWidget.Alignment.RIGHT);
        playerNameLabel.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
        playerNameLabel.setDropShadow(false);
        menuBar.addChild("player_name", playerNameLabel);

        return menuBar;
    }

    public void openAppInWindow(App app) {
        var oldWindow = rootContainer.getChildren().get("app_window");
        if (oldWindow != null) {
            oldWindow.setVisible(false);
        }
        rootContainer.removeChild("app_window");

        var contentWidget = app.createUI(this);
        var title = app.getName();

        var titleBarSize = 16.0F;
        var padding = 2.0F;
        var windowWidth = contentWidget.getWidth() + padding * 2.0F;
        var windowHeight = contentWidget.getHeight() + titleBarSize + padding * 2.0F;

        var dock = rootContainer.getChildUnSafe("app_dock");
        var windowX = (rootContainer.getWidth() - windowWidth) / 2.0F;
        var windowY = (dock.getY() - windowHeight) / 2.0F;

        var windowFrame = new PanelWidget(windowX, windowY, windowWidth, windowHeight);
        windowFrame.setName("app_window");
        rootContainer.addChild(windowFrame.getName(), windowFrame);

        var back = new FillWidget(0.0F, 0.0F, windowWidth, windowHeight, 0x40000000);
        windowFrame.addChild("back", back);

        windowFrame.addChild("content", contentWidget);

        var titleBar = new PanelWidget(0.0F, 0.0F, windowWidth, titleBarSize);
        titleBar.setName("title_bar");
        windowFrame.addChild(titleBar.getName(), titleBar);

        var titleLabel = new LabelWidget(Component.literal(title), padding, 0.0F, windowWidth - padding * 2.0F - titleBarSize, titleBarSize);
        titleLabel.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
        titleBar.addChild("title_label", titleLabel);

        Runnable closeAction = () -> {
            var currentWindow = rootContainer.getChildren().get("app_window");
            if (currentWindow != null) {
                currentWindow.setVisible(false);
            }
            rootContainer.removeChild("app_window");
            rootContainer.addChild("app_window", new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F));
        };
        var closeButton = new ImageButtonWidget(windowWidth - titleBarSize, 0.0F, titleBarSize, titleBarSize, Resource.Textures.ICON_RANDOM, closeAction);
        closeButton.setDefaultHoverEffect(true);
        titleBar.addChild("close_button", closeButton);

        contentWidget.setX(padding);
        contentWidget.setY(titleBarSize + padding);

        windowFrame.setAlpha(0.0F);
        windowFrame.setY(windowFrame.getY() + 20.0F);
        playAnimation(ObjectAnimator.ofFloat(windowFrame::setAlpha, 0.0F, 1.0F).setDuration(200L));
        playAnimation(ObjectAnimator.ofFloat(windowFrame::setY, windowFrame.getY(), windowY).setDuration(200L).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));
    }

    public void tick() {
        rootContainer.tick();
    }

    public void resize() {
        var window = Minecraft.getInstance().getWindow();
        initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public AbstractContainerWidget getRootContainer() {
        return this.rootContainer;
    }

    @Override
    public @NotNull List<Animator> getScreenAnimations() {
        return screenAnimations;
    }

    @Override
    public @NotNull Map<Widget, List<Animator>> getTrackedAnimations() {
        return trackedAnimations;
    }

    @Override
    public void close() {
        cancelAllAnimations();
    }
}