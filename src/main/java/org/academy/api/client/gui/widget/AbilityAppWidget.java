package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.util.ClientUtil;
import org.academy.internal.client.hud.DataTerminalHUD;

public class AbilityAppWidget extends FrameLayoutWidget {

    public AbilityAppWidget() {
        this.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));

        var header = new LinearLayoutWidget();
        header.setOrientation(Orientation.HORIZONTAL);
        header.setSpacing(5);
        header.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(20)
                .margin(5));
        this.addChild("header", header);

        var backLabel = new LabelWidget("< Back") {
            @Override
            protected void onMousePressed(MouseEvent event) {
                if (isMouseOver(event.getX(), event.getY())) {
                    ClientUtil.playDownSound();
                    DataTerminalHUD.closeApp();
                    event.consume();
                }
            }
        };
        backLabel.setClickable(true);
        backLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams().padding(2));
        header.addChild("btn_back", backLabel);

        var titleLabel = new LabelWidget("Ability Developer");
        header.addChild("title", titleLabel);

        var content = new LabelWidget("TODO: Skill Tree Implementation");
        content.setLayoutParams(new FrameLayoutWidget.LayoutParams().gravity(Gravity.CENTER));
        this.addChild("content", content);
    }
}