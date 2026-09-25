#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 1) in vec4 vertexColor;
layout(location = 0) in vec2 texCoord0;

uniform sampler2D Sampler0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    fragColor = vec4(vertexColor.rgb * texColor.rgb, vertexColor.a * texColor.a);
}
