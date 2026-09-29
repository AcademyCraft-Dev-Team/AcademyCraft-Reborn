#version 330
in vec2 markerUv;
in vec4 markerColor;
out vec4 fragColor;
void main() {
    float edge = abs(markerUv.x) + abs(markerUv.y) - 0.78;
    float aa = max(fwidth(edge), 0.001);
    float outline = 1.0 - smoothstep(0.6 * aa, 1.6 * aa, abs(edge));
    fragColor = vec4(markerColor.rgb, markerColor.a * outline);
}
