#version 330
layout(std140) uniform GraphCamera { mat4 View; mat4 Projection; };
in vec3 Position;
in vec3 InstancePos;
in vec3 InstanceVel;
in float InstanceSize;
in vec4 InstanceColor;
in float InstanceRot;
in float InstanceSeed;
in float InstanceAge;
in vec3 InstanceNormal;
in vec4 InstanceClipPlane;
in float InstancePenetration;
out vec2 texCoord;
out vec4 vertexColor;
out float materialPhase;
out float edgeActivity;
out float surfaceBoundary;
out float materialPenetration;

void main() {
    float featherLength = max(length(InstanceVel), 0.00001);
    vec3 axis = InstanceVel / featherLength;
    vec3 side = normalize(cross(axis, InstanceNormal));
    vec3 normal = normalize(cross(side, axis));
    vec3 right = side * cos(InstanceRot) + normal * sin(InstanceRot);
    float u = Position.x * 2.0 - 1.0;
    float v = Position.y;
    // The tessellated feather is curved in depth; the spine remains joined to its sampled bone.
    float camber = sin(v * 3.14159265) * (0.055 + InstanceAge * 0.025);
    vec3 world = InstancePos + axis * v * featherLength
            + right * u * InstanceSize * featherLength * 0.5
            + normal * featherLength * (camber + u * u * 0.018);
    gl_Position = Projection * View * vec4(world, 1.0);
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    materialPhase = InstanceSeed * 0.6180339;
    edgeActivity = InstanceAge;
    surfaceBoundary = dot(InstanceClipPlane.xyz, world) + InstanceClipPlane.w;
    materialPenetration = InstancePenetration;
}
