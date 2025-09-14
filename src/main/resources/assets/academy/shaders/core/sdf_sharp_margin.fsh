#version 330

layout(std140) uniform SdfUniforms {
    vec2 Size;
    vec4 Margins;
    vec4 FillColor;
};

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    if (Size.x <= 0.0 || Size.y <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec4 feather_uv = vec4(
    Margins.x / Size.x,
    Margins.y / Size.y,
    Margins.z / Size.x,
    Margins.w / Size.y
    );

    float alphaL = smoothstep(0.0, feather_uv.x, texCoord0.x);
    float alphaT = smoothstep(0.0, feather_uv.y, texCoord0.y);
    float alphaR = smoothstep(1.0, 1.0 - feather_uv.z, texCoord0.x);
    float alphaB = smoothstep(1.0, 1.0 - feather_uv.w, texCoord0.y);

    float alpha = min(min(alphaL, alphaT), min(alphaR, alphaB));

    fragColor = vec4(FillColor.rgb, FillColor.a * alpha);
}