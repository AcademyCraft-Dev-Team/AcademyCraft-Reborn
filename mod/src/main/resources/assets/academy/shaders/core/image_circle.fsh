#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    float dist = distance(texCoord0, vec2(0.5));
    float alpha = smoothstep(0.5, 0.495, dist);
    fragColor = vec4(color.rgb, color.a * alpha);
}