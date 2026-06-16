#version 330

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float distFromCenter = abs(texCoord0.y - 0.5) * 2.0;
    float alpha = 1.0 - smoothstep(0.0, 1.0, distFromCenter);
    alpha *= vertexColor.a;
    if (alpha < 0.001) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
