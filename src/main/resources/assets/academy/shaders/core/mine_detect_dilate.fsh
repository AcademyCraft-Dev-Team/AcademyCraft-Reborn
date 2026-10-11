#version 330
uniform sampler2D OreMask;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    ivec2 size = textureSize(OreMask, 0);
    ivec2 p = clamp(ivec2(texCoord * vec2(size)), ivec2(0), size - 1);
    vec4 best = texelFetch(OreMask, p, 0);
    for (int x = 1; x <= 3; x++) {
        float weight = 1.0 - smoothstep(2.0, 3.5, float(x));
        vec4 left = texelFetch(OreMask, clamp(p - ivec2(x, 0), ivec2(0), size - 1), 0);
        vec4 right = texelFetch(OreMask, clamp(p + ivec2(x, 0), ivec2(0), size - 1), 0);
        if (left.a * weight > best.a) best = vec4(left.rgb, left.a * weight);
        if (right.a * weight > best.a) best = vec4(right.rgb, right.a * weight);
    }
    fragColor = best;
}
