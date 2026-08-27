#version 330

uniform sampler2D Sampler1;

in vec2 texCoord;
in vec4 vertexColor;
in float impactAge;
in float impactSeed;
flat in vec2 impactCenter;
flat in float impactVisible;

out vec4 fragColor;

const float TWO_PI = 6.28318530717958647692;
const int TRIANGLE_COUNT = 30;

float hash11(float value) {
    return fract(sin(value * 127.1 + impactSeed * 19.7) * 43758.5453);
}

float easeOutCubic(float value) {
    float inverse = 1.0 - clamp(value, 0.0, 1.0);
    return 1.0 - inverse * inverse * inverse;
}

float contourNoise(float angle, float drawing, float scale) {
    float coordinate = fract(angle / TWO_PI + 0.5) * 72.0;
    float cell = floor(coordinate);
    float blend = smoothstep(0.0, 1.0, fract(coordinate));
    float coarse = mix(
        hash11(cell + drawing * 83.7 + 201.4),
        hash11(cell + 1.0 + drawing * 83.7 + 201.4),
        blend);
    float bristle = sin(angle * 53.0 + drawing * 4.9 + impactSeed * 11.0);
    float tooth = pow(hash11(cell * 7.3 + drawing * 29.1 + 249.8), 7.0);
    return (coarse - 0.5) * scale
        + bristle * scale * 0.16
        + tooth * scale * 0.72;
}

void main() {
    // Fifteen frames at 60 FPS: a held white anticipation, a black crush, an inverse release,
    // then a transparent chromatic afterglow over the real plasma detonation.
    float phase = clamp(impactAge, 0.0, 1.0);
    float drawingIndex;
    float drawingProgress;
    if (phase < 0.16) {
        drawingIndex = 0.0;
        drawingProgress = phase / 0.16;
    } else if (phase < 0.42) {
        drawingIndex = 1.0;
        drawingProgress = (phase - 0.16) / 0.26;
    } else if (phase < 0.68) {
        drawingIndex = 2.0;
        drawingProgress = (phase - 0.42) / 0.26;
    } else {
        drawingIndex = 3.0;
        drawingProgress = (phase - 0.68) / 0.32;
    }
    drawingProgress = smoothstep(
        0.0, 1.0, clamp(drawingProgress, 0.0, 1.0));

    // Square-pixel coordinates preserve a spherical blast on every aspect ratio. screenReach
    // is the distance from the physical impact to the farthest viewport corner, so both the
    // black crush and white release can cover the whole frame even for corner impacts.
    vec2 viewport = max(vec2(textureSize(Sampler1, 0)), vec2(1.0));
    float aspect = viewport.x / viewport.y;
    vec2 p = (texCoord - impactCenter) * 2.0 * vec2(aspect, 1.0);
    float radius = length(p);
    float angle = atan(p.y, p.x);
    vec2 farCornerUv = max(impactCenter, vec2(1.0) - impactCenter);
    float screenReach = length(farCornerUv * 2.0 * vec2(aspect, 1.0));
    vec2 inwardVector = (vec2(0.5) - impactCenter)
        * 2.0 * vec2(aspect, 1.0);
    float edgeFactor = clamp(
        length(inwardVector) / max(screenReach, 0.001), 0.0, 1.0);
    float edgeCoverage = smoothstep(0.18, 0.92, edgeFactor);
    float inwardAngle = atan(inwardVector.y, inwardVector.x);
    float pixelWidth = max(2.4 / viewport.y, 0.0012);

    // All four drawings share the same inward-pointing triangle language. Shape, rotation and
    // missing paint change between drawings so the sequence reads as hand-authored cels rather
    // than one procedural shape merely scaling up.
    float angularOffset;
    float keepThreshold;
    float widthScale;
    float eraseStrength;
    if (drawingIndex < 0.5) {
        angularOffset = -0.030;
        keepThreshold = 0.38;
        widthScale = mix(0.10, 0.18, drawingProgress);
        eraseStrength = 0.22;
    } else if (drawingIndex < 1.5) {
        angularOffset = 0.026;
        keepThreshold = 0.80;
        widthScale = mix(0.58, 0.78, drawingProgress);
        eraseStrength = 0.20;
    } else if (drawingIndex < 2.5) {
        angularOffset = -0.042;
        keepThreshold = mix(0.76, 0.46, drawingProgress);
        widthScale = mix(0.92, 0.64, drawingProgress);
        eraseStrength = mix(0.30, 0.52, drawingProgress);
    } else {
        angularOffset = 0.018;
        keepThreshold = mix(0.68, 0.38, drawingProgress);
        widthScale = mix(0.22, 0.14, drawingProgress);
        eraseStrength = mix(0.34, 0.60, drawingProgress);
    }
    widthScale *= mix(1.0, 1.48, edgeCoverage);
    keepThreshold = mix(
        keepThreshold, min(0.98, keepThreshold + 0.12), edgeCoverage);
    eraseStrength *= mix(1.0, 0.76, edgeCoverage);

    // The acute end is the real projected impact point; every mathematical base lies beyond
    // the farthest corner. Irregular transverse wipe-outs and longitudinal scratches provide
    // the partially painted, dry-brush flash requested for the visible shafts.
    float triangleMask = 0.0;
    float sectorAngle = TWO_PI / float(TRIANGLE_COUNT);
    float inwardRotation = inwardAngle * edgeCoverage;
    for (int i = 0; i < TRIANGLE_COUNT; i++) {
        float fi = float(i);
        float stableJitter = (hash11(fi + 4.7) - 0.5)
            * sectorAngle * 0.34;
        float drawingJitter = (hash11(fi + drawingIndex * 41.3 + 13.1) - 0.5)
            * sectorAngle * 0.16;
        float triangleAngle = fi * sectorAngle + inwardRotation
            + angularOffset + stableJitter + drawingJitter;
        vec2 direction = vec2(cos(triangleAngle), sin(triangleAngle));
        vec2 normal = vec2(-direction.y, direction.x);
        float along = dot(p, direction);
        float signedAcross = dot(p, normal);
        float across = abs(signedAcross);

        float contourVariation = mix(1.16, 1.54,
            hash11(fi + drawingIndex * 17.7 + 23.9));
        float outerRadius = screenReach * contourVariation;
        float radialProgress = clamp(
            (along + pixelWidth * 1.5) / max(outerRadius, 0.001), 0.0, 1.0);
        float visibleProgress = clamp(
            (along + pixelWidth * 1.5) / max(screenReach, 0.001), 0.0, 1.0);

        float halfBaseWidth = outerRadius * sectorAngle
            * mix(0.32, 0.52, hash11(fi + 51.6)) * widthScale;
        float allowedWidth = halfBaseWidth * pow(radialProgress, 0.88);
        float edgeCell = floor(
            visibleProgress * mix(8.0, 15.0, hash11(fi + 58.8)));
        float edgeNoise = hash11(
            fi * 23.7 + drawingIndex * 71.3 + edgeCell * 11.9);
        allowedWidth *= mix(0.54, 1.0, edgeNoise);
        float sideMask = 1.0 - smoothstep(
            allowedWidth, allowedWidth + pixelWidth * 1.35, across);
        float radialMask = smoothstep(
            -pixelWidth * 1.5, pixelWidth * 0.65, along);
        radialMask *= 1.0 - smoothstep(
            outerRadius - pixelWidth, outerRadius + pixelWidth, along);

        float bandCount = mix(5.0, 10.0, hash11(fi + 76.2));
        float lateralRatio = signedAcross / max(allowedWidth, pixelWidth);
        float bandWarp = lateralRatio
            * mix(0.10, 0.38, hash11(fi + 84.6));
        bandWarp += sin(
            visibleProgress * mix(10.0, 20.0, hash11(fi + 87.3)) + fi * 1.7
        ) * 0.15;
        float bandCoordinate = visibleProgress * bandCount
            + hash11(fi + 81.4) * 7.0 + bandWarp;
        float bandCell = floor(bandCoordinate);
        float bandPhase = fract(bandCoordinate);
        float eraseChoice = step(
            hash11(fi * 31.1 + drawingIndex * 47.9 + bandCell * 13.7),
            eraseStrength);
        float bandBody = smoothstep(0.07, 0.22, bandPhase)
            * (1.0 - smoothstep(0.68, 0.95, bandPhase));
        float centerProtection = smoothstep(0.12, 0.29, visibleProgress);
        float erasure = eraseChoice * bandBody * centerProtection;

        float scratchCenter = allowedWidth
            * (hash11(fi + drawingIndex * 19.3 + 92.7) - 0.5) * 1.30;
        float scratchWidth = max(
            pixelWidth * 1.5,
            allowedWidth * mix(0.04, 0.13, hash11(fi + 101.6)));
        float scratch = 1.0 - smoothstep(
            scratchWidth, scratchWidth + pixelWidth * 1.4,
            abs(signedAcross - scratchCenter));
        float scratchStart = mix(0.20, 0.46, hash11(fi + 109.2));
        float scratchEnd = min(0.97,
            scratchStart + mix(0.20, 0.46, hash11(fi + 117.5)));
        float scratchWindow = smoothstep(
            scratchStart, scratchStart + 0.05, visibleProgress);
        scratchWindow *= 1.0 - smoothstep(
            scratchEnd - 0.05, scratchEnd, visibleProgress);
        float scratchChoice = step(
            hash11(fi + drawingIndex * 37.1 + 125.8), 0.70);
        erasure = max(erasure,
            scratch * scratchWindow * scratchChoice * centerProtection);

        float keep = hash11(fi + drawingIndex * 29.1 + 67.4);
        if (keep <= keepThreshold) {
            triangleMask = max(
                triangleMask, sideMask * radialMask * (1.0 - erasure));
        }
    }

    float sourcePin = 1.0 - smoothstep(
        pixelWidth * 0.35, pixelWidth * 2.4, radius);
    triangleMask = max(triangleMask, sourcePin);
    float whiteField = clamp(
        0.91 + exp(-radius * radius * 3.8) * 0.11, 0.0, 1.0);

    vec3 color;
    float alpha;
    if (drawingIndex < 0.5) {
        // White anticipation: a quiet luminous field with sparse black perspective lines. The
        // tiny black pin makes every long acute shaft visibly resolve to one physical source.
        float hotCore = exp(-radius * radius * 18.0);
        float ink = triangleMask * mix(0.88, 1.0, drawingProgress);
        float monochrome = mix(whiteField, 0.0, ink);
        monochrome = max(monochrome, hotCore * (1.0 - sourcePin));
        color = vec3(monochrome);
        alpha = 1.0;
    } else if (drawingIndex < 1.5) {
        // A ragged black sphere crushes outward from the real origin. Ground-level impacts are
        // naturally clipped into the same black dome silhouette shown in the reference.
        float growth = pow(drawingProgress, 1.55);
        float blackRadius = screenReach * mix(0.075, 1.10, growth);
        float roughEdge = contourNoise(
            angle, drawingIndex, blackRadius * mix(0.10, 0.035, growth));
        float blackMass = 1.0 - smoothstep(
            -pixelWidth * 2.8, pixelWidth * 2.8,
            radius - blackRadius - roughEdge);
        float ink = max(blackMass, triangleMask * (1.0 - blackMass));
        color = vec3(mix(whiteField, 0.0, ink));
        alpha = 1.0;
    } else if (drawingIndex < 2.5) {
        // The polarity reverses: white erupts through the black field and pushes the remaining
        // dry-brush claws beyond the screen. It ends on a near-total white release drawing.
        float release = pow(drawingProgress, 0.78);
        float releaseRadius = screenReach * mix(0.025, 1.24, release);
        float roughEdge = contourNoise(
            angle, drawingIndex, releaseRadius * mix(0.13, 0.035, release));
        float whiteMass = 1.0 - smoothstep(
            -pixelWidth * 3.0, pixelWidth * 3.0,
            radius - releaseRadius - roughEdge);
        float retreatingInk = max(1.0 - whiteMass,
            triangleMask * (1.0 - whiteMass));
        color = vec3(1.0 - retreatingInk);
        alpha = 1.0;
    } else {
        // Return the world abruptly, then overlay the reference's warm core, cyan/magenta radial
        // separation and dark peripheral exposure. Existing 3D plasma, sparks and smoke remain
        // visible below this transparent afterimage.
        float fade = 1.0 - smoothstep(0.22, 0.92, drawingProgress);
        float core = exp(-radius * radius * 17.0);
        float halo = exp(-radius * radius * 8.0) * 0.13;
        float streaks = triangleMask
            * smoothstep(pixelWidth * 1.5, 0.055, radius);
        float waveRadius = mix(0.08, 0.58, easeOutCubic(drawingProgress));
        float wave = exp(-pow((radius - waveRadius) * 10.0, 2.0))
            * (1.0 - drawingProgress) * 0.55;
        float split = 0.5 + 0.5 * sin(
            angle * 5.0 + radius * 8.0 + impactSeed * 17.0);
        vec3 rayColor = mix(
            vec3(0.30, 0.72, 1.0),
            vec3(0.90, 0.38, 0.76),
            split);
        vec3 warmColor = mix(
            vec3(1.0, 0.84, 0.62), vec3(1.0), clamp(core * 1.4, 0.0, 1.0));
        rayColor = mix(warmColor, rayColor, 0.56);
        float warmAlpha = core * 0.94 + halo + wave * 0.22;
        float rayAlpha = streaks * 0.20;
        float lightAlpha = clamp(warmAlpha + rayAlpha, 0.0, 0.94);
        float vignette = smoothstep(
            screenReach * 0.34, screenReach * 0.98, radius);
        float darkAlpha = vignette * 0.18
            * (1.0 - clamp(streaks + halo, 0.0, 1.0))
            * (1.0 - drawingProgress);
        float combinedAlpha = max(lightAlpha, darkAlpha);
        vec3 lightColor = (warmColor * warmAlpha + rayColor * rayAlpha)
            / max(warmAlpha + rayAlpha, 0.001);
        color = (lightColor * lightAlpha
            + vec3(0.008, 0.014, 0.032) * darkAlpha)
            / max(lightAlpha + darkAlpha, 0.001);
        alpha = combinedAlpha * fade;
    }

    alpha *= vertexColor.a * impactVisible;
    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}
