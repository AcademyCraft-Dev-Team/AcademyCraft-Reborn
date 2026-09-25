#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 InstancePos;
layout(location = 2) in float InstanceSize;
layout(location = 3) in vec4 InstanceColor;
layout(location = 4) in float InstanceRot;

layout(location = 1) out vec4 vertexColor;

void main() {
    float c = cos(InstanceRot);
    float s = sin(InstanceRot);
    vec3 local = Position - 0.5;
    vec3 rot = vec3(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    vec3 world = InstancePos + rot * InstanceSize;
    gl_Position = Projection * View * vec4(world, 1.0);
    vertexColor = InstanceColor;
}
