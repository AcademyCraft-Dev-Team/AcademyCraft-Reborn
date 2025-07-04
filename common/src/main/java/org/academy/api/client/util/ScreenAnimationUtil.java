package org.academy.api.client.util;

import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.framework.CGuiScreen;
import org.academy.api.client.gui.widget.PanelWidget;

public final class ScreenAnimationUtil {
    private ScreenAnimationUtil() {
    }

    public static void show(CGuiContainerScreen<?> screen, PanelWidget panel, float finalY) {
        screen.cancelAnimations(panel);
        var duration = 350L;
        var yOffset = 20f;

        panel.setY(finalY + yOffset);
        panel.setAlpha(0f);
        panel.setVisible(true);
        panel.setEnabled(true);

        screen.playTrackedAnimation(panel, ObjectAnimator.ofFloat(panel::setY, panel.getY(), finalY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_BACK));
        screen.playTrackedAnimation(panel, ObjectAnimator.ofFloat(panel::setAlpha, 0f, 1f).setDuration(duration / 2).setInterpolator(EasingFunctions.LINEAR));
    }

    public static void show(CGuiScreen screen, PanelWidget panel, float finalY) {
        screen.cancelAnimations(panel);
        var duration = 350L;
        var yOffset = 20f;

        panel.setY(finalY + yOffset);
        panel.setAlpha(0f);
        panel.setVisible(true);
        panel.setEnabled(true);

        screen.playTrackedAnimation(panel, ObjectAnimator.ofFloat(panel::setY, panel.getY(), finalY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_BACK));
        screen.playTrackedAnimation(panel, ObjectAnimator.ofFloat(panel::setAlpha, 0f, 1f).setDuration(duration / 2).setInterpolator(EasingFunctions.LINEAR));
    }

    public static void hide(CGuiContainerScreen<?> screen, PanelWidget panel, float startY) {
        screen.cancelAnimations(panel);
        var duration = 300L;
        var yOffset = 20f;

        var listener = new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                panel.setVisible(false);
                panel.setEnabled(false);
                panel.setY(startY);
            }
        };

        var alphaAnim = ObjectAnimator.ofFloat(panel::setAlpha, panel.getAlpha(), 0f).setDuration(duration).setInterpolator(EasingFunctions.LINEAR);
        alphaAnim.addListener(listener);

        var yAnim = ObjectAnimator.ofFloat(panel::setY, startY, startY + yOffset).setDuration(duration).setInterpolator(EasingFunctions.EASE_IN_CUBIC);

        screen.playTrackedAnimation(panel, alphaAnim);
        screen.playTrackedAnimation(panel, yAnim);
    }

    public static void hide(CGuiScreen screen, PanelWidget panel, float startY) {
        screen.cancelAnimations(panel);
        var duration = 300L;
        var yOffset = 20f;

        var listener = new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                panel.setVisible(false);
                panel.setEnabled(false);
                panel.setY(startY);
            }
        };

        var alphaAnim = ObjectAnimator.ofFloat(panel::setAlpha, panel.getAlpha(), 0f).setDuration(duration).setInterpolator(EasingFunctions.LINEAR);
        alphaAnim.addListener(listener);

        var yAnim = ObjectAnimator.ofFloat(panel::setY, startY, startY + yOffset).setDuration(duration).setInterpolator(EasingFunctions.EASE_IN_CUBIC);

        screen.playTrackedAnimation(panel, alphaAnim);
        screen.playTrackedAnimation(panel, yAnim);
    }
}