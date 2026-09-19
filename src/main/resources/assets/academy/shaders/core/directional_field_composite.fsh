#version 330
#moj_import <academy:directional_field.glsl>
uniform sampler2D SceneDepth;
uniform sampler2D SurfaceMask;
uniform sampler2D VolumeMask;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 size = vec2(textureSize(VolumeMask, 0));
    vec2 grid = texCoord * size - 0.5;
    vec2 base = floor(grid), fraction = fract(grid);
    float depth = texture(SceneDepth, texCoord).r;
    float distance = depth > 1e-7 ? length(reconstruct(texCoord, depth)) : 100000.0;
    float volume = 0.0, weight = 0.0;
    for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
        vec2 uv = (base + vec2(x, y) + 0.5) / size;
        float d = texture(SceneDepth, uv).r;
        float other = d > 1e-7 ? length(reconstruct(uv, d)) : 100000.0;
        vec2 bilinear = mix(1.0 - fraction, fraction, vec2(x, y));
        float w = bilinear.x * bilinear.y * exp(-abs(other - distance) / max(0.12, distance * 0.01));
        volume += texture(VolumeMask, uv).r * w; weight += w;
    }
    volume = weight > 1e-5 ? volume / weight : texture(VolumeMask, texCoord).r;
    float surface = texture(SurfaceMask, texCoord).r;
    float alpha = min(0.30, surface * 0.17 + volume * 0.42);
    if (alpha < 0.001) discard;
    fragColor = vec4(0.91, 0.92, 0.94, alpha);
}
