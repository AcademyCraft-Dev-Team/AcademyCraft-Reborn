#version 330

in vec4 vertexColor;
in vec2 texCoord0;

uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    fragColor = vec4(vertexColor.rgb * texColor.rgb, vertexColor.a * texColor.a);
}
