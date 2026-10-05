#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in mat4 InstanceMat;

layout(location = 0) out vec2 texCoord0;
layout(location = 33) out vec3 viewPosition;

void main() {
    vec4 view = View * InstanceMat * vec4(Position, 1.0);
    gl_Position = Projection * view;
    texCoord0 = UV0;
    viewPosition = view.xyz;
}
