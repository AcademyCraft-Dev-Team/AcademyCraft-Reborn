package org.academy.api.common.command;

import icyllis.modernui.core.Handler;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConsoleSource {
    public final BlockPos mainPos;
    public final Handler handler;
    public final ScrollView scrollView;
    public final TextView historyTextView;
    private final List<String> HISTORY = new CopyOnWriteArrayList<>();
    private final StringBuilder historyContent = new StringBuilder();

    public ConsoleSource(BlockPos blockPos, Handler handler, ScrollView scrollView, TextView historyTextView) {
        this.mainPos = blockPos;
        this.handler = handler;
        this.scrollView = scrollView;
        this.historyTextView = historyTextView;
    }

    public void addHistory(String history) {
        HISTORY.add(history);
        update();
    }

    public void addHistory(Collection<String> history) {
        HISTORY.addAll(history);
    }

    public void clearHistory() {
        HISTORY.clear();
        update();
    }

    public void update() {
        handler.post(() -> {
            historyContent.setLength(0);
            for (String entry : HISTORY) {
                historyContent.append(entry).append("\n");
            }
            historyTextView.setText(historyContent.toString());
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}