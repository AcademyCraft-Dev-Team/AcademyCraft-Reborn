#version 330

layout(std140) uniform GraphUniforms {
    float Time;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 v_fn_out = vec4(0.25);
    fragColor = vec4(v_fn_out);
}
