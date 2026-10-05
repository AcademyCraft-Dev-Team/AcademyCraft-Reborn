#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

layout(std140) uniform DistortionRing {
    vec4 Params;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 distortionParams;

layout(location = 0) out vec4 fragColor;

void main() {
    float strength = distortionParams.x;
    float ringWidth = distortionParams.y;
    float edgeBlur = distortionParams.z;
    float aspect = distortionParams.w;

    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
    vec2 centered = vec2(texCoord0.x * 2.0 - 1.0, (texCoord0.y * 2.0 - 1.0) / aspect);
    float dist = length(centered);

    float ringStart = max(0.0, 1.0 - ringWidth);
    if (dist < ringStart || dist > 1.0) discard;

    float progress = clamp((dist - ringStart) / max(ringWidth, 1e-4), 0.0, 1.0);
    float intensity = sin(progress * 3.14159265);

    float edgeFactor = 1.0;
    if (edgeBlur > 1e-4) {
        if (progress < edgeBlur) {
            edgeFactor = progress / edgeBlur;
        } else if (progress > 1.0 - edgeBlur) {
            edgeFactor = (1.0 - progress) / edgeBlur;
        }
    }

    vec2 direction = normalize(centered + vec2(1e-6));
    vec2 offset = direction * intensity * strength * edgeFactor;
    offset.x /= aspect;

    fragColor = texture(Sampler0, clamp(screenUV + offset, vec2(0.0), vec2(1.0)));
}
