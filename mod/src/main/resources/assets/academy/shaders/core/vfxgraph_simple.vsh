#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 1) out vec4 vertexColor;

void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    vertexColor = Color;
}
