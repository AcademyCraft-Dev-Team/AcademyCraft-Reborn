#version 330

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;

layout (std140) uniform BlurInfo {
    vec2 outSize;
    vec2 blurDir;
    float radius;
};

out vec4 fragColor;

void main() {
    if (radius < 0.1) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    float maskAlpha = texture(MaskSampler, texCoord).a;
    if (maskAlpha == 0.0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    int r = int(radius);
    float base = -2.0 / (radius * radius);
    float factor = 0.79788456 / radius;

    vec2 pixelSize = 1.0 / outSize;
    vec2 dir = blurDir * pixelSize;

    vec3 blur = vec3(0.0);
    float wsum = 0.0;

    for (int i = -r; i <= r; i++) {
        float w = exp(float(i * i) * base) * factor;
        vec2 offset = dir * float(i);

        vec3 sampleColor = (texture(MaskSampler, texCoord + offset).a > 0.0)
        ? texture(DiffuseSampler, texCoord + offset).rgb
        : texture(DiffuseSampler, texCoord).rgb;

        blur += sampleColor * w;
        wsum += w;
    }

    fragColor = vec4(blur / wsum, 1.0);
}
