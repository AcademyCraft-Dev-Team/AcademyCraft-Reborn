#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 InstancePos;
layout(location = 2) in vec3 InstanceVel;
layout(location = 3) in float InstanceSize;
layout(location = 4) in vec4 InstanceColor;
layout(location = 5) in float InstanceRot;
layout(location = 6) in float InstanceSeed;
layout(location = 7) in float InstanceAge;

layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec4 vertexColor;
layout(location = 12) out float impactAge;
layout(location = 14) out float impactSeed;
layout(location = 13) flat out vec2 impactCenter;
layout(location = 15) flat out float impactVisible;

void main() {
    vec2 off = Position.xy * 2.0 - 1.0;
    vec4 impactClip = Projection * View * vec4(InstancePos, 1.0);
    float safeW = max(abs(impactClip.w), 0.00001);
    vec2 impactNdc = impactClip.xy / safeW;
    bool inFront = impactClip.w > 0.0001;
    // Preserve impacts that land on, or only slightly beyond, a screen edge. Their full-screen
    // procedural frame still needs to cover the view even though the physical origin is clipped.
    bool insideViewport = all(lessThanEqual(abs(impactNdc), vec2(1.18)));

    // Screen-space impact frame. Reversed-Z uses 1.0 as the nearest depth, so the frame
    // stays on top of world geometry while keeping the shared no-depth-write pipeline. The
    // frame remains full-screen, but its procedural origin comes from the world impact point.
    gl_Position = vec4(off, 1.0, 1.0);
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    impactAge = InstanceAge;
    impactSeed = InstanceSeed;
    impactCenter = impactNdc * 0.5 + 0.5;
    impactVisible = inFront && insideViewport ? 1.0 : 0.0;
}
