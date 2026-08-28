#version 330

uniform sampler2D Sampler0;

noperspective in float maskCoverage;
out vec4 fragColor;

const float DEPTH_EPSILON = 1.0e-5;

void main() {
    if (!(abs(maskCoverage) > 0.0001 && abs(maskCoverage) <= 1.0)) discard;
    ivec2 textureSizePixels = textureSize(Sampler0, 0);
    ivec2 pixel = clamp(
            ivec2(gl_FragCoord.xy),
            ivec2(0),
            textureSizePixels - ivec2(1));
    float sceneDepth = texelFetch(Sampler0, pixel, 0).r;
    if (!(sceneDepth >= 0.0 && sceneDepth <= 1.0
            && gl_FragCoord.z >= 0.0 && gl_FragCoord.z <= 1.0)) {
        discard;
    }

    // Reversed Z: a smaller cut depth is farther away. Ignore cut fragments
    // hidden behind the current scene before the depth buffer reduces the
    // remaining overlaps to the farthest visible plane.
    if (gl_FragCoord.z + DEPTH_EPSILON < sceneDepth) discard;
    fragColor = vec4(0.0);
}
