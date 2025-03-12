package org.academy.internal.client.ui;

import icyllis.arc3d.core.Color;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentContainerView;
import icyllis.modernui.fragment.FragmentTransaction;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RadialGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class PhoneFragment extends Fragment {
    private int id_container;
    private int homeId;
    private LinearLayout layout;
    private FrameLayout infoLayout;
    private TextView time;
    private FragmentContainerView containerView;
    private Handler handler;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

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
            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(layout.dp(680), layout.dp(460), Gravity.CENTER);
            final ShapeDrawable drawable = new ShapeDrawable();
            drawable.setShape(ShapeDrawable.HLINE);
            drawable.setColor(Color.argb(128, 69, 70, 72));
            drawable.setSize(-1, layout.dp(2));
            layout.setDividerDrawable(drawable);
            layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
            layout.setLayoutParams(params);
            layout.setBackground(new Background(layout));
        }
        {
            infoLayout = new FrameLayout(context);
            layout.addView(infoLayout);
        }
        {
            time = new TextView(context);
            time.setGravity(Gravity.RIGHT);
            infoLayout.addView(time);
        }
        {
            RadioGroup radioGroup = new RadioGroup(context);
            radioGroup.setOrientation(LinearLayout.HORIZONTAL);
            radioGroup.setGravity(Gravity.CENTER_HORIZONTAL);
            radioGroup.addView(createNavButton(homeId, "Home"));
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                if (checkedId == homeId) {
                    ft.replace(id_container, new HomeFragment());
                }
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .setReorderingAllowed(true)
                        .commit();
            });
            layout.addView(radioGroup);
        }
        {
            containerView = new FragmentContainerView(getContext());
            containerView.setId(id_container);
            final int padding = layout.dp(48);
            containerView.setPadding(padding, 0, padding, 0);
            layout.addView(containerView);
        }
        handler.post(updateTimeRunnable);
        return layout;
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
        final int dp6 = button.dp(6);
        button.setPadding(dp6, 0, dp6, 0);
        ThemeControl.addBackground(button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMarginsRelative(dp6 * 3, dp6, dp6 * 3, dp6);
        button.setLayoutParams(params);

        return button;
    }

    private static class Background extends Drawable {
        private final float mStrokeWidth;

        private Background(View view) {
            mStrokeWidth = view.dp(2);
        }

        @Override
        public void draw(@Nonnull Canvas canvas) {
            var bounds = getBounds();
            float left = bounds.left;
            float top = bounds.top;
            float right = bounds.right;
            float bottom = bounds.bottom;

            float centerX = (left + right) / 2;
            float centerY = (top + bottom) / 2;
            float radius = Math.max(right - left, bottom - top) / 2;
            float inner = mStrokeWidth * 0.5f;

            RadialGradient gradient = new RadialGradient(
                    centerX, centerY, radius,
                    Color.argb(128, 69, 70, 72),
                    Color.argb(128, 66, 67, 69),
                    Shader.TileMode.CLAMP,
                    null
            );

            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setShader(gradient);
            paint.setStrokeWidth(mStrokeWidth);

            canvas.drawRoundRect(left + inner, top + inner, right - inner,
                    bottom - inner, mStrokeWidth * 2, paint);

            invalidateSelf();
        }
    }
}