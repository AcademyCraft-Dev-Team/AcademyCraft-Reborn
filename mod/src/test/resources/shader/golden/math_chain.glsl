#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphUniforms {
    float Time;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float v_c1_out = 2.0;
    float v_c2_out = 3.0;
    float v_sum_out = (v_c1_out + v_c2_out);
    float v_prod_out = (v_sum_out * v_c2_out);
    fragColor = vec4(v_prod_out);
}
