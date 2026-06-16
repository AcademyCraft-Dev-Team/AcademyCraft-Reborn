#version 330

in vec2 texCoord0;
in vec3 shockwaveParams;

layout (std140) uniform ShockwaveUniforms {
    float Time;
    float MaxRadius;
    float RingThickness;
    float EdgeSoftness;
    vec4 InnerColor;
    vec4 OuterColor;
};

out vec4 fragColor;

void main() {
    vec2 centeredUV = texCoord0 * 2.0 - 1.0;
    float dist = length(centeredUV);

    float ringCenter = Time * MaxRadius;
    float ringStart = ringCenter - RingThickness * 0.5;
    float ringEnd = ringCenter + RingThickness * 0.5;

    if (dist < ringStart || dist > MaxRadius) {
        discard;
    }

    float progressInRing = 0.0;
    float ringHalf = RingThickness * 0.5;
    if (dist >= ringStart && dist <= ringEnd) {
        progressInRing = (dist - ringStart) / RingThickness;
    } else if (dist > ringEnd) {
        discard;
    }

    float edgeFactor = 1.0;
    if (progressInRing < EdgeSoftness) {
        edgeFactor = progressInRing / EdgeSoftness;
    } else if (progressInRing > 1.0 - EdgeSoftness) {
        edgeFactor = (1.0 - progressInRing) / EdgeSoftness;
    }

    float ringIntensity = sin(progressInRing * 3.14159265);
    float fadeOut = 1.0 - smoothstep(0.6 * MaxRadius, MaxRadius, ringCenter);
    float alpha = ringIntensity * edgeFactor * fadeOut;

    fragColor = mix(OuterColor, InnerColor, edgeFactor) * alpha;
}
