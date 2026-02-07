#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 base = texture(Sampler0, texCoord0) * vertexColor;
    float a = base.a;
    float heat = smoothstep(0.05, 0.9, a);
    vec3 flareA = vec3(2.6, 1.7, 0.35);
    vec3 flareB = vec3(1.4, 0.7, 0.1);
    vec3 warm = mix(flareB, flareA, heat);
    float energy = mix(1.2, 6.0, heat);
    vec3 rgb = warm * energy * (0.35 + 0.65 * a);
    float alpha = clamp(a * (0.75 + 0.9 * heat), 0.0, 1.0);
    fragColor = vec4(rgb, alpha);
}
