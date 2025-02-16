package org.academy.internal.client.ui;

import icyllis.arc3d.core.Color;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.text.method.ScrollingMovementMethod;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.academy.api.client.command.CommandManager;
import org.academy.api.client.command.ConsoleSource;
import org.jetbrains.annotations.NotNull;

import static org.academy.api.client.command.CommandManager.HISTORY;

@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public class DeveloperFragment extends Fragment {
    private static Handler handler;
    private static ScrollView scrollView;
    private static final ConsoleSource consoleSource = new ConsoleSource();
    private static TextView historyTextView;
    private static EditText editText;
    private static final StringBuilder historyContent = new StringBuilder();
    private static int lastHistorySize = -1;

    private static final Runnable updateHistory = new Runnable() {
        @Override
        public void run() {
            if (HISTORY.size() > lastHistorySize) {
                lastHistorySize = HISTORY.size();
                historyContent.setLength(0);
                for (String entry : HISTORY) {
                    historyContent.append(entry).append("\n");
                }
                historyTextView.setText(historyContent.toString());
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = requireContext();
        FrameLayout base = new FrameLayout(context);
        FrameLayout terminalLayout = new FrameLayout(context);

        scrollView = new ScrollView(context);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                context.getResources().getDisplayMetrics().widthPixels * 2 / 3,
                context.getResources().getDisplayMetrics().heightPixels * 2 / 3,
                Gravity.CENTER
        );
        scrollView.setLayoutParams(scrollParams);

        historyTextView = new TextView(context);
        historyTextView.setTextColor(Color.GREEN);
        historyTextView.setPadding(16, 16, 16, 16);
        historyTextView.setMovementMethod(new ScrollingMovementMethod());
        scrollView.addView(historyTextView);

        editText = new EditText(context);
        editText.setHint("Enter command...");
        editText.setTextColor(Color.WHITE);
        editText.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        ));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                String command = editable.toString().trim();
                if (editable.toString().endsWith("\n")) {
                    if (!command.isEmpty()) {
                        HISTORY.add("MisakaCloud: " + command);
                        CommandManager.executeCommand(command, consoleSource);
                    }
                    editText.setText("");
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        GradientDrawable background = new GradientDrawable();
        background.setAlpha(128);
        background.setColor(Color.BLACK);
        background.setCornerRadius(32);

        scrollView.setBackground(background);

        terminalLayout.addView(scrollView);
        terminalLayout.addView(editText);
        base.addView(terminalLayout);

        handler = new Handler(Looper.myLooper());
        handler.post(updateHistory);
        return base;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateHistory);
    }
}