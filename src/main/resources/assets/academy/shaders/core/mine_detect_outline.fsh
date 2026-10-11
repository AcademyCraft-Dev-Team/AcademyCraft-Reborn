#version 330
uniform sampler2D OreMask;
uniform sampler2D HorizontalMask;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    ivec2 size = textureSize(OreMask, 0);
    ivec2 p = clamp(ivec2(texCoord * vec2(size)), ivec2(0), size - 1);
    vec4 center = texelFetch(OreMask, p, 0);
    if (center.a > 0.5) {
        float boundary = 0.0;
        const ivec2 directions[4] = ivec2[4](ivec2(2, 0), ivec2(-2, 0), ivec2(0, 2), ivec2(0, -2));
        for (int i = 0; i < 4; i++) {
            vec4 other = texelFetch(OreMask, clamp(p + directions[i], ivec2(0), size - 1), 0);
            if (other.a > 0.5 && length(center.rgb - other.rgb) > 0.15) boundary = 0.65;
        }
        fragColor = vec4(center.rgb, boundary * 0.8);
        return;
    }
    // Separable 3-pixel dilation. Keep only the outside of the solid projection.
    vec4 best = texelFetch(HorizontalMask, p, 0);
    for (int y = 1; y <= 3; y++) {
        float weight = 1.0 - smoothstep(2.0, 3.5, float(y));
        vec4 down = texelFetch(HorizontalMask, clamp(p - ivec2(0, y), ivec2(0), size - 1), 0);
        vec4 up = texelFetch(HorizontalMask, clamp(p + ivec2(0, y), ivec2(0), size - 1), 0);
        if (down.a * weight > best.a) best = vec4(down.rgb, down.a * weight);
        if (up.a * weight > best.a) best = vec4(up.rgb, up.a * weight);
    }
    fragColor = vec4(best.rgb, best.a * 0.8);
}
