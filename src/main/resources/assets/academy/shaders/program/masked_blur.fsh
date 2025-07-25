#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 BlurDir;
uniform float Radius;

out vec4 fragColor;

float gaussianWeight(float x, float sigma) {
    const float invSqrt2Pi = 0.39894;
    return invSqrt2Pi * exp(-0.5 * x * x / (sigma * sigma)) / sigma;
}

void main() {
    if (Radius < 0.1) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    float maskAlpha = texture(MaskSampler, texCoord).a;

    if (maskAlpha == 0.0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    vec2 pixelSize = oneTexel;
    float sigma = Radius;
    float totalWeight = gaussianWeight(0.0, sigma);
    vec3 accumulatedColor = texture(DiffuseSampler, texCoord).rgb * totalWeight;

    for (int offset = 1; offset <= int(Radius); ++offset) {
        float distance = float(offset);
        float weight = gaussianWeight(distance, sigma);
        vec2 offsetCoord = BlurDir * pixelSize * distance;

        vec3 samplePos = (texture(MaskSampler, texCoord + offsetCoord).a > 0.0)
        ? texture(DiffuseSampler, texCoord + offsetCoord).rgb
        : texture(DiffuseSampler, texCoord).rgb;

        vec3 sampleNeg = (texture(MaskSampler, texCoord - offsetCoord).a > 0.0)
        ? texture(DiffuseSampler, texCoord - offsetCoord).rgb
        : texture(DiffuseSampler, texCoord).rgb;

        accumulatedColor += (samplePos + sampleNeg) * weight;
        totalWeight += 2.0 * weight;
    }

    fragColor = vec4(accumulatedColor / totalWeight, 1.0);
}