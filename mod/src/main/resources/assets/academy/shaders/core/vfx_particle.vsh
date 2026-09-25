#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 InstancePos;
layout(location = 2) in float InstanceSize;
layout(location = 3) in vec4 InstanceColor;

layout(location = 1) out vec4 vertexColor;

void main() {
    vec4 viewPos = ModelViewMat * vec4(InstancePos, 1.0);
    vec2 offset = (Position.xy * 2.0 - 1.0) * InstanceSize;
    viewPos.xy += offset;
    gl_Position = ProjMat * viewPos;
    vertexColor = InstanceColor;
}
