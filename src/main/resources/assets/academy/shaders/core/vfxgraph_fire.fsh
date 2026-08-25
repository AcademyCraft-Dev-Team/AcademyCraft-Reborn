#version 330

#define STRENGTH 6.0

uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float pSeed;
in float pAge;

out vec4 fragColor;

vec2 hash( vec2 p ){
	p = vec2( dot(p,vec2(127.1,311.7)),
			 dot(p,vec2(269.5,183.3)) );
	return -1.0 + 2.0*fract(sin(p)*43758.5453123);
}

float noise(in vec2 p) {
    const float K1 = 0.366025404;
    const float K2 = 0.211324865;
    vec2 i = floor(p + (p.x + p.y) * K1);
    vec2 a = p - i + (i.x + i.y) * K2;
    vec2 o = (a.x > a.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec2 b = a - o + K2;
    vec2 c = a - 1.0 + 2.0 * K2;
    vec3 h = max(0.5 - vec3(dot(a, a), dot(b, b), dot(c, c)), 0.0);
    vec3 n = h * h * h * h * vec3(dot(a, hash(i)), dot(b, hash(i + o)), dot(c, hash(i + 1.0)));
    return dot(n, vec3(70.0));
}

float fbm(vec2 uv) {
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    float f = 0.5000 * noise(uv); uv = m * uv;
    f += 0.2500 * noise(uv); uv = m * uv;
    f += 0.1250 * noise(uv); uv = m * uv;
    f += 0.0625 * noise(uv); uv = m * uv;
    return 0.5 + 0.5 * f;
}

void main() {
    // ---- 保留原时间逻辑（使每个粒子有独立相位） ----
    float time = pAge * 2.5 + pSeed * 40.0;

    // ---- 读取强度常量（防止除零或负值） ----
    float strength = STRENGTH;
    float safeStrength = max(0.1, strength);

    // ---- 移植 Shadertoy 的空间变换（单柱版） ----
    vec2 uv = texCoord;
    vec2 q = uv;

    // [原 Shadertoy 有 q.x *= 5.0; 此处去掉，让火柱居中]
    q.x -= 0.5;                     // 单柱水平居中（替代原多柱的 mod 逻辑）

    // [原 Shadertoy 的垂直拉伸，完整保留]
    q.y *= 2.0;                     // 使火焰更修长
    q.y -= 0.25;                    // 垂直偏移，对齐原算法

    // ---- 移植 Shadertoy 的强度驱动逻辑（核心差异点） ----
    // 1. 时间加速：原版用 max(3.0, 1.25 * strength) 保底加速
    float T3 = max(3.0, 1.25 * safeStrength) * time;

    // 2. 噪声采样缩放：strength 越大，火焰细节被拉伸得越粗犷
    float n = fbm(safeStrength * q - vec2(0.0, T3));

    // ---- 以下火焰形状和颜色公式，完全照搬 Shadertoy（未改一字） ----
    float c = 1.0 - 16.0 * pow(max(0.0, length(q * vec2(1.8 + q.y * 1.5, 0.75)) - n * max(0.0, q.y + 0.25)), 1.2);
    float c1 = n * c * (1.5 - pow(2.50 * uv.y, 4.0));
    c1 = clamp(c1, 0.0, 1.0);

    // 暖色指数带（红/橙/黄）
    vec3 col = vec3(1.5 * c1, 1.5 * c1 * c1 * c1, c1 * c1 * c1 * c1 * c1 * c1);

    // 透明度（沿 y 轴衰减）
    float a = c * (1.0 - pow(uv.y, 3.0));
    a = clamp(a, 0.0, 1.0);

    // ---- 叠加顶点颜色（维持原接口功能） ----
    col *= vertexColor.rgb;

    // ---- 软粒子（维持原接口功能，使用 Sampler1） ----
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    float depthDiff = gl_FragCoord.z - sceneDepth;
    float sceneGrad = max(length(vec2(dFdx(sceneDepth), dFdy(sceneDepth))) * 4.0, 1e-3);
    a *= smoothstep(0.0, sceneGrad, depthDiff);

    // ---- 最终输出 ----
    fragColor = vec4(col * a, 1.0);
}
