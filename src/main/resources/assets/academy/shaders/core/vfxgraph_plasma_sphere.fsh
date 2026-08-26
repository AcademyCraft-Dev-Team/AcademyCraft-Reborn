#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float plasmaTime;
in float plasmaSeed;

out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    float angle = atan(p.y, p.x);
    float slowTime = plasmaTime * 0.11;
    vec2 flowUv = vec2(angle * 0.15915494 + slowTime * 0.08 + plasmaSeed,
            radius * 1.7 - slowTime * 0.065);
    float broad = texture(Sampler0, flowUv).r;
    float cells = texture(Sampler0, flowUv * vec2(2.25, 1.65) + vec2(slowTime * 0.025, 0.0)).r;
    float liquidEdge = (broad - 0.5) * 0.045 + (cells - 0.5) * 0.018;
    float sphere = 1.0 - smoothstep(0.86 + liquidEdge, 1.0 + liquidEdge, radius);
    float whiteCore = exp(-radius * radius * 4.7);
    float blueMantle = smoothstep(0.12, 0.72, radius)
            * (1.0 - smoothstep(0.72 + liquidEdge, 1.01 + liquidEdge, radius));
    float violetLimb = smoothstep(0.62, 0.86, radius)
            * (1.0 - smoothstep(0.86, 1.01, radius));
    float liquidFlow = 0.90 + broad * 0.08 + cells * 0.02;

    vec3 hot = vec3(1.0, 0.995, 1.0) * whiteCore * 1.15;
    vec3 blue = mix(vec3(0.46, 0.66, 1.0), vertexColor.rgb, 0.18)
            * blueMantle * liquidFlow * 0.92;
    vec3 violet = vec3(0.56, 0.32, 1.0) * violetLimb * 0.7;
    vec3 color = hot + blue + violet;
    float alpha = vertexColor.a * sphere * clamp(whiteCore + blueMantle + violetLimb, 0.0, 1.0);

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color * alpha, alpha);
}
