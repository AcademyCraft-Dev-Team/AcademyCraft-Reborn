#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 InstPos;
layout(location = 2) in vec2 InstSize;
layout(location = 3) in vec2 InstUVStart;
layout(location = 4) in vec2 InstUVEnd;
layout(location = 5) in vec4 InstColor;
layout(location = 6) in vec2 InstFade;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;
layout(location = 9) out float fadeAlpha;

void main() {
    vec2 realLocalPos = InstPos.xy + (Position.xy * InstSize);

    gl_Position = ProjMat * ModelViewMat * vec4(realLocalPos, InstPos.z, 1.0);

    texCoord0 = mix(InstUVStart, InstUVEnd, Position.xy);

    vertexColor = InstColor;
    fadeAlpha = mix(InstFade.x, InstFade.y, Position.x);
}
