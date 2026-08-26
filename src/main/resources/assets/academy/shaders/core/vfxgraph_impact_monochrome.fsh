#version 330

uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float impactAge;
in float impactSeed;
flat in vec2 impactCenter;
flat in float impactVisible;

out vec4 fragColor;

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;

float hash11(float value) {
    return fract(sin(value * 127.1 + impactSeed * 19.7) * 43758.5453);
}

float angleDistance(float a, float b) {
    return abs(atan(sin(a - b), cos(a - b)));
}

void main() {
    // InstanceAge is supplied as normalized age/lifetime by VfxGraphRenderer.
    float phase = clamp(impactAge, 0.0, 1.0);
    float expansion = 1.0 - pow(1.0 - phase, 3.0);
    float visibility = 1.0 - smoothstep(0.72, 1.0, phase);

    // Build the frame in square screen-pixel space. This keeps the impact core circular and
    // prevents radial brush strokes from becoming horizontal ellipses on wide viewports.
    vec2 viewport = max(vec2(textureSize(Sampler1, 0)), vec2(1.0));
    float aspect = viewport.x / viewport.y;
    vec2 p = (texCoord - impactCenter) * 2.0 * vec2(aspect, 1.0);
    float radius = length(p);
    float angle = atan(p.y, p.x);
    vec2 farCornerUv = max(impactCenter, vec2(1.0) - impactCenter);
    float screenReach = length(farCornerUv * 2.0 * vec2(aspect, 1.0));

    // A clean circular point of impact anchors the otherwise rough, hand-cut silhouette.
    float coreRadius = mix(0.175, 0.070, expansion);
    float core = 1.0 - smoothstep(coreRadius, coreRadius + 0.012, radius);

    // Dense inner ink mass: several angular harmonics break its edge into large asymmetric
    // wedges instead of producing the regular cog/star outline of the old frame.
    float broadLobes = pow(abs(sin(angle * 4.0 + 0.65)), 5.0);
    float fineLobes = pow(abs(cos(angle * 9.0 - sin(angle * 3.0) * 0.8)), 10.0);
    float inkRadius = coreRadius + mix(0.07, 0.46, expansion)
        * (0.20 + broadLobes * 0.56 + fineLobes * 0.24);
    float innerInk = 1.0 - smoothstep(inkRadius - 0.018, inkRadius + 0.012, radius);

    // A broken brush ring expands out of the exact impact point. Three unequal gaps, angular
    // radius wobble and changing stroke weight keep it recognisably circular without becoming
    // a clean HUD-style ring or competing with the radial wedges.
    float ringProgress = smoothstep(0.0, 0.82, expansion);
    float ringRadius = mix(coreRadius * 1.36, 0.88, ringProgress);
    float ringWobble = sin(angle * 7.0 + 0.45) * 0.016
        + sin(angle * 13.0 - 1.20) * 0.008
        + sin(angle * 3.0 + 1.75) * 0.011;
    float ringWeightNoise = 0.70 + 0.30 * (sin(angle * 9.0 - 0.35) * 0.5 + 0.5);
    float ringHalfWidth = mix(0.043, 0.018, ringProgress) * ringWeightNoise;
    float ringDistance = abs(radius - (ringRadius + ringWobble));
    float impactRing = 1.0 - smoothstep(ringHalfWidth, ringHalfWidth * 1.65, ringDistance);

    float arcMask = smoothstep(0.18, 0.34, angleDistance(angle, 0.30));
    arcMask *= smoothstep(0.26, 0.44, angleDistance(angle, 2.58));
    arcMask *= smoothstep(0.14, 0.29, angleDistance(angle, -1.42));
    impactRing *= arcMask;

    // Long perspective blades. Most are hairline-fast, while a handful become broad black
    // brush wedges. Angle jitter, random reach and taper keep the burst deliberately uneven.
    float mainStrokes = 0.0;
    float cutoutStrokes = 0.0;
    for (int i = 0; i < 44; i++) {
        float fi = float(i);
        float rayAngle = fi / 44.0 * TWO_PI + (hash11(fi + 4.2) - 0.5) * 0.46;
        vec2 direction = vec2(cos(rayAngle), sin(rayAngle));
        vec2 normal = vec2(-direction.y, direction.x);
        float upwardBias = smoothstep(-1.0, 1.0, direction.y);
        float along = dot(p, direction);
        float across = abs(dot(p, normal));
        float rayStart = coreRadius * mix(0.55, 1.05, hash11(fi + 12.8));
        float rayEnd = mix(0.32, screenReach * 1.15, expansion)
            * mix(0.52, 1.28, hash11(fi + 20.1));
        rayEnd *= mix(0.62, 1.34, upwardBias);
        float rayWidth = mix(0.006, 0.030, pow(hash11(fi + 29.6), 1.8));
        rayWidth *= mix(0.72, 1.48, upwardBias);
        if ((i % 6) == 0) rayWidth *= mix(3.8, 6.5, hash11(fi + 38.4));

        float progress = clamp((along - rayStart) / max(rayEnd - rayStart, 0.001), 0.0, 1.0);
        float taperedWidth = rayWidth * mix(1.0, 0.055, pow(progress, 0.72));
        float stroke = 1.0 - smoothstep(taperedWidth, taperedWidth * 1.55, across);
        stroke *= smoothstep(rayStart - 0.022, rayStart + 0.020, along);
        stroke *= 1.0 - smoothstep(rayEnd * 0.78, rayEnd, along);

        if (hash11(fi + 47.0) < 0.78) {
            mainStrokes = max(mainStrokes, stroke);
        } else {
            cutoutStrokes = max(cutoutStrokes, stroke);
        }
    }

    // Detached, short ink slashes make the silhouette feel like a painted animation cel rather
    // than one continuous procedural star. They travel outward with the expanding impact beat.
    float mainFragments = 0.0;
    float cutoutFragments = 0.0;
    for (int i = 0; i < 22; i++) {
        float fi = float(i);
        float fragmentAngle = hash11(fi + 71.0) * TWO_PI;
        vec2 direction = vec2(cos(fragmentAngle), sin(fragmentAngle));
        vec2 normal = vec2(-direction.y, direction.x);
        float centreRadius = mix(0.24, screenReach * 0.84, hash11(fi + 82.3))
            * mix(0.32, 1.0, expansion);
        centreRadius *= mix(0.72, 1.20, smoothstep(-1.0, 1.0, direction.y));
        vec2 centre = direction * centreRadius;
        vec2 local = p - centre;
        float along = dot(local, direction);
        float across = abs(dot(local, normal));
        float halfLength = mix(0.035, 0.21, hash11(fi + 93.7));
        float halfWidth = mix(0.008, 0.038, hash11(fi + 104.9));
        float fragmentTaper = halfWidth
            * mix(1.0, 0.25, clamp((along + halfLength) / (halfLength * 2.0), 0.0, 1.0));
        float fragment = 1.0 - smoothstep(fragmentTaper, fragmentTaper * 1.50, across);
        fragment *= 1.0 - smoothstep(halfLength * 0.72, halfLength, abs(along));

        if (hash11(fi + 116.2) < 0.74) {
            mainFragments = max(mainFragments, fragment);
        } else {
            cutoutFragments = max(cutoutFragments, fragment);
        }
    }

    // A compressed horizon slash gives the frame the strong perspective focal line visible in
    // hand-drawn anime impact cels, without changing the core itself into an ellipse.
    float horizonHalfLength = mix(0.25, min(screenReach, aspect) * 0.78, expansion);
    float horizonWidth = mix(0.030, 0.010, expansion);
    float horizon = 1.0 - smoothstep(horizonWidth, horizonWidth * 1.75, abs(p.y));
    horizon *= 1.0 - smoothstep(horizonHalfLength * 0.68, horizonHalfLength, abs(p.x));
    horizon *= smoothstep(coreRadius * 0.42, coreRadius * 0.92, abs(p.x));

    float mainShape = max(max(core, innerInk), max(mainStrokes, mainFragments));
    mainShape = max(mainShape, impactRing);
    mainShape = max(mainShape, horizon);
    float cutouts = max(cutoutStrokes, cutoutFragments);

    // Two-frame polarity change: first a black ink burst on white, then the exact silhouette
    // flashes white on black. This preserves the video's high-contrast inverted second frame.
    float earlyValue = 1.0 - mainShape;
    earlyValue = mix(earlyValue, 1.0, cutouts);
    float lateValue = mainShape;
    lateValue = mix(lateValue, 0.0, cutouts);
    float inverted = step(0.36, phase);
    float monochrome = mix(earlyValue, lateValue, inverted);

    // Full-screen, nearly opaque coverage keeps the impact readable over bright skies and at
    // reduced render distance while retaining a very small amount of scene structure.
    float alpha = vertexColor.a * visibility * impactVisible
        * mix(0.965, 1.0, max(mainShape, cutouts));
    if (alpha < 0.002) discard;
    fragColor = vec4(vec3(monochrome), alpha);
}
