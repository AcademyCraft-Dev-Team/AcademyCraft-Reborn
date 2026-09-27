#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 InstanceColor;

layout(location = 1) out vec4 vertexColor;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
    vertexColor = InstanceColor;
}
