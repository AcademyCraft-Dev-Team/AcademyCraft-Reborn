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

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    // 中性相机面向 quad：仅绕视图轴旋转，无速度拉伸（火舌/烟团由各自专用 vsh 实现）
    float c = cos(InstanceRot);
    float s = sin(InstanceRot);
    vec2 rot = vec2(off.x * c - off.y * s, off.x * s + off.y * c);
    viewPos.xy += rot * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
