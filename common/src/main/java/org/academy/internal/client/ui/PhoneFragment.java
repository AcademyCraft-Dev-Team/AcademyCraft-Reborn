package org.academy.internal.client.ui;

import icyllis.modernui.R;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentContainerView;
import icyllis.modernui.fragment.FragmentTransaction;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class PhoneFragment extends Fragment {
    private static final ColorStateList NAV_BUTTON_COLOR = new ColorStateList(
            new int[][]{
                    new int[]{R.attr.state_checked},
                    StateSet.get(StateSet.VIEW_STATE_HOVERED),
                    StateSet.WILD_CARD},
            new int[]{
                    0xFFFFFFFF, // selected
                    0xFFE0E0E0, // hovered
                    0xFFB4B4B4} // other
    );
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private int id_container;
    private int homeId;
    private LinearLayout root;
    private RadioGroup mainButtonTab;
    private FrameLayout infoLayout;
    private TextView time;
    private FragmentContainerView containerView;
    private Handler handler;
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            LocalTime now = LocalTime.now();
            time.setText(now.format(formatter));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);
        id_container = View.generateViewId();
        homeId = View.generateViewId();
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.replace(id_container, new HomeFragment());
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .setReorderingAllowed(true)
                .commit();
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        final Context context = requireContext();
        handler = new Handler(Looper.myLooper());
        {
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
            root.setDividerDrawable(ThemeControl.makeDivider(root));
        }
        {
            mainButtonTab = new RadioGroup(context);
            mainButtonTab.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            root.addView(mainButtonTab, params);
        }
        {
            mainButtonTab.addView(createNavButton(101, "Home"));
            mainButtonTab.addView(createNavButton(102, "Skills"));
            mainButtonTab.addView(createNavButton(103, "Settings"));
        }
        {
            containerView = new FragmentContainerView(getContext());
            containerView.setId(id_container);
            final int padding = root.dp(48);
            containerView.setPadding(padding, 0, padding, 0);
            root.addView(containerView);
        }
        handler.post(updateTimeRunnable);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(updateTimeRunnable);
    }

    private RadioButton createNavButton(int id, String text) {
        RadioButton button = new RadioButton(getContext());
        button.setId(id);
        button.setText(I18n.get(text));
        button.setTextSize(16);
        button.setTextColor(NAV_BUTTON_COLOR);
        final int dp6 = button.dp(6);
        button.setPadding(dp6, 0, dp6, 0);
        ThemeControl.addBackground(button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMarginsRelative(dp6 * 3, dp6, dp6 * 3, dp6);
        button.setLayoutParams(params);

        return button;
    }
}