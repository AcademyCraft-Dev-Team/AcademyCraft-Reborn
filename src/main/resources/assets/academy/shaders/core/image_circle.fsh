#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    float dist = distance(texCoord0, vec2(0.5));
    float alpha = smoothstep(0.5, 0.495, dist);
    fragColor = vec4(color.rgb, color.a * alpha);
}