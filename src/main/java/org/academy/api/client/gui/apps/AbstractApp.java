package org.academy.api.client.gui.apps;

import net.minecraft.resources.Identifier;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.hud.terminal.DataTerminalHUD;
import org.jspecify.annotations.Nullable;

public abstract class AbstractApp extends LinearLayoutWidget {
    protected final ImageWidget appIcon;
    protected final FrameLayoutWidget contentContainer;
    private boolean contentFadedIn = false;

    public AbstractApp(String title, @Nullable Identifier iconRes) {
        setOrientation(Orientation.VERTICAL);
        setLayoutParams(new LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));

        var header = new FrameLayoutWidget();
        header.setLayoutParams(new LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(20)
                .margin(8, 5, 8, 0));
        addChild("header", header);

        {
            var leftGroup = new LinearLayoutWidget();
            leftGroup.setOrientation(Orientation.HORIZONTAL);
            leftGroup.setSpacing(4);
            leftGroup.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                    .gravity(Gravity.LEFT | Gravity.CENTER_VERTICAL));
            header.addChild("left_group", leftGroup);

            var logo = new ImageWidget(Resource.Textures.ICON_DATA_TERMINAL);
            logo.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                    .size(16, 16)
                    .gravity(Gravity.CENTER_VERTICAL));
            leftGroup.addChild("logo", logo);

            var backBtn = createBackButton();
            leftGroup.addChild("btn_back", backBtn);
        }

        {
            var centerGroup = new LinearLayoutWidget();
            centerGroup.setOrientation(Orientation.HORIZONTAL);
            centerGroup.setSpacing(4);
            centerGroup.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER));
            header.addChild("center_group", centerGroup);

            var titleLabel = new LabelWidget(title);
            titleLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER_VERTICAL));
            centerGroup.addChild("title", titleLabel);

            appIcon = new ImageWidget(iconRes);
            appIcon.setLayoutParams(new LayoutParams()
                    .size(20, 20)
                    .gravity(Gravity.CENTER_VERTICAL));

            if (iconRes == null) {
                appIcon.setVisibility(Visibility.INVISIBLE);
            }
            centerGroup.addChild("icon", appIcon);
        }

        var splitLine = new FillWidget(0xFFFFFFFF);
        splitLine.setLayoutParams(new LayoutParams()
                .height(1)
                .widthMode(SizeMode.MATCH_PARENT)
                .margin(0, 3, 0, 0));
        addChild("split_line", splitLine);

        contentContainer = new FrameLayoutWidget();
        contentContainer.setLayoutParams(new LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT));
        addChild("content", contentContainer);
        contentContainer.setAlpha(0.0f);
        initAppContent(contentContainer);
    }

    @Override
    public void render(RenderContext context) {
        var progress = DataTerminalHUD.getViewStateProgress();

        // 打开时淡入
        if (progress == 1.0f) {
            if (!contentFadedIn) {
                contentFadedIn = true;
                contentContainer.setAlpha(0.0f);
                contentContainer.startAnimation(
                        ObjectAnimator.ofFloat(
                                contentContainer::setAlpha,
                                0.0f, 1.0f
                        ).setDuration(250).setInterpolator(EasingFunctions.EASE_OUT_SINE)
                );
            }
            super.render(context);
            return;
        }

        // 关闭时淡出
        var fadeEnd = 0.6f;
        var alpha = (progress - fadeEnd) / (1.0f - fadeEnd);
        setAlpha(Math.max(0f, Math.min(1f, alpha)));
        if (progress <= fadeEnd) {
            contentFadedIn = false;
        }
        super.render(context);
    }

    protected abstract void initAppContent(FrameLayoutWidget content);

    public ImageWidget getAppIcon() {
        return appIcon;
    }

    private Widget createBackButton() {
        var btnContainer = new FrameLayoutWidget() {
            @Override
            protected void onMousePressed(MouseEvent event) {
                if (isMouseOver(event.getX(), event.getY())) {
                    ClientUtil.playDownSound();
                    DataTerminalHUD.closeApp();
                    event.consume();
                }
            }
        };
        btnContainer.setClickable(true);
        btnContainer.setLayoutParams(new LayoutParams()
                .width(42)
                .height(16)
                .marginTop(-1)
                .gravity(Gravity.CENTER_VERTICAL));

        var bg = new FillWidget(0x80000000);
        bg.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));
        btnContainer.addChild("bg", bg);

        var label = new LabelWidget("< Back");
        label.setLayoutParams(new FrameLayoutWidget.LayoutParams().gravity(Gravity.CENTER));
        btnContainer.addChild("text", label);

        return btnContainer;
    }
}