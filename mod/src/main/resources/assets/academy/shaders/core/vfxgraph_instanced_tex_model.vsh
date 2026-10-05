#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(std140) uniform InstancedParams {
    vec4 InstancedScale;
    vec4 InstancedTint;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in mat4 InstanceMat;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;

void main() {
    vec4 local = InstanceMat * vec4(Position * InstancedScale.xyz, 1.0);
    gl_Position = Projection * View * local;
    texCoord0 = UV0;
    vertexColor = InstancedTint;
}
