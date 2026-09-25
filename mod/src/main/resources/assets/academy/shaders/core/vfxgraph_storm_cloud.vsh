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
layout(location = 22) out float pSeed;
layout(location = 21) out float pAge;
void main() {
    vec4 center = View * vec4(InstancePos, 1.0);
    center.xy += (Position.xy * 2.0 - 1.0) * InstanceSize;
    gl_Position = Projection * center;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    pSeed = InstanceRot;
    pAge = InstanceAge * 100.0;
}
