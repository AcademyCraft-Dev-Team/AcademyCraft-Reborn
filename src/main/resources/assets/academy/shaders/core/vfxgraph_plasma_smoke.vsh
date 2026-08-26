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
out float smokeSeed;
out float smokeAge;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    float angle = InstanceRot + InstanceSeed * 0.73;
    float c = cos(angle);
    float s = sin(angle);
    vec2 rotated = vec2(off.x * c - off.y * s, off.x * s + off.y * c);
    viewPos.xy += rotated * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    smokeSeed = fract(sin(InstanceSeed * 91.7) * 43758.5453);
    smokeAge = InstanceAge;
}
