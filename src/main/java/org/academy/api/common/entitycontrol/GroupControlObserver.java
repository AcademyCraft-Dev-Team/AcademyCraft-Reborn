package org.academy.api.common.entitycontrol;

/** Optional observer for adapters that can report autonomous task progress. */
@FunctionalInterface
public interface GroupControlObserver {
    GroupControlObserver NONE = event -> {
    };

    void onTaskEvent(GroupControlTaskEvent event);
}
