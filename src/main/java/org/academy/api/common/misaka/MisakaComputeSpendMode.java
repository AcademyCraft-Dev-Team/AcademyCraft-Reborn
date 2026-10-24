package org.academy.api.common.misaka;

/**
 * How a registered device spends its player compute share each settle window.
 * <ul>
 *   <li>{@link #ACTUAL} — module calls {@link MisakaComputeAccess#consumeRate}; unused share can top up FORCE.</li>
 *   <li>{@link #FORCE} — network reserves the full share and delivers it once via {@code onCompute}.</li>
 * </ul>
 */
public enum MisakaComputeSpendMode {
    ACTUAL,
    FORCE
}
