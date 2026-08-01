#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    float c = (color.r + color.g + color.b) / 3.0;
    fragColor = vec4(c, c, c, color.a);
}
