#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in float cloudTime;
layout(location = 3) in float cloudPhase;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    vec2 shift = vec2(cloudPhase * 0.37, cloudPhase * 0.63);
    // Sampler0 contains 256 independent noise cells: small UV spans produce cloud billows,
    // while whole-texture spans would turn the same sprites into fine static/grain.
    float broad = texture(Sampler0, texCoord * 0.018 + shift + vec2(cloudTime * 0.022, -cloudTime * 0.015)).r;
    float detail = texture(Sampler0, texCoord * 0.046 - shift + vec2(-cloudTime * 0.035, cloudTime * 0.02)).r;
    float micro = texture(Sampler0, texCoord * 0.13 + shift - cloudTime * 0.014).r;
    float noise = broad * 0.62 + detail * 0.28 + micro * 0.1;
    float radius = length(p);
    // Filled centres and eroded, feathery edges let overlapping puffs form a solid cloud volume.
    float density = max(0.0, 1.0 - radius * radius + (noise - 0.5) * 0.9);
    float alpha = vertexColor.a * smoothstep(0.0, 0.65, density);
    float light = 0.42 + broad * 0.55 + detail * 0.16 - p.y * 0.075;
    vec3 color = vertexColor.rgb * light;
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    alpha *= smoothstep(0.0, sceneGrad, depthDiff);
    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}
