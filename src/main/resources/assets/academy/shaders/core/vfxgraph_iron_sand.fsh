#version 330

uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 grain = texture(Sampler0, texCoord);
    float alpha = grain.a * vertexColor.a;
    if (alpha < 0.025) discard;
    fragColor = vec4(grain.rgb * vertexColor.rgb, alpha);
}
