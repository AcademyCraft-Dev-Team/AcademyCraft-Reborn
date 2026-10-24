package org.academy.internal.common.structure.upgrade.lifecycle;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** A failed or partial rollback never authorizes publishing its original source. */
final class RestorationRollback {
    private RestorationRollback() {}

    static <T> boolean clear(List<T> cells, Predicate<T> exactImage, Predicate<T> clearCell,
                             BooleanSupplier save) {
        // Verify the complete image before the first mutation; a later missing/foreign cell
        // must not cause an otherwise untouched prefix to be erased.
        for (var cell : cells) if (!exactImage.test(cell)) return false;
        for (var cell : cells) if (!clearCell.test(cell)) return false;
        return save.getAsBoolean();
    }
}
