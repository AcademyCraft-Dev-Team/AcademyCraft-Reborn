#version 330

layout(std140) uniform ArcLightning {
    vec4 LightningParams; // x = aces toggle, z = emission boost (Light × 6 语义)
};

in vec3 vNormal;
in vec2 texCoord0;
in vec4 vColor;
in vec3 vViewDir;

out vec4 fragColor;

vec3 acesApprox(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    // Blender Principled BSDF Emission：Emission = LColor × (Light × 6)。
    // 颜色 100% 由图数据顶点色驱动（零代码颜色常量）。
    vec3 base = max(vColor.rgb, vec3(1e-4));
    vec3 color = base * LightningParams.z;

    if (LightningParams.x > 0.5) {
        color = acesApprox(color);
    }

    // 不透明自发光（Blender alpha=1）
    fragColor = vec4(color, 1.0);
}