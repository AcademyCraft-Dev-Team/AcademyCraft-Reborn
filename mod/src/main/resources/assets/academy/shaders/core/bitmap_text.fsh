#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 9) in float fadeAlpha;

layout(location = 0) out vec4 OutColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).r;
    OutColor = vec4(vertexColor.rgb, vertexColor.a * coverage * fadeAlpha);
}
