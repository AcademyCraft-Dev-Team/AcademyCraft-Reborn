#version 330

layout (std140) uniform RoundedRectUniforms {
    vec2 Size;
    vec2 Pad0;
    vec4 CornerRadius;
    float BorderWidth;
    float ShadowBlur;
    vec2 ShadowOffset;
    vec4 FillColor;
    vec4 BorderColor;
    vec4 ShadowColor;
    int GradientMode;
    vec4 GradientFrom;
    vec4 GradientTo;
};

in vec2 texCoord0;

out vec4 fragColor;

// iq 四角圆角盒 SDF, r 顺序: (tr, br, tl, bl)
float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

vec4 shapeColor() {
    if (GradientMode == 1) {
        return mix(GradientFrom, GradientTo, clamp(texCoord0.y, 0.0, 1.0));
    }
    if (GradientMode == 2) {
        return mix(GradientFrom, GradientTo, clamp(texCoord0.x, 0.0, 1.0));
    }
    if (GradientMode == 3) {
        float d = length((texCoord0 - 0.5) * 2.0);
        return mix(GradientFrom, GradientTo, clamp(d, 0.0, 1.0));
    }
    return FillColor;
}

void main() {
    if (Size.x <= 0.0 || Size.y <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec2 halfSize = Size * 0.5;
    vec2 center = texCoord0 * Size - halfSize;

    // CornerRadius 传参顺序 (tl, tr, br, bl) -> sdRoundBox 期望 (tr, br, tl, bl)
    vec4 r = vec4(CornerRadius.y, CornerRadius.z, CornerRadius.x, CornerRadius.w);

    vec4 shadow = vec4(0.0);
    if (ShadowBlur > 0.0 && ShadowColor.a > 0.0) {
        vec2 shadowP = center - ShadowOffset;
        float sdShadow = sdRoundBox(shadowP, halfSize, r);
        float shadowA = 1.0 - smoothstep(0.0, ShadowBlur, sdShadow);
        shadow = vec4(ShadowColor.rgb, ShadowColor.a * shadowA);
    }

    float outer = sdRoundBox(center, halfSize, r);
    float inner = BorderWidth > 0.0
        ? sdRoundBox(center, halfSize - vec2(BorderWidth), max(r - BorderWidth, 0.0))
        : outer;

    vec4 base = shapeColor();
    float aa = max(fwidth(outer), fwidth(inner));
    float shapeA = 1.0 - smoothstep(-aa, aa, outer);
    float fillCoverage = 1.0 - smoothstep(-aa, aa, inner);

    vec3 shapeRgb = mix(BorderColor.rgb, base.rgb, fillCoverage);
    vec3 rgb = mix(shadow.rgb, shapeRgb, shapeA);
    float alpha = max(shadow.a, shapeA);
    fragColor = vec4(rgb, alpha);
}
