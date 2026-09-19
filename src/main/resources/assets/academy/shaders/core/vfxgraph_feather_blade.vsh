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
out vec2 texCoord;
out vec4 vertexColor;
out float materialProgress;
out float materialPhase;

void main() {
    // Velocity is the longitudinal axis. Roll selects two crossed feather surfaces.
    vec3 axis = normalize(InstanceVel);
    vec3 reference = abs(axis.y) > 0.95 ? vec3(1, 0, 0) : vec3(0, 1, 0);
    vec3 side = normalize(cross(reference, axis));
    vec3 other = cross(axis, side);
    vec3 right = side * cos(InstanceRot) + other * sin(InstanceRot);
    vec2 p = Position.xy * 2.0 - 1.0;
    vec3 world = InstancePos + (right * p.x * 0.24 + axis * p.y) * InstanceSize;
    gl_Position = Projection * View * vec4(world, 1.0);
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    materialProgress = InstanceAge;
    materialPhase = InstanceRot;
}
