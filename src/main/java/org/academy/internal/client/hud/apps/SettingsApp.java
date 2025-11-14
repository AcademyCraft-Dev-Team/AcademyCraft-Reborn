package org.academy.internal.client.hud.apps;

import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.widget.*;
import org.academy.internal.client.hud.DataTerminalHUD;

public final class SettingsApp {
    public static FrameLayoutWidget create() {
        var root = new FrameLayoutWidget();
        {
            var back = new FillWidget(DataTerminalHUD.COLOR);
            back.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            root.addChild("back", back);

            var content = new LinearLayoutWidget();
            content.setOrientation(Orientation.VERTICAL);
            content.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            root.addChild("content", content);
            {
                var infoArea = new ScrollPanelWidget();
                infoArea.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .weight(5f / 6)
                                .widthMode(SizeMode.MATCH_PARENT)
                );

                var tabBar = new RadioGroupWidget();
                tabBar.setOrientation(Orientation.HORIZONTAL);
                tabBar.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .weight(1f / 6)
                                .widthMode(SizeMode.MATCH_PARENT)
                );
                root.addChild("tab_bar", tabBar);
                {
                }
                root.addChild("info_area", infoArea);
            }
        }
        return root;
    }

    private SettingsApp() {
    }
}