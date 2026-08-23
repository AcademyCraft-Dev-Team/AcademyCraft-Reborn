#version 330

layout(std140) uniform GraphUniforms {
    float Time;
};

in vec2 texCoord;
out vec4 fragColor;

float _academy_curve_life(float t) {
    if (t <= 0.0) return 0.0;
    if (t < 1.0) return mix(0.0, 1.0, smoothstep(0.0, 1.0, (t - 0.0) / (1.0)));
    return 1.0;
}

vec4 _academy_gradient_col(float t) {
    if (t <= 0.0) return vec4(1.0, 0.0, 0.0, 1.0);
    if (t < 1.0) return mix(vec4(1.0, 0.0, 0.0, 1.0), vec4(0.0, 0.0, 1.0, 1.0), clamp((t - 0.0) / (1.0), 0.0, 1.0));
    return vec4(0.0, 0.0, 1.0, 1.0);
}

void main() {
    vec2 v_uv_out = texCoord;
    float v_cs_out = _academy_curve_life(v_uv_out);
    vec4 v_gs_out = _academy_gradient_col(v_uv_out);
    fragColor = vec4(v_gs_out);
}
