#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Corner0Pos;
layout(location = 2) in vec2 Corner0Uv;
layout(location = 3) in vec4 Corner0Color;
layout(location = 4) in vec3 Corner1Pos;
layout(location = 5) in vec2 Corner1Uv;
layout(location = 6) in vec4 Corner1Color;
layout(location = 7) in vec3 Corner2Pos;
layout(location = 8) in vec2 Corner2Uv;
layout(location = 9) in vec4 Corner2Color;
layout(location = 10) in vec3 Corner3Pos;
layout(location = 11) in vec2 Corner3Uv;
layout(location = 12) in vec4 Corner3Color;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;

void main() {
    int c = gl_VertexIndex;
    vec3 p = c == 0 ? Corner0Pos : (c == 1 ? Corner1Pos : (c == 2 ? Corner2Pos : Corner3Pos));
    vec2 uv = c == 0 ? Corner0Uv : (c == 1 ? Corner1Uv : (c == 2 ? Corner2Uv : Corner3Uv));
    vec4 col = c == 0 ? Corner0Color : (c == 1 ? Corner1Color : (c == 2 ? Corner2Color : Corner3Color));
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    texCoord0 = uv;
    vertexColor = col;
}
