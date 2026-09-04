#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float bladeSeed;
in float bladeAge;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float along = centered.y;
    float across = centered.x;

    float coarse = texture(Sampler0,
            vec2(texCoord.y * 3.8 + bladeSeed * 2.7 - bladeAge * 0.14,
                    texCoord.x * 8.5 + bladeSeed * 1.9)).r;
    float detail = texture(Sampler0,
            vec2(texCoord.y * 11.0 - bladeSeed * 4.2 + bladeAge * 0.26,
                    texCoord.x * 22.0 + bladeSeed * 5.1)).r;

    float endFade = 1.0 - smoothstep(0.56, 1.0, abs(along));
    float taperedWidth = mix(0.08, 0.52, pow(max(endFade, 0.0), 0.58));
    taperedWidth += (coarse - 0.5) * 0.16;
    float band = 1.0 - smoothstep(taperedWidth, taperedWidth + 0.17, abs(across));

    float maskedBody = smoothstep(0.27, 0.62,
            detail * 0.62 + coarse * 0.38 + endFade * 0.22);
    float fineStriation = 0.68 + 0.32 * sin(
            along * 31.0 + bladeSeed * 19.0 + (detail - 0.5) * 3.0);
    fineStriation = smoothstep(0.08, 0.92, fineStriation);
    float brokenBand = band * endFade * mix(0.34, 1.0, maskedBody) * fineStriation;

    float cuttingCore = exp(-across * across * 54.0)
            * (1.0 - smoothstep(0.72, 1.0, abs(along)));
    float density = max(brokenBand * 0.74, cuttingCore);
    float alpha = vertexColor.a * density;

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGradient = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGradient, depthDiff);
    if (alpha < 0.001) discard;

    vec3 paleGray = mix(vec3(0.7, 0.72, 0.73), vec3(0.93, 0.94, 0.94),
            clamp(cuttingCore * 0.72 + detail * 0.2, 0.0, 1.0));
    fragColor = vec4(paleGray * vertexColor.rgb, alpha);
}
