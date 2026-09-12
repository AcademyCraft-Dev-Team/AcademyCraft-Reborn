#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in float fadeAlpha;

out vec4 OutColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).r;
    OutColor = vec4(vertexColor.rgb, vertexColor.a * coverage * fadeAlpha);
}
