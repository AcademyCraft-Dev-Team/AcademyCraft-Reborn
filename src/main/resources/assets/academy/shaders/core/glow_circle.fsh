#version 330

in vec2 texCoord0;

layout (std140) uniform Uniforms {
    vec2 RingCenter;
    float InnerRadius;
    float OuterRadius;
    float InnerGlowRadius;
    float OuterGlowRadius;
    float Progress;
    vec4 RingColor;
    float StartAngle;
};

out vec4 fragColor;

const float PI = 3.14159265359;
const float TWO_PI = 2.0 * PI;

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, 0.0);

    vec2 diff = texCoord0 - RingCenter;

    float dist = length(diff);

    float angle = atan(diff.y, diff.x);
    if (angle < 0.0) {
        angle += TWO_PI;
    }

    float targetAngle = StartAngle + Progress * TWO_PI;

    bool angleInRange = false;
    if (Progress >= 1.0) {
        angleInRange = true;
    } else if (Progress > 0.001) {
        float normalizedStart = mod(StartAngle, TWO_PI);
        float normalizedTarget = mod(targetAngle, TWO_PI);

        if (normalizedTarget >= normalizedStart) {
            angleInRange = (angle >= normalizedStart && angle < normalizedTarget);
        } else {
            angleInRange = (angle >= normalizedStart || angle < normalizedTarget);
        }
    }

    if (angleInRange) {
        if (dist >= InnerRadius && dist < OuterRadius) {
            fragColor = RingColor;

        } else if (dist >= OuterRadius && dist < OuterGlowRadius) {
            float outerGlowFactor = 1.0 - smoothstep(OuterRadius, OuterGlowRadius, dist);
            fragColor = vec4(RingColor.rgb, RingColor.a * outerGlowFactor);

        } else if (dist >= InnerGlowRadius && dist < InnerRadius) {
            float innerGlowFactor = smoothstep(InnerGlowRadius, InnerRadius, dist);
            fragColor = vec4(RingColor.rgb, RingColor.a * innerGlowFactor);
        }
    }
}