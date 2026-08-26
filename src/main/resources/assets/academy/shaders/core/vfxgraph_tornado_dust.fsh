#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float dustTime;
in float dustSeed;

out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    float grain = texture(Sampler0,
            texCoord * 2.35 + vec2(dustSeed + dustTime * 0.06, -dustTime * 0.14)).r;
    float softMote = 1.0 - smoothstep(0.10 + grain * 0.05, 1.02, radius);
    float alpha = vertexColor.a * softMote * (0.66 + grain * 0.42);
    vec3 color = mix(vertexColor.rgb * 0.76, vec3(0.72, 0.71, 0.68), grain * 0.28);

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}
