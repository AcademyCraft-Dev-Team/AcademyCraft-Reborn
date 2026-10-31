package org.academy.internal.common.ability.program;

import org.academy.api.common.profiler.AcademyProfiler;

/** Fixed-cardinality, opt-in query measurements. Disabled queries reuse one allocation-free scope. */
public final class ProgramQueryProfiling implements AutoCloseable {
    public enum Zone {
        ENTITIES_AROUND("program.query.entities_around"),
        STRUCTURES_AROUND("program.query.structures_around"),
        STRUCTURE_DISTANCE("program.query.structure_distance"),
        STRUCTURE_CONTENTS("program.query.structure_contents"),
        NEARBY("structure.query.nearby"),
        PROXIMITY("structure.query.proximity"),
        MEASURE("structure.query.measure"),
        SUMMARY_SCAN("structure.query.summary_scan");

        private final String label;
        Zone(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum Counter {
        CALLS("calls"), CANDIDATES("candidates"), ACCEPTED("accepted"), RESULTS("results"),
        TRUNCATED("truncated"), BACKENDS("backends"), BODIES("bodies"), BOXES("boxes"),
        CELLS_READ("cells_read"), COMPLETE("complete"), UNAVAILABLE("unavailable"),
        BUDGET_REJECTED("budget_rejected"), MISSING_CELL("missing_cell"),
        COVERAGE_UNAVAILABLE("coverage_unavailable");

        private final String label;
        Counter(String label) { this.label = label; }
        public String label() { return label; }
    }

    private static final ProgramQueryProfiling DISABLED = new ProgramQueryProfiling(false);
    private final boolean enabled;

    private ProgramQueryProfiling(boolean enabled) { this.enabled = enabled; }

    public static ProgramQueryProfiling begin(Zone zone) {
        if (!AcademyProfiler.isCapturingZones()) return DISABLED;
        AcademyProfiler.push(zone.label());
        AcademyProfiler.incrementCounter(Counter.CALLS.label());
        return new ProgramQueryProfiling(true);
    }

    public boolean enabled() { return enabled; }

    public void count(Counter counter, int value) {
        if (enabled) AcademyProfiler.incrementCounter(counter.label(), value);
    }

    @Override public void close() {
        if (enabled) AcademyProfiler.pop();
    }
}
