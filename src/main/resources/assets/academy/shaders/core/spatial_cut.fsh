#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;
uniform sampler2D Sampler8;
uniform sampler2D Sampler9;
uniform sampler2D Sampler10;
uniform sampler2D Sampler11;
uniform sampler2D Sampler12;
uniform sampler2D Sampler13;

layout (std140) uniform SpatialCutPost {
    // Selected slot count, Z convention, and diagnostic-classification toggle.
    vec4 Params;
    vec4 Plane0;
    vec4 Plane1;
    vec4 Plane2;
    vec4 Plane3;
    vec4 Plane4;
    // xy = positive half, zw = negative half. Both are cut-wide UV offsets.
    vec4 Offset0;
    vec4 Offset1;
    vec4 Offset2;
    vec4 Offset3;
    vec4 Offset4;
    mat4 InverseProjection;
};

in vec2 texCoord;
out vec4 fragColor;

const float DEPTH_EPSILON = 1.0e-5;
const float DIRECTION_EPSILON = 1.0e-6;
const float PLANE_EPSILON = 2.0e-3;
const float RAY_PARALLEL_EPSILON = 1.0e-5;
const float SAFE_RETURN_MAX_SLOPE = 8.0e-1;
const float SAFE_RETURN_RAMP_STEPS = 2.5e-1;
const float SAFE_RETURN_FULL_STEPS = 1.5;
const float VIEWPORT_MARGIN_TEXELS = 1.5;
const float AVAILABLE_STEPS_SMOOTH_MIN_WIDTH = 5.0e-2;
const float AVAILABLE_STEPS_INFINITY = 1.0e20;
const float MIN_SOURCE_VALID_WEIGHT = 7.5e-1;
const int ALPHA_CUTOUT_SEARCH_RADIUS = 2;

const int DEBUG_SELECTED_DEPTH_INVALID = 1;
const int DEBUG_MASK_MISSING = 2;
const int DEBUG_DESTINATION_IN_FRONT = 3;
const int DEBUG_AFFINE_OFFSET_INVALID = 4;
const int DEBUG_SOURCE_UV_OUTSIDE = 5;
const int DEBUG_SOURCE_PLANE_REJECTED = 6;

bool isFiniteScalar(float value) {
    return value >= -1.0e30 && value <= 1.0e30;
}

bool isFiniteVec2(vec2 value) {
    return isFiniteScalar(value.x) && isFiniteScalar(value.y);
}

bool isFiniteVec3(vec3 value) {
    return isFiniteScalar(value.x) && isFiniteScalar(value.y)
        && isFiniteScalar(value.z);
}

bool isFiniteVec4(vec4 value) {
    return isFiniteVec3(value.xyz) && isFiniteScalar(value.w);
}

bool isFiniteDepth(float depth) {
    return depth >= 0.0 && depth <= 1.0;
}

bool isValidCutDepth(float depth) {
    return depth >= 0.0 && depth < 1.0 - DEPTH_EPSILON;
}

// Minecraft uses reversed Z: a larger depth is closer to the camera.
bool isInFrontOfCut(float sceneDepth, float cutDepth) {
    return sceneDepth > cutDepth + DEPTH_EPSILON;
}

vec4 diagnosticFallback(vec4 scene, int reason) {
    if (Params.z <= 0.5) return scene;
    if (reason == DEBUG_SELECTED_DEPTH_INVALID) return vec4(1.0, 0.05, 0.05, 1.0);
    if (reason == DEBUG_MASK_MISSING) return vec4(1.0, 0.0, 1.0, 1.0);
    if (reason == DEBUG_DESTINATION_IN_FRONT) return vec4(1.0, 0.45, 0.0, 1.0);
    if (reason == DEBUG_AFFINE_OFFSET_INVALID) return vec4(0.10, 0.20, 1.0, 1.0);
    if (reason == DEBUG_SOURCE_UV_OUTSIDE) return vec4(0.0, 1.0, 1.0, 1.0);
    if (reason == DEBUG_SOURCE_PLANE_REJECTED) return vec4(1.0, 1.0, 0.0, 1.0);
    return scene;
}

vec4 diagnosticSuccess(vec4 shiftedScene) {
    return Params.z > 0.5 ? vec4(0.05, 0.85, 0.10, 1.0) : shiftedScene;
}

bool footprintInside(vec2 sourceUv, ivec2 sizePixels, out ivec2 basePixel) {
    vec2 pixelPosition = sourceUv * vec2(sizePixels) - vec2(0.5);
    basePixel = ivec2(floor(pixelPosition));
    return all(greaterThanEqual(basePixel, ivec2(0)))
        && all(lessThan(basePixel + ivec2(1), sizePixels));
}

struct MaskProfile {
    float coverage;
    vec3 worldDisplacement;
};

float finiteUnsignedScalar(float magnitude) {
    return isFiniteScalar(magnitude) ? clamp(magnitude, 0.0, 1.0) : 0.0;
}

vec3 finiteWorldDisplacement(vec3 value) {
    return isFiniteVec3(value) ? value : vec3(0.0);
}

// Converts local translation headroom into a no-fold return. The derivative
// is bounded by 0.8, so the plane coordinate keeps at least 20% of its
// original derivative while the requested displacement returns to zero.
float safeTranslationWeight(float availableSteps) {
    if (!isFiniteScalar(availableSteps) || availableSteps <= 0.0) return 0.0;
    if (availableSteps >= SAFE_RETURN_FULL_STEPS) return 1.0;

    float result;
    if (availableSteps < SAFE_RETURN_RAMP_STEPS) {
        result = 0.5 * SAFE_RETURN_MAX_SLOPE
                * availableSteps * availableSteps / SAFE_RETURN_RAMP_STEPS;
    } else if (availableSteps
            <= SAFE_RETURN_FULL_STEPS - SAFE_RETURN_RAMP_STEPS) {
        result = SAFE_RETURN_MAX_SLOPE
                * (availableSteps - 0.5 * SAFE_RETURN_RAMP_STEPS);
    } else {
        float remaining = SAFE_RETURN_FULL_STEPS - availableSteps;
        result = 1.0 - 0.5 * SAFE_RETURN_MAX_SLOPE
                * remaining * remaining / SAFE_RETURN_RAMP_STEPS;
    }
    return clamp(result, 0.0, 1.0);
}

vec2 selectedAffineOffset(int selectedPlane, float halfSign) {
    vec4 offsets = Offset0;
    if (selectedPlane == 1) offsets = Offset1;
    else if (selectedPlane == 2) offsets = Offset2;
    else if (selectedPlane == 3) offsets = Offset3;
    else if (selectedPlane == 4) offsets = Offset4;
    return halfSign > 0.0 ? offsets.xy : offsets.zw;
}

vec4 selectedSourcePlane(int selectedPlane) {
    vec4 plane = Plane0;
    if (selectedPlane == 1) plane = Plane1;
    else if (selectedPlane == 2) plane = Plane2;
    else if (selectedPlane == 3) plane = Plane3;
    else if (selectedPlane == 4) plane = Plane4;
    return plane;
}

float affineAxisAvailableSteps(
        float destination,
        float offset,
        float lower,
        float upper
) {
    if (!isFiniteScalar(destination) || !isFiniteScalar(offset)
            || !isFiniteScalar(lower) || !isFiniteScalar(upper)
            || destination <= lower || destination >= upper) {
        return 0.0;
    }
    if (abs(offset) <= DIRECTION_EPSILON) return AVAILABLE_STEPS_INFINITY;
    float steps = offset > 0.0
            ? (upper - destination) / offset
            : (lower - destination) / offset;
    return isFiniteScalar(steps) && steps > 0.0
            ? min(steps, AVAILABLE_STEPS_INFINITY) : 0.0;
}

float smoothMinimumAvailableSteps(float first, float second) {
    if (!isFiniteScalar(first)) return isFiniteScalar(second) ? second : 0.0;
    if (!isFiniteScalar(second)) return first;
    if (first >= AVAILABLE_STEPS_INFINITY * 0.5) return second;
    if (second >= AVAILABLE_STEPS_INFINITY * 0.5) return first;
    float width = AVAILABLE_STEPS_SMOOTH_MIN_WIDTH;
    float h = clamp(0.5 + 0.5 * (second - first) / width, 0.0, 1.0);
    return mix(second, first, h) - width * h * (1.0 - h);
}

float affineViewportAvailableSteps(
        vec2 destinationUv,
        vec2 offsetUv,
        ivec2 sizePixels
) {
    if (any(lessThan(sizePixels, ivec2(2)))
            || !isFiniteVec2(destinationUv) || !isFiniteVec2(offsetUv)) {
        return 0.0;
    }
    vec2 margin = vec2(VIEWPORT_MARGIN_TEXELS) / vec2(sizePixels);
    float horizontal = affineAxisAvailableSteps(
            destinationUv.x, offsetUv.x, margin.x, 1.0 - margin.x);
    float vertical = affineAxisAvailableSteps(
            destinationUv.y, offsetUv.y, margin.y, 1.0 - margin.y);
    return smoothMinimumAvailableSteps(horizontal, vertical);
}

// Alpha and RGB are reconstructed from the same texels before bilinear mixing.
// RGB is an unsigned world-space displacement; alpha stores the physical half.
MaskProfile unsignedMaskProfile(sampler2D mask, vec2 uv) {
    ivec2 sizePixels = textureSize(mask, 0);
    if (any(lessThan(sizePixels, ivec2(1)))) {
        return MaskProfile(0.0, vec3(0.0));
    }
    vec2 pixelPosition = uv * vec2(sizePixels) - vec2(0.5);
    ivec2 lower = clamp(ivec2(floor(pixelPosition)), ivec2(0), sizePixels - ivec2(1));
    ivec2 right = clamp(lower + ivec2(1, 0), ivec2(0), sizePixels - ivec2(1));
    ivec2 upper = clamp(lower + ivec2(0, 1), ivec2(0), sizePixels - ivec2(1));
    ivec2 upperRight = clamp(lower + ivec2(1, 1), ivec2(0), sizePixels - ivec2(1));
    vec2 fraction = fract(pixelPosition);
    vec4 lowerSample = texelFetch(mask, lower, 0);
    vec4 rightSample = texelFetch(mask, right, 0);
    vec4 upperSample = texelFetch(mask, upper, 0);
    vec4 upperRightSample = texelFetch(mask, upperRight, 0);
    float lowerLeft = finiteUnsignedScalar(abs(lowerSample.a));
    float lowerRight = finiteUnsignedScalar(abs(rightSample.a));
    float upperLeft = finiteUnsignedScalar(abs(upperSample.a));
    float upperRightAlpha = finiteUnsignedScalar(abs(upperRightSample.a));
    float lowerCoverage = mix(lowerLeft, lowerRight, fraction.x);
    float upperCoverage = mix(upperLeft, upperRightAlpha, fraction.x);
    vec3 lowerDisplacement = mix(finiteWorldDisplacement(lowerSample.rgb),
            finiteWorldDisplacement(rightSample.rgb), fraction.x);
    vec3 upperDisplacement = mix(finiteWorldDisplacement(upperSample.rgb),
            finiteWorldDisplacement(upperRightSample.rgb), fraction.x);
    return MaskProfile(mix(lowerCoverage, upperCoverage, fraction.y),
            mix(lowerDisplacement, upperDisplacement, fraction.y));
}

float nearestSignedMaskHalf(sampler2D mask, vec2 uv) {
    ivec2 sizePixels = textureSize(mask, 0);
    if (any(lessThan(sizePixels, ivec2(1)))) return 0.0;
    ivec2 pixel = clamp(ivec2(floor(uv * vec2(sizePixels))),
            ivec2(0), sizePixels - ivec2(1));
    float alpha = texelFetch(mask, pixel, 0).a;
    if (!isFiniteScalar(alpha) || abs(alpha) <= 1.0e-4) return 0.0;
    return alpha < 0.0 ? -1.0 : 1.0;
}

bool reconstructViewPositionAtUv(vec2 uv, float depth, out vec3 viewPosition) {
    vec2 clipXY = uv * 2.0 - 1.0;
    float clipZ = Params.y > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 viewH = InverseProjection * vec4(clipXY, clipZ, 1.0);
    if (!isFiniteVec4(viewH) || abs(viewH.w) <= 1.0e-6) return false;
    viewPosition = viewH.xyz / viewH.w;
    return isFiniteVec3(viewPosition);
}

bool reconstructViewPosition(ivec2 pixel, float depth, ivec2 sizePixels,
        out vec3 viewPosition) {
    vec2 uv = (vec2(pixel) + vec2(0.5)) / vec2(sizePixels);
    return reconstructViewPositionAtUv(uv, depth, viewPosition);
}

bool viewRayDirectionAtUv(vec2 uv, out vec3 rayDirection) {
    vec2 clipXY = uv * 2.0 - 1.0;
    float farClipZ = Params.y > 0.5 ? 0.0 : -1.0;
    vec4 farH = InverseProjection * vec4(clipXY, farClipZ, 1.0);
    if (!isFiniteVec4(farH)) return false;
    rayDirection = abs(farH.w) > 1.0e-6 ? farH.xyz / farH.w : farH.xyz;
    float rayLength = length(rayDirection);
    if (!isFiniteVec3(rayDirection) || !isFiniteScalar(rayLength)
            || rayLength <= 1.0e-6) return false;
    rayDirection /= rayLength;
    return true;
}

bool clearDepthRayIsBehindPlane(ivec2 pixel, ivec2 sizePixels, vec4 plane) {
    vec2 uv = (vec2(pixel) + vec2(0.5)) / vec2(sizePixels);
    vec3 rayDirection;
    return viewRayDirectionAtUv(uv, rayDirection)
            && dot(plane.xyz, rayDirection) > RAY_PARALLEL_EPSILON;
}

bool sourceTexelIsBehindPlane(ivec2 pixel, ivec2 sizePixels, vec4 plane) {
    float sourceDepth = texelFetch(Sampler3, pixel, 0).r;
    if (!isFiniteDepth(sourceDepth)) return false;
    if (sourceDepth <= DEPTH_EPSILON) {
        return clearDepthRayIsBehindPlane(pixel, sizePixels, plane);
    }
    vec3 viewPosition;
    if (!reconstructViewPosition(pixel, sourceDepth, sizePixels, viewPosition)) {
        return false;
    }
    return dot(plane, vec4(viewPosition, 1.0)) >= -PLANE_EPSILON;
}

struct SafeSceneSample {
    vec4 color;
    float validWeight;
};

SafeSceneSample sampleSceneBehindOnly(vec2 sourceUv, vec4 sourcePlane) {
    ivec2 sizePixels = textureSize(Sampler0, 0);
    ivec2 basePixel;
    if (!footprintInside(sourceUv, sizePixels, basePixel)) {
        return SafeSceneSample(vec4(0.0), -1.0);
    }

    vec2 pixelPosition = sourceUv * vec2(sizePixels) - vec2(0.5);
    vec2 fraction = fract(pixelPosition);
    ivec2 pixels[4] = ivec2[4](
            basePixel,
            basePixel + ivec2(1, 0),
            basePixel + ivec2(0, 1),
            basePixel + ivec2(1, 1));
    float weights[4] = float[4](
            (1.0 - fraction.x) * (1.0 - fraction.y),
            fraction.x * (1.0 - fraction.y),
            (1.0 - fraction.x) * fraction.y,
            fraction.x * fraction.y);

    vec4 accumulated = vec4(0.0);
    float validWeight = 0.0;
    for (int i = 0; i < 4; i++) {
        if (sourceTexelIsBehindPlane(pixels[i], sizePixels, sourcePlane)) {
            accumulated += texelFetch(
                    Sampler0, pixels[i], 0) * weights[i];
            validWeight += weights[i];
        }
    }
    if (!isFiniteScalar(validWeight)
            || validWeight < MIN_SOURCE_VALID_WEIGHT) {
        return SafeSceneSample(vec4(0.0), validWeight);
    }
    return SafeSceneSample(accumulated / validWeight, validWeight);
}

// Alpha-cutout holes have clear depth just like open sky. Only borrow a nearby
// surface when finite geometry brackets the hole along at least one axis. This
// proxy is used only for destination occlusion classification, never mapping.
float resolveDestinationDepth(vec2 uv, float centerDepth) {
    if (centerDepth > DEPTH_EPSILON) return centerDepth;
    ivec2 sizePixels = textureSize(Sampler3, 0);
    if (any(lessThan(sizePixels, ivec2(1)))) return centerDepth;
    ivec2 centerPixel = clamp(ivec2(floor(uv * vec2(sizePixels))),
            ivec2(0), sizePixels - ivec2(1));
    bool hasLeft = false;
    bool hasRight = false;
    bool hasUp = false;
    bool hasDown = false;
    float nearestDistanceSquared = 1.0e30;
    float nearestSurfaceDepth = centerDepth;
    for (int y = -ALPHA_CUTOUT_SEARCH_RADIUS;
            y <= ALPHA_CUTOUT_SEARCH_RADIUS; y++) {
        for (int x = -ALPHA_CUTOUT_SEARCH_RADIUS;
                x <= ALPHA_CUTOUT_SEARCH_RADIUS; x++) {
            if (x == 0 && y == 0) continue;
            ivec2 samplePixel = centerPixel + ivec2(x, y);
            if (any(lessThan(samplePixel, ivec2(0)))
                    || any(greaterThanEqual(samplePixel, sizePixels))) continue;
            float sampleDepth = texelFetch(Sampler3, samplePixel, 0).r;
            if (!isFiniteDepth(sampleDepth) || sampleDepth <= DEPTH_EPSILON) continue;
            hasLeft = hasLeft || x < 0;
            hasRight = hasRight || x > 0;
            hasUp = hasUp || y > 0;
            hasDown = hasDown || y < 0;
            float distanceSquared = float(x * x + y * y);
            if (distanceSquared < nearestDistanceSquared
                    || (distanceSquared == nearestDistanceSquared
                    && sampleDepth > nearestSurfaceDepth)) {
                nearestDistanceSquared = distanceSquared;
                nearestSurfaceDepth = sampleDepth;
            }
        }
    }
    bool bracketedHorizontally = hasLeft && hasRight;
    bool bracketedVertically = hasUp && hasDown;
    if (!bracketedHorizontally && !bracketedVertically) return centerDepth;
    return nearestSurfaceDepth;
}

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    float sceneDepth = texture(Sampler3, texCoord).r;
    if (!isFiniteDepth(sceneDepth)) {
        fragColor = scene;
        return;
    }

    int selectedCount = int(clamp(Params.x, 0.0, 5.0) + 0.5);
    int selectedPlane = -1;
    float selectedDepth = -1.0;
    if (selectedCount > 0) {
        float candidateDepth = texture(Sampler4, texCoord).r;
        if (isValidCutDepth(candidateDepth)
                && !isInFrontOfCut(sceneDepth, candidateDepth)) {
            selectedPlane = 0;
            selectedDepth = candidateDepth;
        }
    }
    if (selectedCount > 1) {
        float candidateDepth = texture(Sampler5, texCoord).r;
        if (isValidCutDepth(candidateDepth)
                && !isInFrontOfCut(sceneDepth, candidateDepth)
                && candidateDepth > selectedDepth) {
            selectedPlane = 1;
            selectedDepth = candidateDepth;
        }
    }
    if (selectedCount > 2) {
        float candidateDepth = texture(Sampler6, texCoord).r;
        if (isValidCutDepth(candidateDepth)
                && !isInFrontOfCut(sceneDepth, candidateDepth)
                && candidateDepth > selectedDepth) {
            selectedPlane = 2;
            selectedDepth = candidateDepth;
        }
    }
    if (selectedCount > 3) {
        float candidateDepth = texture(Sampler7, texCoord).r;
        if (isValidCutDepth(candidateDepth)
                && !isInFrontOfCut(sceneDepth, candidateDepth)
                && candidateDepth > selectedDepth) {
            selectedPlane = 3;
            selectedDepth = candidateDepth;
        }
    }
    if (selectedCount > 4) {
        float candidateDepth = texture(Sampler8, texCoord).r;
        if (isValidCutDepth(candidateDepth)
                && !isInFrontOfCut(sceneDepth, candidateDepth)
                && candidateDepth > selectedDepth) {
            selectedPlane = 4;
            selectedDepth = candidateDepth;
        }
    }
    if (selectedPlane < 0) {
        if (Params.z > 0.5) {
            MaskProfile debugMask0 = unsignedMaskProfile(Sampler9, texCoord);
            MaskProfile debugMask1 = unsignedMaskProfile(Sampler10, texCoord);
            MaskProfile debugMask2 = unsignedMaskProfile(Sampler11, texCoord);
            MaskProfile debugMask3 = unsignedMaskProfile(Sampler12, texCoord);
            MaskProfile debugMask4 = unsignedMaskProfile(Sampler13, texCoord);
            if (debugMask0.coverage > 0.0001 || debugMask1.coverage > 0.0001
                    || debugMask2.coverage > 0.0001 || debugMask3.coverage > 0.0001
                    || debugMask4.coverage > 0.0001) {
                fragColor = diagnosticFallback(scene, DEBUG_SELECTED_DEPTH_INVALID);
                return;
            }
        }
        fragColor = scene;
        return;
    }

    MaskProfile maskProfile = unsignedMaskProfile(Sampler9, texCoord);
    if (selectedPlane == 1) maskProfile = unsignedMaskProfile(Sampler10, texCoord);
    else if (selectedPlane == 2) maskProfile = unsignedMaskProfile(Sampler11, texCoord);
    else if (selectedPlane == 3) maskProfile = unsignedMaskProfile(Sampler12, texCoord);
    else if (selectedPlane == 4) maskProfile = unsignedMaskProfile(Sampler13, texCoord);
    float unsignedCoverage = maskProfile.coverage;
    float halfSign = nearestSignedMaskHalf(Sampler9, texCoord);
    if (selectedPlane == 1) halfSign = nearestSignedMaskHalf(Sampler10, texCoord);
    else if (selectedPlane == 2) halfSign = nearestSignedMaskHalf(Sampler11, texCoord);
    else if (selectedPlane == 3) halfSign = nearestSignedMaskHalf(Sampler12, texCoord);
    else if (selectedPlane == 4) halfSign = nearestSignedMaskHalf(Sampler13, texCoord);
    if (!isFiniteScalar(unsignedCoverage)
            || unsignedCoverage <= 0.0001
            || halfSign == 0.0) {
        fragColor = diagnosticFallback(scene, DEBUG_MASK_MISSING);
        return;
    }

    vec4 sourcePlane = selectedSourcePlane(selectedPlane);
    if (!isFiniteVec4(sourcePlane)) {
        fragColor = diagnosticFallback(scene, DEBUG_SOURCE_PLANE_REJECTED);
        return;
    }

    // Destination depth only classifies occlusion. It never changes the
    // cut-wide affine source offset selected for this physical half.
    float classificationDepth = resolveDestinationDepth(texCoord, sceneDepth);
    if (classificationDepth > DEPTH_EPSILON
            && isInFrontOfCut(classificationDepth, selectedDepth)) {
        fragColor = diagnosticFallback(scene, DEBUG_DESTINATION_IN_FRONT);
        return;
    }

    vec2 affineOffset = selectedAffineOffset(selectedPlane, halfSign);
    if (!isFiniteVec2(affineOffset)
            || length(affineOffset) <= DIRECTION_EPSILON) {
        fragColor = diagnosticFallback(scene, DEBUG_AFFINE_OFFSET_INVALID);
        return;
    }

    // The offset is constant over one half. Only its mask coverage and the
    // no-fold viewport return vary per pixel, so the rigid core has J = I.
    ivec2 sceneSize = textureSize(Sampler0, 0);
    float availableSteps = affineViewportAvailableSteps(
            texCoord, affineOffset, sceneSize);
    float viewportReturn = safeTranslationWeight(availableSteps);
    float mappingStrength = unsignedCoverage * viewportReturn;
    if (!isFiniteScalar(mappingStrength) || mappingStrength <= 0.0001) {
        fragColor = diagnosticFallback(scene, DEBUG_SOURCE_UV_OUTSIDE);
        return;
    }

    vec2 shiftedUv = texCoord + affineOffset * mappingStrength;
    SafeSceneSample safeSample = sampleSceneBehindOnly(shiftedUv, sourcePlane);
    if (safeSample.validWeight < 0.0) {
        fragColor = diagnosticFallback(scene, DEBUG_SOURCE_UV_OUTSIDE);
        return;
    }
    if (safeSample.validWeight < MIN_SOURCE_VALID_WEIGHT) {
        fragColor = diagnosticFallback(scene, DEBUG_SOURCE_PLANE_REJECTED);
        return;
    }

    if (Params.z > 0.5 && viewportReturn < 0.9999) {
        fragColor = diagnosticFallback(scene, DEBUG_SOURCE_UV_OUTSIDE);
        return;
    }
    fragColor = diagnosticSuccess(safeSample.color);
}
