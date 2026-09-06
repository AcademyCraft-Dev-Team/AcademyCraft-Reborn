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
out float pSeed;
out float pAge;
void main() {
    vec4 center = View * vec4(InstancePos, 1.0);
    center.xy += (Position.xy * 2.0 - 1.0) * InstanceSize;
    gl_Position = Projection * center;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    pSeed = InstanceRot;
    pAge = InstanceAge * 100.0;
}
