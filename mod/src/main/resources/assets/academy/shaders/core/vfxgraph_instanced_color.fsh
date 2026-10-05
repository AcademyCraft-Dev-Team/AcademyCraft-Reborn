#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor;
}
