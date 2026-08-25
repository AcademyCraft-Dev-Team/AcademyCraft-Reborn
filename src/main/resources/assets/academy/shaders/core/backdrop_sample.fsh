#version 330

// Backdrop sample: cross-blends two blur-pyramid levels of the backdrop into a
// screen-space blur region, with an optional rounded-corner fade. Vertex shader is
// screen_blit (POSITION NDC quad); texCoord is the pixel's normalized position over
// the full target (scissor confines the draw to the region).

uniform sampler2D Sampler0; // sharper level (floor(k)), L0 when radius~0
uniform sampler2D Sampler1; // blurkier level (ceil(k))

layout(std140) uniform BackdropInfo {
    vec4 RegionRect;       // region in normalized backdrop space (bottom-up): xy=min, zw=size
    vec4 Tint;             // rgba tint color (a = tint strength)
    vec2 OutSize;          // full texture resolution (px)
    float LevelLerp;       // frac(k) in [0,1]
    float CornerRadius;    // region corner radius (px)
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // texCoord is already the pixel's normalized position, so sample the backdrop
    // at the same location on both pyramid levels.
    vec4 sharp = texture(Sampler0, texCoord);
    vec4 blur = texture(Sampler1, texCoord);

    // Cross-fade between pyramid levels. frac=0/1 gives the limiting level exactly,
    // so integer level boundaries are continuous; radius=0 keeps only L0 (identity).
    vec3 blurred = mix(sharp.rgb, blur.rgb, clamp(LevelLerp, 0.0, 1.0));

    // Optional rounded-rectangle SDF fade at the region corners (px/local space).
    vec2 regionCenter = RegionRect.xy + 0.5 * RegionRect.zw;
    vec2 p = (texCoord - regionCenter) * OutSize;
    vec2 halfSize = 0.5 * RegionRect.zw * OutSize;
    float r = min(CornerRadius, min(halfSize.x, halfSize.y));
    vec2 d = abs(p) - (halfSize - vec2(r));
    vec2 outside = max(d, vec2(0.0));
    float inside = min(max(d.x, d.y), 0.0);
    float dist = length(outside) + inside - r;
    float feather = max(1.0, CornerRadius * 0.25);
    float alpha = 1.0 - smoothstep(-feather, 0.0, dist);

    // Tint applied on top of the blurred backdrop; output premultiplied.
    vec3 tinted = mix(blurred, blurred * Tint.rgb, Tint.a);
    fragColor = vec4(tinted * alpha, alpha);
}
