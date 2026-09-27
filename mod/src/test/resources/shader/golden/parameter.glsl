#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphUniforms {
    float Time;
    float u_speed;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float v_p_out = u_speed;
    float v_sat_out = clamp(v_p_out, 0.0, 1.0);
    fragColor = vec4(v_sat_out);
}
