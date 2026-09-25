#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphUniforms {
    float Time;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 v_fn_out = vec4(0.25);
    fragColor = vec4(v_fn_out);
}
