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
layout(location = 35) out float windTime;
layout(location = 34) out float windSeed;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    float angle = InstanceRot * 0.18 + InstanceSeed * 0.37;
    float c = cos(angle);
    float s = sin(angle);
    vec2 stretched = off * vec2(1.48, 0.72);
    vec2 rotated = vec2(
            stretched.x * c - stretched.y * s,
            stretched.x * s + stretched.y * c
    );
    viewPos.xy += rotated * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    windTime = InstanceAge;
    windSeed = fract(sin(InstanceSeed * 91.731) * 43758.5453);
}
