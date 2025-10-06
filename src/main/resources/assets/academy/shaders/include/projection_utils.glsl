#version 330

/**
 * Reconstructs the world-space position of a pixel.
 *
 * @param texCoord The 2D texture coordinate of the pixel on the screen, in the range [0.0, 1.0].
 * @param depth The depth value for the given texCoord, sampled from a depth buffer, in the range [0.0, 1.0].
 * @param inverseProjectionMatrix The inverse of the camera's projection matrix (e.g., inverse(ProjMat)).
 * @param modelViewMatrix The camera's model-view matrix (ModelViewMat).
 * @param cameraPosition The camera's position in world space (CameraPosition).
 * @return The calculated 3D position in world space.
 */
vec3 reconstructWorldPos(
    vec2 texCoord,
    float depth,
    mat4 inverseProjectionMatrix,
    mat4 modelViewMatrix,
    vec3 cameraPosition
) {
    // Transform coordinates from UV space to Normalized Device Coordinates (NDC) space [-1, 1].
    // The Z component is the depth value, also mapped to the [-1, 1] range.
    vec3 ndc = vec3(texCoord.x * 2.0 - 1.0, texCoord.y * 2.0 - 1.0, depth * 2.0 - 1.0);

    // Unproject from NDC space back to View (or Eye) space by multiplying with the inverse projection matrix.
    vec4 viewPosHomogeneous = inverseProjectionMatrix * vec4(ndc, 1.0);

    // Perform the perspective divide to get the 3D coordinates in View space.
    vec3 viewPos = viewPosHomogeneous.xyz / viewPosHomogeneous.w;

    // Transform from View space back to World space by multiplying with the inverse model-view matrix.
    // The original code from the mod added CameraPosition at the end. We replicate this logic here.
    // For some rendering pipelines, this might be redundant, but it ensures compatibility with the original shader's behavior.
    return (inverse(modelViewMatrix) * vec4(viewPos, 1.0)).xyz + cameraPosition;
}

/**
 * Generates a pseudo-random float value from a 2D vector.
 *
 * @param p The input 2D vector.
 * @return A pseudo-random float in the range [0.0, 1.0].
 */
float rand2D(vec2 p) {
    // Use the fractional part of the sine of a dot product to generate a chaotic, pseudo-random value.
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

/**
 * Rotates a 2D vector by a given angle.
 *
 * @param v The 2D vector to rotate.
 * @param angle The angle of rotation in radians.
 * @return The rotated 2D vector.
 */
vec2 rotate2D(vec2 v, float angle) {
    // Create a 2x2 rotation matrix and multiply it with the vector.
    mat2 m = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    return m * v;
}

/**
 * Remaps a value from one range to another.
 *
 * @param value The input value to remap.
 * @param inMin The lower bound of the input range.
 * @param inMax The upper bound of the input range.
 * @param outMin The lower bound of the output range.
 * @param outMax The upper bound of the output range.
 * @return The remapped value.
 */
float remap(float value, float inMin, float inMax, float outMin, float outMax) {
    // Linearly interpolate the value from the input range to the output range.
    return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
}

/**
 * Calculates the Signed Distance Function (SDF) for a 2D circle.
 *
 * @param p The sample point, relative to the circle's center.
 * @param radius The radius of the circle.
 * @return The signed distance from the point to the circle's edge.
 */
float sdCircle(vec2 p, float radius) {
    // The distance is the length of the point vector minus the radius.
    return length(p) - radius;
}