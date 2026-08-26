#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float plasmaTime;

out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    float noise = texture(Sampler0, p * 0.42 + vec2(plasmaTime * 0.006, -plasmaTime * 0.004)).r;
    float outer = 1.0 - smoothstep(0.42, 1.0, radius);
    float layered = exp(-pow((radius - 0.58) * 3.3, 2.0))
            + exp(-pow((radius - 0.82) * 7.0, 2.0)) * 0.72;
    float alpha = vertexColor.a * outer * layered * (0.86 + noise * 0.14);
    vec3 color = mix(vec3(0.38, 0.55, 1.0), vec3(0.68, 0.36, 1.0), radius) * (0.72 + layered * 0.4);

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color * alpha, alpha);
}
