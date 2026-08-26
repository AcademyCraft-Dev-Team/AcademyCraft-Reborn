#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float windTime;
in float windSeed;

out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    float angle = atan(p.y, p.x) * 0.15915494;

    // Two upward-panning noise fields plus a polar field form a soft rotating air sheet.
    vec2 panA = texCoord * vec2(1.35, 2.25)
            + vec2(windTime * 0.035 + windSeed, -windTime * 0.17);
    vec2 panB = texCoord.yx * vec2(2.8, 1.55)
            + vec2(-windTime * 0.075, windTime * 0.052 + windSeed * 1.7);
    vec2 spiralUv = vec2(angle + windTime * 0.085 + windSeed, radius * 1.7 - windTime * 0.12);
    float broad = texture(Sampler0, panA).r;
    float detail = texture(Sampler0, panB).r;
    float spiral = texture(Sampler0, spiralUv).r;
    float flowNoise = broad * 0.52 + detail * 0.22 + spiral * 0.26;

    // Hollow opacity gradient: the sprite centre is fully clear and its wind edge remains translucent.
    float centreClear = smoothstep(0.025, 0.19, radius);
    float outerSoft = 1.0 - smoothstep(0.62, 1.04, radius);
    float brokenSheet = smoothstep(0.22, 0.78, flowNoise + (1.0 - radius) * 0.18);
    float ribbon = centreClear * outerSoft * (0.42 + brokenSheet * 0.58);
    float edgeLight = exp(-pow((radius - 0.64) * 5.2, 2.0))
            * (0.46 + spiral * 0.54);
    float alpha = vertexColor.a * ribbon * (0.92 + edgeLight * 0.20);

    // A mid-grey self-shadow keeps the fog readable against a bright daytime sky.
    // Only the turbulent rim approaches white, preserving the pale translucent air look.
    vec3 baseGrey = clamp(vertexColor.rgb, vec3(0.48), vec3(0.80));
    vec3 shadowGrey = baseGrey * (0.62 + flowNoise * 0.20);
    vec3 softWhite = vec3(0.88, 0.90, 0.92);
    float illuminatedRim = edgeLight * (0.44 + detail * 0.18);
    vec3 color = mix(shadowGrey, softWhite, illuminatedRim);

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}
