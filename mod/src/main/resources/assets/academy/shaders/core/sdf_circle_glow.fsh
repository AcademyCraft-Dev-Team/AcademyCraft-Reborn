#version 330
#extension GL_ARB_separate_shader_objects : require

layout (std140) uniform GlowUniforms {
    vec4 Color;
    float Radius;
    float Softness;
};

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 centered_uv = texCoord0 * 2.0 - 1.0;
    float dist = length(centered_uv);
    float alpha = 1.0 - clamp((dist - Radius) / Softness, 0.0, 1.0);
    fragColor = vec4(Color.rgb, Color.a * alpha);
}