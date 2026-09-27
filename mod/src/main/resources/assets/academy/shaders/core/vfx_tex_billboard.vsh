#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 InstancePos;
layout(location = 3) in float InstanceSize;
layout(location = 4) in float InstanceAlpha;
layout(location = 5) in vec4 InstanceUVRect;

layout(location = 0) out vec2 texCoord0;
layout(location = 32) out float vertexAlpha;

void main() {
    vec4 viewPos = ModelViewMat * vec4(InstancePos, 1.0);
    vec2 offset = (Position.xy * 2.0 - 1.0) * InstanceSize;
    viewPos.xy += offset;
    gl_Position = ProjMat * viewPos;
    texCoord0 = vec2(
        InstanceUVRect.x + UV0.x * (InstanceUVRect.z - InstanceUVRect.x),
        InstanceUVRect.y + UV0.y * (InstanceUVRect.w - InstanceUVRect.y)
    );
    vertexAlpha = InstanceAlpha;
}
