#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 28) in float smokeSeed;
layout(location = 27) in float smokeAge;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    float coarse = texture(Sampler0, texCoord * 1.25 + vec2(smokeSeed, smokeAge * 0.12)).r;
    float detail = texture(Sampler0, texCoord * 3.1 + vec2(-smokeAge * 0.18, smokeSeed)).r;
    float edge = 0.72 + (coarse - 0.5) * 0.22 + (detail - 0.5) * 0.08;
    float density = 1.0 - smoothstep(edge, 1.02, radius);
    density *= smoothstep(0.08, 0.42, coarse * 0.7 + detail * 0.3);
    float alpha = vertexColor.a * density;

    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    vec3 grey = vertexColor.rgb * mix(0.72, 1.08, coarse);
    fragColor = vec4(grey, alpha);
}
