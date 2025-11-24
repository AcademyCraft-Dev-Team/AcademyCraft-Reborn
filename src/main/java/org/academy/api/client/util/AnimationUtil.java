package org.academy.api.client.util;

import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.Widget;

public final class AnimationUtil {
    public static final long DURATION = 350L;
    private static final float Y_OFFSET = 20f;
    private static final float SCALE_START = 0.5f;

    private AnimationUtil() {
    }

    public static void show(Widget widget) {
        widget.cancelAnimations();
        widget.setAlpha(0f);
        widget.setVisible(true);
        widget.setEnabled(true);

        moveTranslationYShow(widget);
        alphaShow(widget);
    }

    public static void hide(Widget widget) {
        widget.cancelAnimations();
        widget.setEnabled(false);

        moveTranslationYHide(widget);
        alphaHide(widget, () -> {});
    }

    public static void moveTranslationYShow(Widget widget) {
        widget.setTranslationY(Y_OFFSET);
        var anim = ObjectAnimator.ofFloat(widget::setTranslationY, Y_OFFSET, 0f)
                .setDuration(DURATION)
                .setInterpolator(EasingFunctions.EASE_OUT_BACK);
        widget.startAnimation(anim);
    }

    public static void moveTranslationYHide(Widget widget) {
        var startY = widget.getTranslationY();
        var anim = ObjectAnimator.ofFloat(widget::setTranslationY, startY, startY + Y_OFFSET)
                .setDuration(DURATION)
                .setInterpolator(EasingFunctions.EASE_IN_CUBIC);
        widget.startAnimation(anim);
    }

    public static void alphaShow(Widget widget) {
        widget.startAnimation(ObjectAnimator.ofFloat(widget::setAlpha, 0f, 1f).setDuration(DURATION));
    }

    public static void alphaHide(Widget widget, Runnable onEndCallback) {
        var alphaAnim = ObjectAnimator.ofFloat(widget::setAlpha, widget.getAlpha(), 0f).setDuration(DURATION);
        alphaAnim.addListener(createHideListener(widget, onEndCallback));
        widget.startAnimation(alphaAnim);
    }

    public static void scaleShow(ImageWidget widget) {
        widget.setWidthScale(SCALE_START);
        widget.setHeightScale(SCALE_START);
        widget.startAnimation(ObjectAnimator.ofFloat(val -> {
            widget.setWidthScale(val);
            widget.setHeightScale(val);
        }, SCALE_START, 1.0f).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_OUT_BACK));
    }

    public static void scaleHide(ImageWidget widget, Runnable onEndCallback) {
        var anim = ObjectAnimator.ofFloat(val -> {
            widget.setWidthScale(val);
            widget.setHeightScale(val);
        }, 1.0f, SCALE_START).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_IN_BACK);
        anim.addListener(createHideListener(widget, onEndCallback));
        widget.startAnimation(anim);
    }

    private static AnimatorListener createHideListener(Widget widget, Runnable onEndCallback) {
        return new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                widget.setVisible(false);
                onEndCallback.run();
            }
        };
    }
}