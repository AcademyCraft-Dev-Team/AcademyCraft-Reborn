package org.academy.api.client.util;

import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.screen.IAnimationScreen;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.gui.widget.ImageWidget;

public final class ScreenAnimationUtil {
    public static final long DURATION = 350L;
    private static final float Y_OFFSET = 20f;
    private static final float SCALE_START = 0.5f;

    private ScreenAnimationUtil() {
    }

    public static void show(IAnimationScreen screen, Widget widget) {
        screen.cancelAnimations(widget);
        widget.setAlpha(0f);
        widget.setVisible(true);
        widget.setEnabled(true);

        moveTranslationYShow(screen, widget);
        alphaShow(screen, widget);
    }

    public static void hide(IAnimationScreen screen, Widget widget) {
        screen.cancelAnimations(widget);
        widget.setEnabled(false);

        moveTranslationYHide(screen, widget);
        alphaHide(screen, widget, () -> {});
    }

    public static void moveTranslationYShow(IAnimationScreen screen, Widget widget) {
        widget.setTranslationY(Y_OFFSET);
        var anim = ObjectAnimator.ofFloat(widget::setTranslationY, Y_OFFSET, 0f)
                .setDuration(DURATION)
                .setInterpolator(EasingFunctions.EASE_OUT_BACK);
        screen.playTrackedAnimation(widget, anim);
    }

    public static void moveTranslationYHide(IAnimationScreen screen, Widget widget) {
        var startY = widget.getTranslationY();
        var anim = ObjectAnimator.ofFloat(widget::setTranslationY, startY, startY + Y_OFFSET)
                .setDuration(DURATION)
                .setInterpolator(EasingFunctions.EASE_IN_CUBIC);
        screen.playTrackedAnimation(widget, anim);
    }

    public static void alphaShow(IAnimationScreen screen, Widget widget) {
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(widget::setAlpha, 0f, 1f).setDuration(DURATION));
    }

    public static void alphaHide(IAnimationScreen screen, Widget widget, Runnable onEndCallback) {
        var alphaAnim = ObjectAnimator.ofFloat(widget::setAlpha, widget.getAlpha(), 0f).setDuration(DURATION);
        alphaAnim.addListener(createHideListener(widget, onEndCallback));
        screen.playTrackedAnimation(widget, alphaAnim);
    }

    public static void scaleShow(IAnimationScreen screen, ImageWidget widget) {
        widget.setWidthScale(SCALE_START);
        widget.setHeightScale(SCALE_START);
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(val -> {
            widget.setWidthScale(val);
            widget.setHeightScale(val);
        }, SCALE_START, 1.0f).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_OUT_BACK));
    }

    public static void scaleHide(IAnimationScreen screen, ImageWidget widget, Runnable onEndCallback) {
        var anim = ObjectAnimator.ofFloat(val -> {
            widget.setWidthScale(val);
            widget.setHeightScale(val);
        }, 1.0f, SCALE_START).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_IN_BACK);
        anim.addListener(createHideListener(widget, onEndCallback));
        screen.playTrackedAnimation(widget, anim);
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