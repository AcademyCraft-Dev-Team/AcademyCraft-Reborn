#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float mistSeed;
in float mistAge;
in float flowStretch;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float coarse = texture(Sampler0,
            texCoord * vec2(1.35, 0.82) + vec2(mistSeed * 1.7 - mistAge * 0.06, mistSeed)).r;
    float detail = texture(Sampler0,
            texCoord * vec2(4.2, 2.7) + vec2(mistAge * 0.11, mistSeed * 2.3)).r;
    float breakup = coarse * 0.68 + detail * 0.32;

    // 雾气只是气流的材质：粒子轮廓沿速度方向拉成长丝，而不是压成贴地雾片。
    float ribbonDistance = length(vec2(centered.x * 1.12, centered.y * 0.72));
    float edge = 0.76 + (breakup - 0.5) * 0.22;
    float body = 1.0 - smoothstep(edge, 1.04, ribbonDistance);
    float filament = 0.58 + 0.42 * smoothstep(0.22, 0.72, breakup);
    float axialFade = 1.0 - smoothstep(0.68, 1.02, abs(centered.y));
    float density = max(body * filament * axialFade, 0.0);

    float alpha = vertexColor.a * max(density, 0.0);
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGradient = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGradient, depthDiff);
    if (alpha < 0.001) discard;

    float pressureCore = (1.0 - smoothstep(0.0, 0.62, abs(centered.x)))
            * clamp((flowStretch - 1.0) * 0.18, 0.0, 0.32);
    vec3 whiteMist = mix(vec3(0.88, 0.94, 0.98), vec3(1.0),
            clamp(coarse + pressureCore, 0.0, 1.0));
    fragColor = vec4(whiteMist * vertexColor.rgb, alpha);
}
