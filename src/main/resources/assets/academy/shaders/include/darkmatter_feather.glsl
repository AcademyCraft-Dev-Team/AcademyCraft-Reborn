// Shared physical feather material for Dark Matter. No noisy luminous outlines or tiled lace.
// UV y runs from the softly shaded root to the ivory tip; x spans the two asymmetric vanes.
float academyFeatherShaftX(float v) {
    return -0.115 * (1.0 - v) + sin(v * 3.14159265) * 0.032;
}

float academyFeatherLine(float distance, float width) {
    float pixel = max(fwidth(distance), 0.001);
    return 1.0 - smoothstep(width - pixel, width + pixel, abs(distance));
}

vec3 academyDarkmatterPearl(vec2 uv, float phase, float activity) {
    float v = clamp(uv.y, 0.0, 1.0);
    float s = uv.x * 2.0 - 1.0 - academyFeatherShaftX(v);
    float variant = fract(phase * 0.754877666);
    // The low-frequency body shading stays visible when a complete wing is viewed at distance.
    float ivory = mix(0.745, 0.955, smoothstep(0.025, 0.82, v));
    ivory += 0.038 * (1.0 - smoothstep(0.06, 0.90, abs(s)));
    ivory -= 0.065 * smoothstep(0.10, 0.84, -s);
    ivory += (variant - 0.5) * 0.024;
    float branchCoordinate = (v - abs(s) * (s < 0.0 ? 0.265 : 0.315)
            + s * s * 0.025) * (9.0 + variant * 2.0);
    float bundleDistance = abs(fract(branchCoordinate + 0.5) - 0.5);
    float bundlePixel = max(fwidth(branchCoordinate), 0.001);
    float bundle = 1.0 - smoothstep(0.023, 0.023 + bundlePixel, bundleDistance);
    float branchVisibility = 1.0 - smoothstep(0.30, 0.90, bundlePixel);
    float vane = smoothstep(0.035, 0.12, abs(s)) * smoothstep(0.045, 0.17, v)
            * (1.0 - smoothstep(0.91, 1.0, v));
    ivory -= bundle * vane * branchVisibility * (0.024 + clamp(activity, 0.0, 1.0) * 0.009 - v * 0.010);
    // A small number of fine barbs fills each visible bundle; subpixel barbs disappear gracefully.
    float fineCoordinate = branchCoordinate * 3.0 + abs(s) * 0.15;
    float fineDistance = abs(fract(fineCoordinate + 0.5) - 0.5);
    float finePixel = max(fwidth(fineCoordinate), 0.001);
    float fine = (1.0 - smoothstep(0.028, 0.028 + finePixel, fineDistance))
            * (1.0 - smoothstep(0.20, 0.60, finePixel));
    ivory -= fine * vane * 0.008;
    // The rachis is pearl grey, with a slim illuminated side rather than an emissive stripe.
    float shaftWidth = mix(0.030, 0.007, v);
    float shaft = academyFeatherLine(s, shaftWidth) * (1.0 - smoothstep(0.95, 1.0, v));
    float shaftGrey = mix(0.70, 0.89, smoothstep(0.08, 0.85, v));
    ivory = mix(ivory, shaftGrey, shaft * 0.62);
    float shaftLight = academyFeatherLine(s - shaftWidth * 0.65, shaftWidth * 0.18);
    ivory += shaftLight * 0.055 * (1.0 - smoothstep(0.91, 1.0, v));
    return vec3(ivory, ivory * 0.997, ivory * 0.986);
}

vec4 academyDarkmatterFeather(vec2 uv, float phase, float activity) {
    float v = clamp(uv.y, 0.0, 1.0);
    float s = uv.x * 2.0 - 1.0 - academyFeatherShaftX(v);
    float variant = fract(phase * 0.754877666);
    // Wide shoulders, a short exposed quill, and a curved pointed tip. One vane is broader.
    // A compact shoulder followed by a long taper reads as a flight feather at wing scale.
    float shoulder = smoothstep(0.018, 0.17, v)
            * (1.0 - 0.18 * smoothstep(0.20, 0.52, v))
            * pow(clamp((1.0 - v) / 0.52, 0.0, 1.0), 0.90);
    float width = shoulder * (s < 0.0 ? 0.75 : 0.92);
    float notchA = exp(-pow((v - (0.36 + variant * 0.07)) / 0.022, 2.0));
    float notchB = exp(-pow((v - (0.72 - variant * 0.05)) / 0.017, 2.0));
    width -= (s < 0.0 ? notchA * 0.073 : notchB * 0.055) * (0.80 + clamp(activity, 0.0, 1.0) * 0.40);
    float edge = width - abs(s);
    float pixel = max(fwidth(edge), 0.002);
    float vaneCoverage = smoothstep(-pixel, pixel, edge);
    float quill = academyFeatherLine(s, 0.022 * (1.0 - v))
            * (1.0 - smoothstep(0.11, 0.19, v));
    float thinMargin = mix(0.48, 1.0, smoothstep(0.0, 0.080, edge));
    float coverage = max(vaneCoverage * thinMargin, quill * 0.82);
    coverage *= 1.0 - smoothstep(0.987, 1.0, v);
    return vec4(academyDarkmatterPearl(uv, phase, activity), coverage);
}
