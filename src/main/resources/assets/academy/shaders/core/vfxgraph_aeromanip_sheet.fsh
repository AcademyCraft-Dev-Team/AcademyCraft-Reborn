#version 330

uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
in float sheetAge;
out vec4 fragColor;

void main() {
    float across = texCoord.x * 2.0 - 1.0;
    float along = texCoord.y;
    float endFade = smoothstep(0.0, 0.025, along) * (1.0 - smoothstep(0.78, 1.0, along));
    float taperedWidth = mix(0.75, 1.0, sin(along * 3.14159265));
    float edge = 1.0 - smoothstep(taperedWidth * 0.8, taperedWidth, abs(across));
    // A filled sheet remains connected; moving striations and soft grains provide its airflow detail.
    float noise = texture(Sampler0, vec2(along * 6.0 - sheetAge * 2.4, texCoord.x * 9.0)).r;
    float flow = 0.5 + 0.5 * sin(across * 76.0 + noise * 2.2 + along * 7.0 - sheetAge * 22.0);
    float core = pow(flow, 8.0);
    float density = (0.45 + core * 0.4 + noise * 0.15) * edge * endFade;
    float alpha = vertexColor.a * density;
    if (alpha < 0.001) discard;
    fragColor = vec4(vertexColor.rgb * mix(0.96, 1.0, core), alpha);
}

