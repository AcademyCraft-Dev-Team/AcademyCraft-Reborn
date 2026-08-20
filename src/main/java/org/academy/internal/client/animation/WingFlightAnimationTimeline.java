package org.academy.internal.client.animation;

import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;

/** Deterministic transition timeline for the five authored wing-flight clips. */
public final class WingFlightAnimationTimeline {
    private Phase phase = Phase.IDLE;
    private WingFlightPose.Pose target = WingFlightPose.Pose.IDLE;
    private float phaseStartTick;
    private float lastTick = Float.NEGATIVE_INFINITY;

    public Playback update(WingFlightPose.Pose requested, float nowTick) {
        if (requested == null || !Float.isFinite(nowTick)) {
            throw new IllegalArgumentException("Wing-flight timeline input is invalid");
        }
        if (nowTick < lastTick) reset(nowTick);
        lastTick = nowTick;
        if (requested != target) transitionTo(requested, nowTick);
        advance(nowTick);
        return new Playback(phase, Math.max(0.0f, (nowTick - phaseStartTick) / 20.0f));
    }

    private void transitionTo(WingFlightPose.Pose requested, float nowTick) {
        target = requested;
        switch (requested) {
            case IDLE -> {
                if (phase == Phase.FLYING_FAST || phase == Phase.START_FLYING_FAST) {
                    start(Phase.QUIT_FLYING_FAST, nowTick);
                } else if (phase == Phase.FLYING_SLOW) {
                    start(Phase.STOP_FLYING_SLOW, nowTick);
                } else if (phase == Phase.START_FLYING_SLOW) {
                    switchSlowDirection(Phase.STOP_FLYING_SLOW, nowTick);
                } else {
                    start(Phase.IDLE, nowTick);
                }
            }
            case SLOW -> {
                if (phase == Phase.FLYING_FAST || phase == Phase.START_FLYING_FAST) {
                    start(Phase.QUIT_FLYING_FAST, nowTick);
                } else if (phase == Phase.IDLE) {
                    start(Phase.START_FLYING_SLOW, nowTick);
                } else if (phase == Phase.STOP_FLYING_SLOW) {
                    switchSlowDirection(Phase.START_FLYING_SLOW, nowTick);
                }
            }
            case FAST -> {
                if (phase == Phase.IDLE) {
                    // startFlyingFast is authored from the low-speed pose, so enter that pose first.
                    start(Phase.START_FLYING_SLOW, nowTick);
                } else if (phase == Phase.STOP_FLYING_SLOW) {
                    switchSlowDirection(Phase.START_FLYING_SLOW, nowTick);
                } else if (phase == Phase.FLYING_SLOW || phase == Phase.QUIT_FLYING_FAST) {
                    start(Phase.START_FLYING_FAST, nowTick);
                }
                // Do not interrupt startFlyingSlow; startFlyingFast follows when it completes.
            }
        }
    }

    private void advance(float nowTick) {
        while (phase.durationTicks > 0.0f
                && nowTick - phaseStartTick >= phase.durationTicks) {
            phaseStartTick += phase.durationTicks;
            phase = switch (phase) {
                case START_FLYING_SLOW -> switch (target) {
                    case IDLE -> Phase.IDLE;
                    case SLOW -> Phase.FLYING_SLOW;
                    case FAST -> Phase.START_FLYING_FAST;
                };
                case START_FLYING_FAST -> target == WingFlightPose.Pose.FAST
                        ? Phase.FLYING_FAST : Phase.QUIT_FLYING_FAST;
                case QUIT_FLYING_FAST -> switch (target) {
                    case IDLE -> Phase.STOP_FLYING_SLOW;
                    case SLOW -> Phase.FLYING_SLOW;
                    case FAST -> Phase.START_FLYING_FAST;
                };
                case STOP_FLYING_SLOW -> switch (target) {
                    case IDLE -> Phase.IDLE;
                    case SLOW -> Phase.FLYING_SLOW;
                    case FAST -> Phase.START_FLYING_FAST;
                };
                default -> phase;
            };
        }
    }

    private void reset(float nowTick) {
        phase = Phase.IDLE;
        target = WingFlightPose.Pose.IDLE;
        phaseStartTick = nowTick;
    }

    private void start(Phase next, float nowTick) {
        phase = next;
        phaseStartTick = nowTick;
    }

    private void switchSlowDirection(Phase next, float nowTick) {
        var duration = Phase.START_FLYING_SLOW.durationTicks;
        var elapsed = Math.max(0.0f, Math.min(duration, nowTick - phaseStartTick));
        var clipTick = phase == Phase.START_FLYING_SLOW ? elapsed : duration - elapsed;
        phase = next;
        phaseStartTick = nowTick - (next == Phase.START_FLYING_SLOW
                ? clipTick : duration - clipTick);
    }

    public record Playback(Phase phase, float elapsedSeconds) {
        public float clipTimeSeconds() {
            if (phase != Phase.STOP_FLYING_SLOW) return elapsedSeconds;
            return Math.max(0.0f, phase.durationTicks / 20.0f - elapsedSeconds);
        }
    }

    public enum Phase {
        IDLE(0.0f),
        START_FLYING_SLOW(10.0f),
        FLYING_SLOW(0.0f),
        START_FLYING_FAST(20.0f),
        FLYING_FAST(0.0f),
        QUIT_FLYING_FAST(10.0f),
        STOP_FLYING_SLOW(10.0f);

        private final float durationTicks;

        Phase(float durationTicks) {
            this.durationTicks = durationTicks;
        }
    }
}
