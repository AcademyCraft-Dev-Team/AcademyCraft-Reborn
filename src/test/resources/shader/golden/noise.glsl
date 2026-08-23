#version 330

layout(std140) uniform GraphUniforms {
    float Time;
};

in vec2 texCoord;
out vec4 fragColor;

float _academy_hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
vec2 _academy_perlin_grad(vec2 p) {
    float h = _academy_hash(p) * 6.28318530718;
    return vec2(cos(h), sin(h));
}
float _academy_perlin_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    vec2 g00 = _academy_perlin_grad(i);
    vec2 g10 = _academy_perlin_grad(i + vec2(1.0, 0.0));
    vec2 g01 = _academy_perlin_grad(i + vec2(0.0, 1.0));
    vec2 g11 = _academy_perlin_grad(i + vec2(1.0, 1.0));
    float v00 = dot(g00, f);
    float v10 = dot(g10, f - vec2(1.0, 0.0));
    float v01 = dot(g01, f - vec2(0.0, 1.0));
    float v11 = dot(g11, f - vec2(1.0, 1.0));
    return mix(mix(v00, v10, u.x), mix(v01, v11, u.x), u.y) * 0.5 + 0.5;
}

void main() {
    vec2 v_uv_out = texCoord;
    float v_n_out = _academy_perlin_noise(v_uv_out);
    fragColor = vec4(v_n_out);
}
