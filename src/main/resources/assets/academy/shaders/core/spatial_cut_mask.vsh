#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec3 WorldDisplacement;
in float Coverage;

out vec3 maskWorldDisplacement;
noperspective out float maskCoverage;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Store a camera-independent world vector. Projection happens later for
    // each actual background pixel rather than once at the cut-plane depth.
    maskWorldDisplacement = WorldDisplacement;
    maskCoverage = Coverage;
}
