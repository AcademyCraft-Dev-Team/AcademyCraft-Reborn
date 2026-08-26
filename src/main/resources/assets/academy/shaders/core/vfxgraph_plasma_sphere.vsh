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
out float plasmaSeed;

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
