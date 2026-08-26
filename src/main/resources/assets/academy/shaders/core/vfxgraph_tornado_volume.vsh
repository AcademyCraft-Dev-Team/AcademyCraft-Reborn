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
out float windTime;
out float windSeed;

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
