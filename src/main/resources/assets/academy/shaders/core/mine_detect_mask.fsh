#version 330
in vec4 oreColor;
flat in float inRange;
out vec4 fragColor;

void main() {
    if (inRange < 0.5) discard;
    fragColor = oreColor;
}
