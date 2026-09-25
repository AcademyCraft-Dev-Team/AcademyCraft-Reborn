#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 17) in vec3 maskWorldDisplacement;
layout(location = 16) noperspective in float maskCoverage;
layout(location = 0) out vec4 fragColor;

void main() {
    if (!(maskWorldDisplacement.x >= -1.0e4 && maskWorldDisplacement.x <= 1.0e4
            && maskWorldDisplacement.y >= -1.0e4 && maskWorldDisplacement.y <= 1.0e4
            && maskWorldDisplacement.z >= -1.0e4 && maskWorldDisplacement.z <= 1.0e4
            && maskCoverage >= -1.0 && maskCoverage <= 1.0)) {
        discard;
    }
    float weight = clamp(abs(maskCoverage), 0.0, 1.0);
    // Zero-vector fragments do not contribute to the visible displacement mask.
    if (weight <= 0.0001 || length(maskWorldDisplacement) <= 0.0001) discard;
    fragColor = vec4(
            maskWorldDisplacement * weight,
            sign(maskCoverage) * weight);
}
