package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Bounded observation of one saved invocation; completion does not end persistent ability effects. */
public record ProgramRunStatus(
        UUID token, Target target, long runSequence, long updateSequence, Phase phase, WaitKind waitKind,
        int nodeId, int sourceNodeId, @Nullable Identifier nodeType,
        long logicalTick, long wakeAtLogicalTick, long worldGameTick, long observedServerTick,
        String diagnosticCode, String port, String reasonCode, boolean rollbackIncomplete
) {
    public static final int MAX_IDENTIFIER_LENGTH = 128;
    public static final int MAX_DIAGNOSTIC_BYTES = 64;
    public static final int MAX_PORT_BYTES = 64;
    public static final int MAX_REASON_BYTES = 96;
    public static final int MAX_PACKET_BYTES = 1024;

    public ProgramRunStatus {
        Objects.requireNonNull(token); Objects.requireNonNull(target);
        Objects.requireNonNull(phase); Objects.requireNonNull(waitKind);
        if (runSequence < 0 || updateSequence < 0 || nodeId < -1 || sourceNodeId < -1
                || logicalTick < -1 || wakeAtLogicalTick < -1 || worldGameTick < -1 || observedServerTick < 0) {
            throw new IllegalArgumentException("Invalid run observation bounds");
        }
        if (nodeType != null && nodeType.toString().length() > MAX_IDENTIFIER_LENGTH) nodeType = null;
        diagnosticCode = bounded(diagnosticCode, MAX_DIAGNOSTIC_BYTES);
        port = bounded(port, MAX_PORT_BYTES); reasonCode = bounded(reasonCode, MAX_REASON_BYTES);
    }

    public boolean terminal() {
        return phase == Phase.COMPLETED || phase == Phase.FAILED || phase == Phase.CANCELLED || phase == Phase.EXPIRED;
    }

    public enum Phase { IDLE, PREPARING, WAITING, RUNNING, COMPLETED, FAILED, CANCELLED, EXPIRED }
    public enum WaitKind { NONE, WAIT, RETRY, FUEL }

    public record Target(Identifier dimension, Identifier category, int slot, UUID programId, long bookRevision) {
        public Target {
            Objects.requireNonNull(dimension); Objects.requireNonNull(category); Objects.requireNonNull(programId);
            if (dimension.toString().length() > MAX_IDENTIFIER_LENGTH || category.toString().length() > MAX_IDENTIFIER_LENGTH
                    || slot < 0 || slot >= 10 || bookRevision < 0) throw new IllegalArgumentException("Invalid observation target");
        }
    }

    private static String bounded(@Nullable String value, int bytes) {
        if (value == null || value.isEmpty()) return "";
        var result = new StringBuilder(Math.min(value.length(), bytes));
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset); offset += Character.charCount(cp);
            if (Character.isISOControl(cp)) continue;
            int width = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            if (used + width > bytes) break;
            result.appendCodePoint(cp); used += width;
        }
        return result.toString();
    }
}
