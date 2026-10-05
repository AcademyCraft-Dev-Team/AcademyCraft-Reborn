#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(std140) uniform DistortionRing {
    vec4 Params;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 distortionParams;

void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    texCoord0 = UV0;
    distortionParams = Params;
}
