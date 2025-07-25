#version 150

in vec2 texCoord0;

uniform vec4 Color;
uniform float Radius;
uniform float Softness;

out vec4 fragColor;

void main() {
    vec2 centered_uv = texCoord0 * 2.0 - 1.0;
    float dist = length(centered_uv);
    float alpha = 1.0 - clamp((dist - Radius) / Softness, 0.0, 1.0);
    fragColor = vec4(Color.rgb, Color.a * alpha);
}