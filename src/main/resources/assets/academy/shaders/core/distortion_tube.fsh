#version 150

uniform sampler2D Sampler0;
uniform float Time;

in vec2 texCoord0;
in vec3 distortionParams; // x=Strength, y=Speed(unused), z=Blur(unused)

out vec4 fragColor;

// 简单的哈希函数
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 2D 值噪声 (Value Noise) - 比之前的随机杂讯更平滑
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    // 五次多项式插值 (Quintic Interpolation) 消除晶格感
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    float strength = distortionParams.x;

    // 计算屏幕坐标
    // 注意：在某些环境下 gl_FragCoord 可能需要归一化，但 PostEffect 通常能直接处理
    vec2 screenUV = gl_FragCoord.xy / textureSize(Sampler0, 0);

    // 1. 流动噪声计算
    // 降低频率 (vec2(3.0, 1.5)) 让波纹更宽大，不再是密集条纹
    float n = noise(texCoord0 * vec2(3.0, 1.5));

    // 2. 计算偏移量
    // (n - 0.5) 将范围从 [0,1] 变为 [-0.5, 0.5]
    vec2 offset = (vec2(n) - 0.5) * strength * 0.1;

    // 3. 边缘虚化 (Fade)
    // 利用 UV.y (管长方向) 和 UV.x (管宽方向) 简单的做个边缘衰减
    // 这样管子两头和侧边不会有硬切边
    float edgeFade = 1.0;
    // 侧边淡出 (模拟圆管厚度)
    float sideFade = sin(texCoord0.x * 3.14159);
    edgeFade *= smoothstep(0.0, 0.2, sideFade);

    // 应用偏移
    vec2 finalUV = screenUV + offset * edgeFade;

    // 采样
    vec4 color = texture(Sampler0, finalUV);

    // 强制 Alpha 为 1，防止混合导致半透明变黑
    fragColor = vec4(color.rgb, 1.0);
}