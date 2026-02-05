#version 150

uniform sampler2D Sampler0;

layout (std140) uniform MsdfUniforms {
    float Range;
    float Thickness;
    float OutlineThickness;
    vec4 OutlineColor;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 OutColor;

float median(vec3 rgb) {
    return max(min(rgb.r, rgb.g), min(max(rgb.r, rgb.g), rgb.b));
}

void main() {
    vec3 msdfSample = texture(Sampler0, texCoord0).rgb;
    float sd = median(msdfSample) - 0.5 + Thickness;

    vec2 dx = dFdx(texCoord0) * textureSize(Sampler0, 0);
    vec2 dy = dFdy(texCoord0) * textureSize(Sampler0, 0);
    float screenParam = Range * inversesqrt(dot(dx, dx) + dot(dy, dy));

    float opacity = clamp(sd * screenParam + 0.5, 0.0, 1.0);
    opacity *= smoothstep(-0.5, -0.05, sd * Range);

    vec4 baseColor = vec4(vertexColor.rgb, vertexColor.a * opacity);

    if (OutlineThickness > 0.0) {
        float outlineSd = sd + OutlineThickness;
        float outlineOpacity = clamp(outlineSd * screenParam + 0.5, 0.0, 1.0);
        outlineOpacity *= smoothstep(-0.5, -0.05, outlineSd * Range);
        vec4 strokeColor = vec4(OutlineColor.rgb, OutlineColor.a * outlineOpacity);
        OutColor = mix(strokeColor, baseColor, opacity);
    } else {
        OutColor = baseColor;
    }
}