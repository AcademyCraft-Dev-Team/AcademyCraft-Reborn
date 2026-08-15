package org.academy.api.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, revisioned collection of program slots owned by one programmable skill.
 */
public record ProgramBook(
        int schemaVersion,
        long revision,
        int selectedSlot,
        List<Slot> slots
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProgramBook {
        if (schemaVersion <= 0) throw new IllegalArgumentException("Invalid program book schema");
        if (revision < 0) throw new IllegalArgumentException("Program book revision cannot be negative");
        slots = slots == null ? List.of() : List.copyOf(slots);
        if (slots.isEmpty()) {
            if (selectedSlot != 0) throw new IllegalArgumentException("Empty program book selects slot zero");
        } else if (selectedSlot < 0 || selectedSlot >= slots.size()) {
            throw new IllegalArgumentException("Selected program slot is out of range");
        }
    }

    public static ProgramBook empty(int slotCount) {
        if (slotCount <= 0) throw new IllegalArgumentException("Program book needs at least one slot");
        return new ProgramBook(
                CURRENT_SCHEMA_VERSION,
                0,
                0,
                java.util.Collections.nCopies(slotCount, Slot.EMPTY)
        );
    }

    public Slot slot(int index) {
        if (index < 0 || index >= slots.size()) throw new IndexOutOfBoundsException(index);
        return slots.get(index);
    }

    public ProgramBook replaceSlot(int index, @Nullable AbilityProgram program) {
        if (index < 0 || index >= slots.size()) throw new IndexOutOfBoundsException(index);
        var changed = new ArrayList<>(slots);
        changed.set(index, new Slot(program));
        return new ProgramBook(schemaVersion, revision + 1, selectedSlot, changed);
    }

    public ProgramBook select(int index) {
        if (index < 0 || index >= slots.size()) throw new IndexOutOfBoundsException(index);
        return index == selectedSlot
                ? this
                : new ProgramBook(schemaVersion, revision, index, slots);
    }

    /** Preserves existing slots while expanding or truncating a book to a new fixed slot count. */
    public ProgramBook resize(int slotCount) {
        if (slotCount <= 0) throw new IllegalArgumentException("Program book needs at least one slot");
        if (slotCount == slots.size()) return this;
        var resized = new ArrayList<Slot>(slotCount);
        for (var index = 0; index < slotCount; index++) {
            resized.add(index < slots.size() ? slots.get(index) : Slot.EMPTY);
        }
        return new ProgramBook(
                schemaVersion,
                revision,
                Math.clamp(selectedSlot, 0, slotCount - 1),
                resized
        );
    }

    public record Slot(@Nullable AbilityProgram program) {
        public static final Slot EMPTY = new Slot(null);

        public boolean empty() {
            return program == null;
        }
    }
}
