#version 330

#moj_import <minecraft:dynamictransforms.glsl>

layout(std140) uniform LightningUniforms {
    vec4 LightningBaseColor;
    vec4 LightningEmissionColor;
    vec4 LightningParams;
    vec4 LightningCameraOffset;
};

in vec2 texCoord0;

out vec4 fragColor;

vec3 acesApprox(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    float intensity = texCoord0.x;
    vec3 color = LightningBaseColor.rgb + LightningEmissionColor.rgb * intensity;
    if (LightningParams.x > 0.5) {
        color = acesApprox(color);
    }
    fragColor = vec4(color, LightningParams.y);
}
