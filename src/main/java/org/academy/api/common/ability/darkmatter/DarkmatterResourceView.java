package org.academy.api.common.ability.darkmatter;

/** Read-only snapshot of the server-owned dark-matter resource ledger. */
public record DarkmatterResourceView(
        float naturalMatter,
        float createdMatter,
        float createdCpDebt,
        float reservedMatter,
        float baseCapacity,
        float effectiveCapacity
) {
    public float totalMatter() {
        return naturalMatter + createdMatter;
    }
}
