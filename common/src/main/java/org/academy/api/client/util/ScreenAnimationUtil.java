package org.academy.api.client.util;

import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.IAnimationScreen;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;

public final class ScreenAnimationUtil {
    public static final long DURATION = 350L;
    private static final float Y_OFFSET = 20f;
    private static final float X_OFFSET = 20f;
    private static final float SCALE_START = 0.5f;

    private ScreenAnimationUtil() {
    }

    public static void alphaShow(IAnimationScreen screen, Widget widget) {
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(widget::setAlpha, 0f, 1f).setDuration(DURATION).setInterpolator(EasingFunctions.LINEAR));
    }

    public static void alphaHide(IAnimationScreen screen, Widget widget, Runnable onEndCallback) {
        var listener = new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                widget.setVisible(false);
                widget.setEnabled(false);
                if (onEndCallback != null) {
                    onEndCallback.run();
                }
            }
        };

        var alphaAnim = ObjectAnimator.ofFloat(widget::setAlpha, widget.getAlpha(), 0f).setDuration(DURATION).setInterpolator(EasingFunctions.LINEAR);
        alphaAnim.addListener(listener);
        screen.playTrackedAnimation(widget, alphaAnim);
    }

    public static void moveYShow(IAnimationScreen screen, Widget widget, float finalY) {
        widget.setY(finalY + Y_OFFSET);
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(widget::setY, widget.getY(), finalY).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_OUT_BACK));
    }

    public static void moveYHide(IAnimationScreen screen, Widget widget, float startY) {
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(widget::setY, startY, startY + Y_OFFSET).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_IN_CUBIC));
    }

    public static void moveXShow(IAnimationScreen screen, Widget widget, float finalX) {
        widget.setX(finalX - X_OFFSET);
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(widget::setX, widget.getX(), finalX).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_OUT_BACK));
    }

    public static void moveXHide(IAnimationScreen screen, Widget widget, float startX) {
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(widget::setX, startX, startX - X_OFFSET).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_IN_CUBIC));
    }

    public static void scaleShow(IAnimationScreen screen, ImageWidget widget) {
        widget.widthScale = SCALE_START;
        widget.heightScale = SCALE_START;
        screen.playTrackedAnimation(widget, ObjectAnimator.ofFloat(val -> {
            widget.widthScale = val;
            widget.heightScale = val;
        }, SCALE_START, 1.0f).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_OUT_BACK));
    }

    public static void scaleHide(IAnimationScreen screen, ImageWidget widget, Runnable onEndCallback) {
        var listener = new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                widget.setVisible(false);
                widget.setEnabled(false);
                if (onEndCallback != null) {
                    onEndCallback.run();
                }
            }
        };

        var anim = ObjectAnimator.ofFloat(val -> {
            widget.widthScale = val;
            widget.heightScale = val;
        }, 1.0f, SCALE_START).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_IN_BACK);
        anim.addListener(listener);
        screen.playTrackedAnimation(widget, anim);
    }

    public static void show(IAnimationScreen screen, PanelWidget panel, float finalY) {
        screen.cancelAnimations(panel);

        panel.setAlpha(0f);
        panel.setVisible(true);
        panel.setEnabled(true);

        moveYShow(screen, panel, finalY);
        alphaShow(screen, panel);
    }

    public static void hide(IAnimationScreen screen, PanelWidget panel, float startY) {
        screen.cancelAnimations(panel);

        moveYHide(screen, panel, startY);
        alphaHide(screen, panel, () -> panel.setY(startY));
    }
}