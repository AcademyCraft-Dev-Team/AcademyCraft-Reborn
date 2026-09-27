#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform GraphCamera { mat4 View; mat4 Projection; };
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
layout(location = 2) out float materialProgress;
layout(location = 3) out float materialPhase;

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
