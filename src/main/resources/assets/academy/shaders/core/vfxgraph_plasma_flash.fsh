#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    float whiteDisc = exp(-radius * radius * 7.5);
    float hotCorona = exp(-radius * 4.2) * (1.0 - smoothstep(0.82, 1.0, radius));
    float softHalo = (1.0 - smoothstep(0.18, 1.0, radius)) * 0.34;
    float energy = whiteDisc * 1.8 + hotCorona * 1.25 + softHalo;
    vec3 color = vec3(1.0, 0.995, 1.0) * whiteDisc * 1.9
            + vec3(1.0, 0.56, 0.88) * hotCorona * 0.82
            + vec3(0.52, 0.68, 1.0) * softHalo * 0.68;
    float alpha = vertexColor.a * clamp(energy, 0.0, 1.0)
            * (1.0 - smoothstep(0.86, 1.0, radius));

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color * alpha, alpha);
}
