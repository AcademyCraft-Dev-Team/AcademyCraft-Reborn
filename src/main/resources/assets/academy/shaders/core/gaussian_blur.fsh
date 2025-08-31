// Adapted from the Photon project (commit 08db839ccb01a741b21c70741c1015719992602a)
// https://github.com/Low-Drag-MC/Photon/commit/08db839ccb01a741b21c70741c1015719992602a
// Licensed under GNU GPLv3, copyright Low-Drag-MC and contributors.
// Modified for this project.

#version 150

uniform sampler2D DiffuseSampler;

layout(std140) uniform BlurInfo {
    vec2 OutSize;
    vec2 BlurDir;
    int Radius;
};

in vec2 texCoord;
out vec4 fragColor;

float gaussianWeight(in float x, in float sigma) {
    return 0.39894 * exp(-0.5 * x * x / (sigma * sigma)) / sigma;
}

void main() {
    vec2 pixelSize = 1.0 / OutSize;
    float sigma = float(Radius);
    float totalWeight = gaussianWeight(0.0, sigma);
    vec3 accumulatedColor = texture(DiffuseSampler, texCoord).rgb * totalWeight;

    for (int offset = 1; offset < Radius; offset++) {
        float distance = float(offset);
        float weight = gaussianWeight(distance, sigma);
        vec2 offsetCoord = BlurDir * pixelSize * distance;
        vec3 samplePos = texture(DiffuseSampler, texCoord + offsetCoord).rgb;
        vec3 sampleNeg = texture(DiffuseSampler, texCoord - offsetCoord).rgb;
        accumulatedColor += (samplePos + sampleNeg) * weight;
        totalWeight += 2.0 * weight;
    }

    fragColor = vec4(accumulatedColor / totalWeight, 1.0);
}