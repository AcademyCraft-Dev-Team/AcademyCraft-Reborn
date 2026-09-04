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
    float contourNoise = texture(Sampler0,
            texCoord * vec2(9.0, 13.0) + vec2(mistSeed * 5.7, mistAge * 0.18)).r;
    float grainNoise = texture(Sampler0,
            texCoord * vec2(25.0, 31.0) + vec2(mistAge * 0.32, mistSeed * 8.9)).r;

    vec2 offsetCenter = centered + vec2(
            (mistSeed - 0.5) * 0.18,
            (contourNoise - 0.5) * 0.12);
    float radius = length(vec2(offsetCenter.x * 1.24, offsetCenter.y * 0.9));
    float raggedEdge = 0.48 + (contourNoise - 0.5) * 0.3;
    float speck = 1.0 - smoothstep(raggedEdge, raggedEdge + 0.24, radius);
    float chipped = smoothstep(0.24, 0.62, grainNoise + speck * 0.22);
    float alpha = vertexColor.a * speck * chipped;

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGradient = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGradient, depthDiff);
    if (alpha < 0.001) discard;

    vec3 grainGray = mix(vec3(0.7, 0.72, 0.73), vec3(0.9, 0.91, 0.91), grainNoise);
    fragColor = vec4(grainGray * vertexColor.rgb, alpha);
}
