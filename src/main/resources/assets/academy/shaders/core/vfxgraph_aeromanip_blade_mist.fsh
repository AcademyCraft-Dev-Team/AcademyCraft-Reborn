#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float mistSeed;
in float mistAge;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float coarse = texture(Sampler0,
            texCoord * vec2(5.2, 2.7) + vec2(mistSeed * 3.7 - mistAge * 0.11, mistSeed)).r;
    float detail = texture(Sampler0,
            texCoord * vec2(17.0, 8.0) + vec2(mistAge * 0.23, mistSeed * 6.1)).r;

    float envelope = 1.0 - smoothstep(0.42, 1.0,
            length(vec2(centered.x * 1.42, centered.y * 0.78)));
    float endFade = 1.0 - smoothstep(0.6, 1.0, abs(centered.y));
    float threadWave = 0.5 + 0.5 * sin(
            centered.y * 18.0 + mistSeed * 24.0 + (coarse - 0.5) * 5.0);
    float filament = smoothstep(0.34, 0.72,
            detail * 0.58 + coarse * 0.27 + threadWave * 0.28);
    float density = envelope * endFade * filament;
    float alpha = vertexColor.a * density;

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGradient = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGradient, depthDiff);
    if (alpha < 0.001) discard;

    vec3 gasGray = mix(vec3(0.61, 0.63, 0.64), vec3(0.82, 0.83, 0.83), detail);
    fragColor = vec4(gasGray * vertexColor.rgb, alpha);
}
