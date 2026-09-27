#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in mat4 InstanceMat;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;

void main() {
    vec4 local = InstanceMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * local;
    texCoord0 = UV0;
    vertexColor = vec4(1.0);
}
