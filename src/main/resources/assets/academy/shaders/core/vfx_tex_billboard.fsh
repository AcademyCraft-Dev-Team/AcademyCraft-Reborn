#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in float vertexAlpha;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vec4(1.0, 1.0, 1.0, vertexAlpha) * ColorModulator;
    fragColor = color;
}
