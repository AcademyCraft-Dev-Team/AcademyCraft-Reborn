#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float pSeed;
in float pAge;

out vec4 fragColor;

void main() {
    vec2 d = texCoord * 2.0 - 1.0;
    float h = texCoord.y;
    // 噪声扰动半径：烟雾卷须边缘（低频 wobble，随粒子种子/年龄演化）
    float n = texture(Sampler0, vec2(pSeed * 1.7 + h * 0.5, pAge * 0.3)).r;
    float radius = 0.5 + (n - 0.5) * 0.1;
    vec2 p = vec2(d.x / radius, d.y);
    float dist = length(p);
    float fall = 1.0 - smoothstep(0.4, 1.0, dist);
    fall = max(fall, 0.0);
    // 顶部渐隐：烟团扩散消散
    float vert = 1.0 - smoothstep(0.6, 1.0, h);
    float a = vertexColor.a * fall * vert;
    // soft particles：深度软边
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    a *= smoothstep(0.0, sceneGrad, depthDiff);
    fragColor = vec4(vertexColor.rgb, a);
}
