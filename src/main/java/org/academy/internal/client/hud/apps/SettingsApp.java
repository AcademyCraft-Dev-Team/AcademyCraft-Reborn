package org.academy.internal.client.hud.apps;

import org.academy.api.client.gui.layout.Gravity;
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
            root.addChild("content", content);
            {
                var infoArea = new ScrollPanelWidget();
                infoArea.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .weight(5f / 6)
                                .height(0)
                                .widthMode(SizeMode.MATCH_PARENT)
                );

                var terminalArea = new ScrollPanelWidget();

                var tabBar = new RadioGroupWidget();
                tabBar.setOrientation(Orientation.HORIZONTAL);
                tabBar.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .weight(1f / 6)
                                .height(0)
                                .widthMode(SizeMode.MATCH_PARENT)
                );
                content.addChild("tab_bar", tabBar);
                {
                    var terminal = new RadioButtonWidget();
                    terminal.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .weight(1f / 2)
                                    .heightMode(SizeMode.MATCH_PARENT)
                                    .gravity(Gravity.CENTER)
                    );
                    tabBar.addChild("terminal", terminal);
                    {
                        var label = new LabelWidget("Settings");
                        label.setLayoutParams(
                                new FrameLayoutWidget.LayoutParams()
                                        .gravity(Gravity.CENTER)
                        );
                        terminal.addChild("content", label);
                    }

                    var keyBinding = new RadioButtonWidget();
                    keyBinding.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .weight(1f / 2)
                                    .heightMode(SizeMode.MATCH_PARENT)
                                    .gravity(Gravity.CENTER)
                    );
                    tabBar.addChild("key_binding", keyBinding);
                    {
                        var label = new LabelWidget("KeyBinding");
                        label.setLayoutParams(
                                new FrameLayoutWidget.LayoutParams()
                                        .gravity(Gravity.CENTER)
                        );
                        keyBinding.addChild("content", label);
                    }
                }
                var line = new FillWidget(-1);
                line.setAlpha(0.75f);
                line.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .widthMode(SizeMode.MATCH_PARENT)
                                .height(1)
                );
                content.addChild("line", line);
                content.addChild("info_area", infoArea);
            }
        }
        return root;
    }

    private SettingsApp() {
    }
}