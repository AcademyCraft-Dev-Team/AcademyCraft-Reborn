package org.academy.internal.common.sublevel;

/** Lifecycle of a plot-backed structure. Only ACTIVE/SLEEPING structures should own a body. */
public enum SubLevelState {
    ALLOCATED,
    LOADING,
    ACTIVE,
    SLEEPING,
    INACTIVE,
    REMOVED
}
