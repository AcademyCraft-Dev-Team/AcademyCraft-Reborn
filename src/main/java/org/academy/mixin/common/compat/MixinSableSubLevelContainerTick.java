package org.academy.mixin.common.compat;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.SableContainerGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Version-gated compatibility patch for the audited Sable artifact. {@code SubLevelContainer.tick}
 * iterates its live {@code ReferenceArrayList} instances through fastutil's cached-array
 * {@code forEach}; a body that adds or removes a sub-level during its own heat-map split makes the
 * cached array stale and throws {@code ArrayIndexOutOfBoundsException}. Iterating a snapshot keeps
 * additions safe (they tick from the next tick) and lets {@code processSubLevelRemovals} own
 * removals. Each callback is also marked in {@link SableContainerGuard} so Academy-side mutations
 * are deferred instead of joining the live iteration.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer", remap = false)
public abstract class MixinSableSubLevelContainerTick {
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                    ordinal = 0
            ),
            require = 1
    )
    private void academy$snapshotSubLevelIteration(List<?> subLevels, Consumer<?> tick) {
        var snapshot = new ArrayList<>(subLevels);
        var existing = Collections.newSetFromMap(new IdentityHashMap<>());
        existing.addAll(snapshot);
        academy$runGuarded(snapshot, tick);
        // A heat-map split can create a child after the snapshot was taken. Its full tick
        // waits until next time, but physics chunk tickets run now and need assembled world
        // bounds. Do not update existing bodies twice: that would erase their previous bounds.
        for (var value : new ArrayList<>(subLevels)) {
            if (existing.contains(value)) continue;
            var source = SableReflection.optional(
                    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel.class, value);
            if (source != null && !source.isRemoved()
                    && !org.academy.internal.common.structure.sable.SableRestorationIsolation.hidden(source)) {
                source.updateBoundingBox();
            }
        }
    }

    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                    ordinal = 1
            ),
            require = 1
    )
    private void academy$snapshotObserverIteration(List<?> observers, Consumer<?> tick) {
        academy$runGuarded(new ArrayList<>(observers), tick);
    }

    @SuppressWarnings("unchecked")
    private static void academy$runGuarded(List<?> values, Consumer<?> action) {
        var consumer = (Consumer<Object>) action;
        for (var value : values) {
            var source = SableReflection.optional(
                    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel.class, value);
            if (source != null && org.academy.internal.common.structure.sable.SableRestorationIsolation.hidden(source)) continue;
            SableContainerGuard.enterContainerTick();
            try {
                consumer.accept(value);
            } finally {
                SableContainerGuard.exitContainerTick();
            }
        }
    }
}
