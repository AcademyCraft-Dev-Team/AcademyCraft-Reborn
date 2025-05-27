#version 150

in vec2 texCoord0;

uniform vec2 ringCenter;
uniform float innerRadius;
uniform float outerRadius;
uniform float innerGlowRadius;
uniform float outerGlowRadius;
uniform float progress;
uniform vec4 ringColor;
uniform float startAngle;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TWO_PI = 2.0 * PI;

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, 0.0);

    vec2 diff = texCoord0 - ringCenter;

    float dist = length(diff);

    float angle = atan(diff.y, diff.x);
    if (angle < 0.0) {
        angle += TWO_PI;
    }

    float targetAngle = startAngle + progress * TWO_PI;

    bool angleInRange = false;
    if (progress >= 1.0) {
        angleInRange = true;
    } else if (progress > 0.001) {
        float normalizedStart = mod(startAngle, TWO_PI);
        float normalizedTarget = mod(targetAngle, TWO_PI);

        if (normalizedTarget >= normalizedStart) {
            angleInRange = (angle >= normalizedStart && angle < normalizedTarget);
        } else {
            angleInRange = (angle >= normalizedStart || angle < normalizedTarget);
        }
    }

    if (angleInRange) {
        if (dist >= innerRadius && dist < outerRadius) {
            fragColor = ringColor;

        } else if (dist >= outerRadius && dist < outerGlowRadius) {
            float outerGlowFactor = 1.0 - smoothstep(outerRadius, outerGlowRadius, dist);
            fragColor = vec4(ringColor.rgb, ringColor.a * outerGlowFactor);

        } else if (dist >= innerGlowRadius && dist < innerRadius) {
            float innerGlowFactor = smoothstep(innerGlowRadius, innerRadius, dist);
            fragColor = vec4(ringColor.rgb, ringColor.a * innerGlowFactor);
        }
    }
}