package org.academy.internal.client.ui;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
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
import icyllis.modernui.widget.*;
import net.minecraft.core.BlockPos;
import org.academy.AcademyCraft;
import org.academy.api.common.command.CommandManager;
import org.academy.api.common.command.ConsoleSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({"UnstableApiUsage", "DataFlowIssue"})
public class AbilityDeveloperFragment extends Fragment {
    private static BlockPos blockPos;
    private static Handler handler;
    private static EditText editText;
    private static final PopupWindow popupWindow = new PopupWindow();
    public static ConsoleSource consoleSource;

    public AbilityDeveloperFragment(@NotNull BlockPos mainPos) {
        super();
        blockPos = mainPos;
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = requireContext();
        FrameLayout base = new FrameLayout(context);
        FrameLayout terminalLayout = new FrameLayout(context);

        ScrollView scrollView = new ScrollView(context);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                context.getResources().getDisplayMetrics().widthPixels * 2 / 3,
                context.getResources().getDisplayMetrics().heightPixels * 2 / 3,
                Gravity.CENTER
        );
        scrollView.setLayoutParams(scrollParams);

        TextView historyTextView = new TextView(context);
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

        GradientDrawable background = new GradientDrawable();
        background.setAlpha(128);
        background.setColor(Color.BLACK);
        background.setCornerRadius(32);

        scrollView.setBackground(background);

        terminalLayout.addView(scrollView);
        terminalLayout.addView(editText);
        base.addView(terminalLayout);

        handler = new Handler(Looper.myLooper());
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                popupWindow.dismiss();
                ParseResults<ConsoleSource> parseResults =
                        CommandManager.Client.dispatcher.parse(editText.getText().toString(), consoleSource);
                CompletableFuture<Suggestions> suggestionsCompletableFuture =
                        CommandManager.Client.dispatcher.getCompletionSuggestions(parseResults);
                suggestionsCompletableFuture.thenAccept(suggestions -> {
                    ArrayList<String> list = new ArrayList<>();
                    for (Suggestion suggestion : suggestions.getList()) {
                        String suggestionText = suggestion.getText();
                        list.add(suggestionText);
                    }
                    handler.post(() -> {
                        ListView listView = new ListView(requireContext());
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), list);
                        listView.setAdapter(adapter);
                        popupWindow.setContentView(listView);
                        popupWindow.setWidth(base.dp(135));
                        popupWindow.setHeight(base.dp(240));
                        popupWindow.setFocusable(false);
                        listView.setOnItemClickListener((parent, view, position, id) -> {
                            popupWindow.dismiss();
                            String selectedItem = adapter.getItem(position);
                            String currentText = editText.getText().toString();

                            if (currentText.isEmpty()) {
                                editText.setText(selectedItem);
                            } else {
                                String[] strings = currentText.split(" ");
                                String lastWord = strings[strings.length - 1];

                                if (selectedItem.startsWith(lastWord) && !lastWord.equals(selectedItem)) {
                                    String prefix = String.join(" ", Arrays.copyOf(strings, strings.length - 1));
                                    String newText = prefix.isEmpty() ? selectedItem : prefix + " " + selectedItem;
                                    editText.setText(newText);
                                } else {
                                    String trimmedText = currentText.trim();
                                    editText.setText(trimmedText + " " + selectedItem);
                                }
                            }

                            editText.requestFocus();
                            editText.setSelection(editText.getText().length());
                        });
                        GradientDrawable popupWindowBack = new GradientDrawable();
                        popupWindowBack.setAlpha(128);
                        popupWindowBack.setColor(Color.BLACK);
                        popupWindowBack.setCornerRadius(32);
                        popupWindow.setBackgroundDrawable(popupWindowBack);
                        popupWindow.showAsDropDown(editText, 0, -base.dp(256));
                    });
                });
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        editText.requestFocus();
        consoleSource = new ConsoleSource(blockPos, handler, scrollView, historyTextView);
        consoleSource.clearHistory();
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                String command = editable.toString().trim();
                if (editable.toString().endsWith("\n")) {
                    if (!command.isEmpty()) {
                        consoleSource.addHistory("MisakaCloud: " + command);
                        AcademyCraft.executorService.execute(() -> CommandManager.Client.executeCommand(command, consoleSource));
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
        return base;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        popupWindow.dismiss();
    }

    public static void addHistory(String history) {
        if (consoleSource != null) {
            consoleSource.addHistory(history);
        }
    }

    public static void addHistory(Collection<String> history) {
        if (consoleSource != null) {
            consoleSource.addHistory(history);
        }
    }
}