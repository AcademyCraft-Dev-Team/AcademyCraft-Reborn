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
layout(location = 24) out float plasmaTime;
layout(location = 23) out float plasmaSeed;

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    float c = cos(InstanceRot);
    float s = sin(InstanceRot);
    vec2 rotated = vec2(off.x * c - off.y * s, off.x * s + off.y * c);
    plasmaTime = InstanceAge;
    plasmaSeed = hash1(InstanceSeed * 0.37 + 4.1);
    float breath = 1.0 + sin(plasmaTime * 0.82) * 0.014;
    viewPos.xy += rotated * InstanceSize * breath;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
