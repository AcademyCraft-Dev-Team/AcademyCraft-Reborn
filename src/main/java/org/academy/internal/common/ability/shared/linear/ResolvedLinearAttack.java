package org.academy.internal.common.ability.shared.linear;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ResolvedLinearAttack {
    private final LinearSegment original;
    private final LinearSegment outbound;
    @Nullable
    private final LinearSegment returnSegment;
    @Nullable
    private final LinearReflectionCandidate reflectionCandidate;

    private ResolvedLinearAttack(
            LinearSegment original,
            LinearSegment outbound,
            @Nullable LinearSegment returnSegment,
            @Nullable LinearReflectionCandidate reflectionCandidate
    ) {
        this.original = Objects.requireNonNull(original, "original");
        this.outbound = Objects.requireNonNull(outbound, "outbound");
        this.returnSegment = returnSegment;
        this.reflectionCandidate = reflectionCandidate;
    }

    public static ResolvedLinearAttack unreflected(LinearSegment original) {
        return new ResolvedLinearAttack(original, original, null, null);
    }

    public static ResolvedLinearAttack reflected(
            LinearSegment original,
            LinearSegment outbound,
            LinearSegment returnSegment,
            LinearReflectionCandidate candidate
    ) {
        return new ResolvedLinearAttack(original, outbound, returnSegment, candidate);
    }

    static LinearSegment limitReturnSegment(LinearSegment returnSegment, double maximumLength) {
        return returnSegment.limitedTo(maximumLength);
    }

    static double calculateReturnVisualLength(Vec3 mirrorPoint, @Nullable LinearSegment returnSegment) {
        return returnSegment == null ? 0.0 : mirrorPoint.distanceTo(returnSegment.end());
    }

    public LinearSegment original() {
        return original;
    }

    public LinearSegment outbound() {
        return outbound;
    }

    public Optional<LinearSegment> returnSegment() {
        return Optional.ofNullable(returnSegment);
    }

    public Optional<LinearSegment> redirectedSegment() {
        return returnSegment();
    }

    public Optional<LinearReflectionCandidate> reflectionCandidate() {
        return Optional.ofNullable(reflectionCandidate);
    }

    @Deprecated(forRemoval = false)
    public boolean isReflected() {
        return isRedirected();
    }

    public boolean isRedirected() {
        return returnSegment != null && reflectionCandidate != null;
    }

    public boolean isReflection() {
        return isRedirected()
                && reflectionCandidate.mode() == LinearReflectionCandidate.Mode.REFLECTION;
    }

    public boolean isRefracted() {
        return isRedirected()
                && reflectionCandidate.mode() != LinearReflectionCandidate.Mode.REFLECTION;
    }

    public boolean isRefraction() {
        return isRefracted();
    }

    public double reflectionProgress() {
        return reflectionCandidate == null ? 1.0 : reflectionCandidate.progress();
    }

    public Vec3 mirrorPoint() {
        return reflectionCandidate == null ? original.end() : reflectionCandidate.mirrorPoint();
    }

    public double returnVisualLength() {
        return calculateReturnVisualLength(mirrorPoint(), returnSegment);
    }

    /** An obstruction before the mirror cancels the unreachable redirected segment. */
    public ResolvedLinearAttack limitOutboundLength(double maximumLength) {
        // LinearSegment uses float-length math; a clear double-precision trace must not erase
        // a valid reflection solely because of the rounding difference at its endpoint.
        if (Double.isFinite(maximumLength) && maximumLength + 1.0e-6 * Math.max(1, outbound.length()) >= outbound.length()) return this;
        var limited = outbound.limitedTo(maximumLength);
        return limited == outbound ? this : unreflected(limited);
    }

    public ResolvedLinearAttack limitReturnLength(double maximumLength) {
        if (returnSegment == null) return this;
        var limited = limitReturnSegment(returnSegment, maximumLength);
        if (limited == returnSegment) return this;
        return new ResolvedLinearAttack(original, outbound, limited, reflectionCandidate);
    }

    public List<LinearSegment> segments() {
        return returnSegment == null ? List.of(outbound) : List.of(outbound, returnSegment);
    }
}
