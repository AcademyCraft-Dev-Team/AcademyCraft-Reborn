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
    float liquidTime = plasmaTime * 0.18;

    vec2 broadUv = vec2(
            angle * 0.15915494 + liquidTime * 0.026 + plasmaSeed,
            radius * 1.08 - liquidTime * 0.018
    );
    float broadA = texture(Sampler0, broadUv).r;
    float broadB = texture(Sampler0,
            broadUv * vec2(0.72, 1.34) + vec2(-liquidTime * 0.013, liquidTime * 0.009)).r;
    float liquidMass = broadA * 0.62 + broadB * 0.38;

    float edgeFlow = (liquidMass - 0.5) * 0.075;
    float sphere = 1.0 - smoothstep(0.86 + edgeFlow, 1.0 + edgeFlow, radius);
    float whiteCore = exp(-radius * radius * 3.9);
    float liquidBody = smoothstep(0.08, 0.82, radius)
            * (1.0 - smoothstep(0.79 + edgeFlow, 1.01 + edgeFlow, radius));
    float liquidFold = smoothstep(0.24, 0.76, liquidMass) * liquidBody;
    float blueLimb = smoothstep(0.66, 0.88, radius)
            * (1.0 - smoothstep(0.88 + edgeFlow, 1.02 + edgeFlow, radius));

    vec3 hot = vec3(1.0, 0.995, 1.0) * whiteCore * 1.3;
    vec3 pinkLiquid = mix(vec3(1.0, 0.38, 0.78), vec3(1.0, 0.72, 0.94), liquidMass)
            * liquidBody * (0.72 + liquidFold * 0.38);
    vec3 coolLimb = vec3(0.50, 0.72, 1.0) * blueLimb * 0.72;
    vec3 color = hot + pinkLiquid + coolLimb;
    float alpha = vertexColor.a * sphere
            * clamp(whiteCore + liquidBody * 0.94 + blueLimb, 0.0, 1.0);

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color * alpha, alpha);
}
