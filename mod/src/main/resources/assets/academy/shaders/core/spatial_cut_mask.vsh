#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 WorldDisplacement;
layout(location = 2) in float Coverage;

layout(location = 17) out vec3 maskWorldDisplacement;
layout(location = 16) noperspective out float maskCoverage;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Store a camera-independent world vector. Projection happens later for
    // each actual background pixel rather than once at the cut-plane depth.
    maskWorldDisplacement = WorldDisplacement;
    maskCoverage = Coverage;
}
