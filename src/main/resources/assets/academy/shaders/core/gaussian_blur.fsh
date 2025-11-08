// Adapted from the Photon project (commit 08db839ccb01a741b21c70741c1015719992602a)
// https://github.com/Low-Drag-MC/Photon/commit/08db839ccb01a741b21c70741c1015719992602a
// Licensed under GNU GPLv3, copyright Low-Drag-MC and contributors.
// Modified for this project.

#version 330

uniform sampler2D DiffuseSampler;

#define MAX_SAMPLES 12

layout(std140) uniform BlurInfo {
    vec2 OutSize;
    vec2 BlurDir;
    int SampleCount;
    vec4 Samples[MAX_SAMPLES];
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 pixelSize = 1.0 / OutSize;

    vec3 accumulatedColor = texture(DiffuseSampler, texCoord).rgb * Samples[0].z;

    for (int i = 1; i < SampleCount; i++) {
        float offset = Samples[i].x;
        float weight = Samples[i].z;

        vec2 offsetCoord = BlurDir * pixelSize * offset;
        vec3 samplePos = texture(DiffuseSampler, texCoord + offsetCoord).rgb;
        vec3 sampleNeg = texture(DiffuseSampler, texCoord - offsetCoord).rgb;

        accumulatedColor += (samplePos + sampleNeg) * weight;
    }

    fragColor = vec4(accumulatedColor, 1.0);
}