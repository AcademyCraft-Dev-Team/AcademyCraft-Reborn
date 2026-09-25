#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(std140) uniform LightningUniforms {
    vec4 LightningBaseColor;
    vec4 LightningEmissionColor;
    vec4 LightningParams;
    vec4 LightningCameraOffset;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 texCoord0;

void main() {
    vec3 camRel = Position - LightningCameraOffset.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(camRel, 1.0);
    texCoord0 = UV0;
}
