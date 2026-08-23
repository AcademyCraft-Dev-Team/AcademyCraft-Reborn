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
out float pSeed;
out float pAge;

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    // 稳定种子：来自每粒子 spawn 时的 ID（不随位置漂移，火舌特征全程稳定）
    pSeed = hash1(InstanceSeed * 0.37 + 1.7);
    pAge = InstanceAge;
    float lengthVar = mix(0.7, 1.5, hash1(pSeed * 7.3));
    float widthVar = mix(0.6, 1.4, hash1(pSeed * 3.1 + 5.7));
    // 随机倾斜：billboard 在视图平面内轻微歪斜，避免整齐竖直
    float tilt = (hash1(pSeed * 2.9 + 0.4) - 0.5) * 0.5;
    float c = cos(InstanceRot + tilt);
    float s = sin(InstanceRot + tilt);
    vec2 rot = vec2(off.x * c - off.y * s, off.x * s + off.y * c);
    viewPos.xy += rot * InstanceSize * widthVar;
    // 沿速度方向拉伸成火舌：拉伸量收敛（低速下短火舌，避免过快观感）
    vec3 velView = (View * vec4(InstanceVel, 0.0)).xyz;
    float speed = length(velView.xy);
    if (speed > 1e-4) {
        vec2 dir = velView.xy / speed;
        float stretch = clamp(speed * 0.5 + 0.15, 0.15, 1.4) * lengthVar;
        viewPos.xy += dir * off.y * InstanceSize * stretch;
    }
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
