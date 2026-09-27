#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in float Flow;
layout(location = 3) in vec2 MaterialUv;

layout(location = 1) out vec4 vertexColor;
layout(location = 10) out float flowCoord;
layout(location = 18) out vec2 materialUv;
layout(location = 25) out vec4 portalProjection;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    flowCoord = Flow;
    materialUv = MaterialUv;
    // Match the vanilla End Portal vertex path. The portal layers are projected
    // from clip space instead of stretched along the cut's long local UV axis.
    portalProjection = projection_from_position(gl_Position);
}
