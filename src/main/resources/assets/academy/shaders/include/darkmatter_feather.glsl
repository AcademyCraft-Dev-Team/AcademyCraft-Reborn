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
    float ivory = mix(0.79, 0.965, smoothstep(0.035, 0.78, v));
    ivory += 0.026 * (1.0 - smoothstep(0.08, 0.90, abs(s)));
    ivory -= 0.040 * smoothstep(0.12, 0.80, -s);
    ivory += sin(v * 5.4 + variant * 2.0) * 0.012;
    float branchCoordinate = (v - abs(s) * (s < 0.0 ? 0.265 : 0.315)
            + s * s * 0.025) * (9.0 + variant * 2.0);
    float bundleDistance = abs(fract(branchCoordinate + 0.5) - 0.5);
    float bundlePixel = max(fwidth(branchCoordinate), 0.001);
    float bundle = 1.0 - smoothstep(0.023, 0.023 + bundlePixel, bundleDistance);
    float branchVisibility = 1.0 - smoothstep(0.30, 0.90, bundlePixel);
    float vane = smoothstep(0.035, 0.12, abs(s)) * smoothstep(0.045, 0.17, v)
            * (1.0 - smoothstep(0.91, 1.0, v));
    ivory -= bundle * vane * branchVisibility * (0.057 + clamp(activity, 0.0, 1.0) * 0.025 - v * 0.027);
    // A small number of fine barbs fills each visible bundle; subpixel barbs disappear gracefully.
    float fineCoordinate = branchCoordinate * 3.0 + abs(s) * 0.15;
    float fineDistance = abs(fract(fineCoordinate + 0.5) - 0.5);
    float finePixel = max(fwidth(fineCoordinate), 0.001);
    float fine = (1.0 - smoothstep(0.028, 0.028 + finePixel, fineDistance))
            * (1.0 - smoothstep(0.20, 0.60, finePixel));
    ivory -= fine * vane * 0.019;
    // The rachis is pearl grey, with a slim illuminated side rather than an emissive stripe.
    float shaftWidth = mix(0.030, 0.007, v);
    float shaft = academyFeatherLine(s, shaftWidth) * (1.0 - smoothstep(0.95, 1.0, v));
    float shaftGrey = mix(0.64, 0.84, smoothstep(0.08, 0.85, v));
    ivory = mix(ivory, shaftGrey, shaft * 0.82);
    float shaftLight = academyFeatherLine(s - shaftWidth * 0.65, shaftWidth * 0.18);
    ivory += shaftLight * 0.055 * (1.0 - smoothstep(0.91, 1.0, v));
    return vec3(ivory, ivory * 0.997, ivory * 0.986);
}

vec4 academyDarkmatterFeather(vec2 uv, float phase, float activity) {
    float v = clamp(uv.y, 0.0, 1.0);
    float s = uv.x * 2.0 - 1.0 - academyFeatherShaftX(v);
    float variant = fract(phase * 0.754877666);
    // Wide shoulders, a short exposed quill, and a curved pointed tip. One vane is broader.
    float shoulder = pow(max(0.0, sin(pow(v, 0.73) * 3.14159265)), 0.60);
    shoulder *= smoothstep(0.018, 0.11, v);
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
