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
out float surfaceBoundary;
out float materialPenetration;

void main() {
    float radius = max(length(InstanceVel), 0.00001);
    vec3 forward = InstanceVel / radius;
    vec3 normal = normalize(InstanceNormal);
    vec3 right = normalize(cross(normal, forward));
    float reverse = InstanceRot < 0.0 ? -1.0 : 1.0;
    float sweep = (InstanceAge - 0.5) * InstanceSize * 0.30 * reverse;
    float angle = (Position.x - 0.5) * InstanceSize + sweep;
    float radial = radius * (1.0 - Position.y * abs(InstanceRot));
    vec3 world = InstancePos + (right * sin(angle) + forward * cos(angle)) * radial
            + normal * sin(Position.x * 3.14159265) * Position.y * radius * 0.025;
    gl_Position = Projection * View * vec4(world, 1.0);
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    materialPhase = InstanceSeed * 0.6180339;
    surfaceBoundary = dot(InstanceClipPlane.xyz, world) + InstanceClipPlane.w;
    materialPenetration = InstancePenetration;
}
