package org.academy.internal.common.entitycontrol;

final class EntityMotionPolicy {
    private EntityMotionPolicy() {
    }

    static boolean shouldBlock(
            boolean internalCorrection,
            boolean imprisoned,
            boolean forcedMovementProtection,
            boolean hasExplicitSource,
            boolean sourceIsTarget,
            boolean fallbackIsSelfSource
    ) {
        if (internalCorrection) return false;
        if (imprisoned) return true;
        if (!forcedMovementProtection) return false;
        return hasExplicitSource ? !sourceIsTarget : !fallbackIsSelfSource;
    }

    static boolean shouldBlockExternalManipulation(
            boolean forcedMovementProtection,
            boolean sourceIsTarget
    ) {
        return shouldBlock(
                false,
                false,
                forcedMovementProtection,
                true,
                sourceIsTarget,
                false
        );
    }
}
