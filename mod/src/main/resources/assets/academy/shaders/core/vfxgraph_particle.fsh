#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler1;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 d = texCoord * 2.0 - 1.0;
    float dist = length(d);
    float fall = 1.0 - smoothstep(0.35, 1.0, dist);
    fall = max(fall, 0.0);
    float a = vertexColor.a * fall;
    // soft particles：深度软边，避免生硬裁切（深度不连续处由 alpha 归零保证，无硬 discard）
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    a *= smoothstep(0.0, sceneGrad, depthDiff);
    fragColor = vec4(vertexColor.rgb, a);
}
