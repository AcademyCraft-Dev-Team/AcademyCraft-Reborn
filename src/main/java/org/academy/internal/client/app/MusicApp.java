package org.academy.internal.client.app;

import net.minecraft.resources.Identifier;
import org.academy.api.client.Resource;
import org.academy.api.client.app.App;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.widget.*;

public class MusicApp implements App {
    public static final MusicApp INSTANCE = new MusicApp();

    @Override
    public Widget content() {
        return createContent();
    }

    private static FrameLayoutWidget createContent() {
        var content = new FrameLayoutWidget();
        content.setLayoutParams(
                new WidgetContainer.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
        );
        {
            var root = new LinearLayoutWidget();
            root.setOrientation(Orientation.VERTICAL);
            root.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));
            content.addChild("root", root);

            var vinylArea = new FrameLayoutWidget();
            vinylArea.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT));
            root.addChild("vinyl_area", vinylArea);

            var vinylWidget = new org.academy.api.client.gui.apps.MusicApp.VinylWidget(Resource.Textures.ICON_MUSIC_VINYL);
            vinylWidget.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .size(100, 100)
                    .gravity(Gravity.CENTER));
            vinylArea.addChild("vinyl", vinylWidget);

            var needle = new ImageWidget(Resource.Textures.ICON_MUSIC_NEEDLE);
            needle.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .size(60, 60)
                    .gravity(Gravity.TOP | Gravity.RIGHT)
                    .margin(0, 20, 30, 0));
            vinylArea.addChild("needle", needle);

            var controls = new LinearLayoutWidget();
            controls.setOrientation(Orientation.VERTICAL);
            controls.setSpacing(4);
            controls.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(70)
                    .padding(8));
            root.addChild("controls", controls);

            var infoRow = new LinearLayoutWidget();
            infoRow.setOrientation(Orientation.HORIZONTAL);
            infoRow.setLayoutParams(new LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(12));
            controls.addChild("info", infoRow);

            var titleLabel = new LabelWidget("Ready");
            titleLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams().weight(1).gravity(Gravity.CENTER_VERTICAL));
            infoRow.addChild("title", titleLabel);

            var timeLabel = new LabelWidget("0%");
            timeLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER_VERTICAL));
            infoRow.addChild("time", timeLabel);

            var progressContainer = new FrameLayoutWidget();
            progressContainer.setLayoutParams(new LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(4).margin(0, 2));
            controls.addChild("progress", progressContainer);

            var progressBg = new FillWidget(0x40FFFFFF);
            progressBg.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));
            progressContainer.addChild("bg", progressBg);

            var progressBarFill = new FillWidget(0xFF00BFFF);
            progressBarFill.setLayoutParams(new FrameLayoutWidget.LayoutParams().height(4).width(0));
            progressContainer.addChild("fill", progressBarFill);

            var btns = new LinearLayoutWidget();
            btns.setOrientation(Orientation.HORIZONTAL);
            btns.setSpacing(8);
            btns.setLayoutParams(new LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER).marginTop(6));
            controls.addChild("btns", btns);
        }
        return content;
    }

    @Override
    public String name() {
        return "Music";
    }

    @Override
    public Identifier icon() {
        return Resource.Textures.ICON_MUSIC_PLAYER;
    }

    private MusicApp() {
    }
}
