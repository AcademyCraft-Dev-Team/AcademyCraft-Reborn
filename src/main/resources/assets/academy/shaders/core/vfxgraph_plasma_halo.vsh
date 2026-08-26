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
out float plasmaTime;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    plasmaTime = InstanceAge * 120.0;
    float breath = 1.0 + sin(plasmaTime * 0.68) * 0.018;
    viewPos.xy += off * InstanceSize * breath;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
