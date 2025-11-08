package org.academy.api.client.gui.util;

import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.IAnimationScreen;
import org.academy.api.client.gui.screen.RenderRoot;
import org.academy.api.client.gui.widget.*;

public final class InfoAreaUtil {
    public static LinearLayoutWidget create(RenderRoot ui, IAnimationScreen animation, float left, float top) {
        var infoArea = new FrameLayoutWidget();
        infoArea.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .margin(left, top, 0, 0)
                        .sizeMode(SizeMode.WRAP_CONTENT)
        );
        ui.getRoot().addChild("area_info", infoArea);
        animation.playAnimation(ObjectAnimator.ofFloat(infoArea::setAlpha, 0, 1).setDuration(600));
        animation.playAnimation(ObjectAnimator.ofFloat(infoArea::setTranslationY, 20, 0).setDuration(600).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));
        {
            var back = new BlendQuadWidget();
            back.setAlpha(0.5f);
            back.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            infoArea.addChild("back", back);

            var info = new LinearLayoutWidget();
            info.setOrientation(Orientation.VERTICAL);
            info.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .marginTop(6.5f)
                            .marginBottom(6.5f)
                            .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            );
            info.setSpacing(2);
            infoArea.addChild("info", info);
            return info;
        }
    }

    public static LinearLayoutWidget createInfoRow(String labelText, String iconName, int iconColor, LabelWidget valueLabel) {
        var layout = new LinearLayoutWidget();
        layout.setOrientation(Orientation.HORIZONTAL);
        layout.setSpacing(4);
        layout.setLayoutParams(
                new WidgetContainer.LayoutParams()
                        .width(128)
                        .heightMode(SizeMode.WRAP_CONTENT)
                        .padding(6.5f, 0, 10, 0)
        );
        {
            var icon = new FillWidget(iconColor);
            icon.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .gravity(Gravity.CENTER_VERTICAL)
                            .size(6.5f, 6.5f)
            );
            layout.addChild(iconName, icon);

            var label = new LabelWidget(labelText);
            label.setScale(0.75f);
            label.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .gravity(Gravity.CENTER_VERTICAL)
            );
            layout.addChild(labelText.toLowerCase() + "_label", label);

            var empty = new EmptyWidget();
            empty.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .weight(1)
                            .heightMode(SizeMode.MATCH_PARENT)
            );
            layout.addChild("empty", empty);

            valueLabel.setScale(0.75f);
            layout.addChild(labelText.toLowerCase() + "_value_label", valueLabel);
        }
        return layout;
    }

    public static LinearLayoutWidget createAttributeRow(String labelText, Widget valueWidget, float scale) {
        var layout = new LinearLayoutWidget();
        layout.setOrientation(Orientation.HORIZONTAL);
        layout.setSpacing(8);
        layout.setLayoutParams(
                new WidgetContainer.LayoutParams()
                        .width(128)
                        .heightMode(SizeMode.WRAP_CONTENT)
                        .padding(10, 0)
        );
        {
            var label = new LabelWidget(labelText);
            label.setScale(scale);
            label.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .gravity(Gravity.CENTER_VERTICAL)
            );
            layout.addChild(labelText.toLowerCase().replace(" ", "_") + "_label", label);

            var empty = new EmptyWidget();
            empty.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .weight(1)
                            .heightMode(SizeMode.MATCH_PARENT)
            );
            layout.addChild("empty", empty);

            layout.addChild("value_widget", valueWidget);
        }
        return layout;
    }

    public static LinearLayoutWidget createInputRow(TextBoxWidget textBox) {
        var layout = new LinearLayoutWidget();
        layout.setOrientation(Orientation.HORIZONTAL);
        {
            var leftBracket = new LabelWidget("[");
            leftBracket.setScale(0.85f);
            leftBracket.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .gravity(Gravity.CENTER_VERTICAL)
            );
            layout.addChild("bracket_left", leftBracket);

            textBox.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .width(48)
                            .heightMode(SizeMode.MATCH_PARENT)
                            .gravity(Gravity.CENTER)
            );
            textBox.setScale(0.75f);
            layout.addChild("textbox", textBox);

            var rightBracket = new LabelWidget("]");
            rightBracket.setScale(0.85f);
            rightBracket.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .gravity(Gravity.CENTER_VERTICAL)
            );
            layout.addChild("bracket_right", rightBracket);
        }
        return layout;
    }

    private InfoAreaUtil() {
    }
}