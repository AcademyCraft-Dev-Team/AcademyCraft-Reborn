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
layout(location = 22) out float pSeed;
layout(location = 21) out float pAge;

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    // 稳定种子/年龄：供片元烟雾卷须与消散使用
    pSeed = hash1(InstanceSeed * 0.37 + 1.7);
    pAge = InstanceAge;
    // 轻微速度拉伸：烟团上升略拉长（远弱于火焰火舌）
    vec3 velView = (View * vec4(InstanceVel, 0.0)).xyz;
    float speed = length(velView.xy);
    if (speed > 1e-4) {
        vec2 dir = velView.xy / speed;
        float stretch = clamp(speed * 0.15, 0.0, 0.5);
        viewPos.xy += dir * off.y * InstanceSize * stretch;
    }
    float c = cos(InstanceRot);
    float s = sin(InstanceRot);
    vec2 rot = vec2(off.x * c - off.y * s, off.x * s + off.y * c);
    viewPos.xy += rot * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
