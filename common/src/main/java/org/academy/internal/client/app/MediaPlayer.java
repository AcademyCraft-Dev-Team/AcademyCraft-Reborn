package org.academy.internal.client.app;

import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.renderer.RenderTypes;

public final class MediaPlayer implements DataTerminalHUD.App {
    public static final DataTerminalHUD.App INSTANCE = new MediaPlayer();

    private PanelWidget create(){
        var width = 150f;
        var height = 200f;
        var root = new PanelWidget(0, 0, width, height);
        {
            var back = new BlendQuadWidget(0, 0, width, height);
            back.drawLine = false;
            back.alpha = 0.25f;
            root.addChild("back", back);

            var main = new LayeredPanelWidget(0, 0, width, height);
            root.addChild("main", main);
            {
                var dockBarHeight = 35f;
                var mediaListScrollBarWidth = 4f;
                var margin = 2f;
                var mediaList = new ScrollPanelWidget(0, 0, width - mediaListScrollBarWidth - margin, height - dockBarHeight);
                main.addChild("list_media", mediaList);
                {

                }

                var mediaListScrollBar = new VerticalScrollBarWidget(
                        mediaList,
                        mediaList.getWidth() - margin, margin,
                        mediaListScrollBarWidth, mediaList.getHeight() - margin
                );
                mediaListScrollBar.setThumbColor(0x20AAAAAA);
                mediaListScrollBar.setTrackColor(0x10202020);
                main.addChild("bar_list_media", mediaListScrollBar);

                var dockBar = new PanelWidget(0, height - dockBarHeight, width, dockBarHeight);
                main.addChild("bar_dock", dockBar);
                {
                    var dockBarBack = new BlendQuadWidget(0, 0, width, dockBarHeight);
                    dockBarBack.drawLine = false;
                    dockBarBack.alpha = 0.25f;
                    dockBar.addChild("back", dockBarBack);
                }
            }
        }
        return root;
    }

    @Override
    public RenderType getIcon() {
        return RenderTypes.RENDER_TYPE_APP_MEDIA_PLAYER;
    }

    @Override
    public String getName() {
        return "Media Player";
    }

    @Override
    public Runnable onClick() {
        return () -> DataTerminalHUD.setAppArea(create());
    }

    private MediaPlayer() {
    }
}