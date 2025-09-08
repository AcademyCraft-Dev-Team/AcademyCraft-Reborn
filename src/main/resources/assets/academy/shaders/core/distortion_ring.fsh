#version 150

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec3 distortionParams;

out vec4 fragColor;

void main() {
    float distortionStrength = distortionParams.x;
    float ringWidth = distortionParams.y;
    float ringEdgeBlur = distortionParams.z;

    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
    vec2 centeredUV = texCoord0 * 2.0 - 1.0;
    float dist = length(centeredUV);

    float ring_start = 1.0 - ringWidth;
    float ring_end = 1.0;

    if (dist < ring_start || dist > ring_end) {
        discard;
    }

    float progress_in_ring = (dist - ring_start) / ringWidth;
    float intensity = sin(progress_in_ring * 3.14159265);

    float edgeFactor = 1.0;
    if (progress_in_ring < ringEdgeBlur) {
        edgeFactor = progress_in_ring / ringEdgeBlur;
    } else if (progress_in_ring > 1.0 - ringEdgeBlur) {
        edgeFactor = (1.0 - progress_in_ring) / ringEdgeBlur;
    }

    vec2 direction = normalize(centeredUV);
    vec2 offset = direction * intensity * distortionStrength * edgeFactor;
    vec2 distortedUV = screenUV + offset;

    fragColor = texture(Sampler0, distortedUV);
}
