#version 330

layout(std140) uniform GraphUniforms {
    float Time;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float v_c1_out = 2.0;
    float v_c2_out = 3.0;
    float v_sum_out = (v_c1_out + v_c2_out);
    float v_prod_out = (v_sum_out * v_c2_out);
    fragColor = vec4(v_prod_out);
}
