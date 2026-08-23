#version 330

layout(std140) uniform GraphUniforms {
    float Time;
};

uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 v_uv_out = texCoord;
    vec4 v_tex_rgba = texture(Sampler0, v_uv_out * vec2(1.0, 1.0) + vec2(0.0, 0.0));
    fragColor = vec4(v_tex_rgba);
}
