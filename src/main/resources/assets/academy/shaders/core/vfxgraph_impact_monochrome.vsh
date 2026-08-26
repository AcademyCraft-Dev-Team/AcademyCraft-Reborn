#version 330

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

in vec3 Position;
in vec3 InstancePos;
in vec3 InstanceVel;
in float InstanceSize;
in vec4 InstanceColor;
in float InstanceRot;
in float InstanceSeed;
in float InstanceAge;

out vec2 texCoord;
out vec4 vertexColor;
out float impactAge;
out float impactSeed;
flat out vec2 impactCenter;
flat out float impactVisible;

void main() {
    vec2 off = Position.xy * 2.0 - 1.0;
    vec4 impactClip = Projection * View * vec4(InstancePos, 1.0);
    float safeW = max(abs(impactClip.w), 0.00001);
    vec2 impactNdc = impactClip.xy / safeW;
    bool inFront = impactClip.w > 0.0001;
    bool insideViewport = all(lessThanEqual(abs(impactNdc), vec2(1.0)));

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
