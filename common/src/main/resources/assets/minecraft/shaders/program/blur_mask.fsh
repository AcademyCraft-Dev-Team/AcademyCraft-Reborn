#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 BlurDir;
uniform float Radius;

out vec4 fragColor;

void main() {
    float mask = texture(MaskSampler, texCoord).a;
    if (mask == 0.0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    vec4 sum = vec4(0.0);
    float totalWeight = 0.0;
    float invRadius = 1.0 / Radius;

    for (float i = -Radius; i <= Radius; i += 1.0) {
        vec2 uv = texCoord + BlurDir * oneTexel * i;
        float sampleMask = texture(MaskSampler, uv).a;
        vec4 color = sampleMask > 0.0 ? texture(DiffuseSampler, uv) : texture(DiffuseSampler, texCoord);

        float x = 1.0 - abs(i) * invRadius;
        float sx = x * 2.0 - 1.0;
        float weight = -sx * abs(sx) * 0.5 + sx + 0.5;

        sum += color * weight;
        totalWeight += weight;
    }

    fragColor = totalWeight > 0.0 ? vec4(sum.rgb / totalWeight, 1.0) : texture(DiffuseSampler, texCoord);
}