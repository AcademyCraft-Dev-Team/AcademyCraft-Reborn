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
layout(location = 8) out float dustTime;
layout(location = 7) out float dustSeed;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    float angle = InstanceRot + InstanceSeed * 0.41;
    float c = cos(angle);
    float s = sin(angle);
    vec2 rotated = vec2(off.x * c - off.y * s, off.x * s + off.y * c);

    vec2 velocity = (View * vec4(InstanceVel, 0.0)).xy;
    float speed = length(velocity);
    if (speed > 1e-4) {
        vec2 direction = velocity / speed;
        rotated += direction * off.y * clamp(speed * 0.0025, 0.0, 0.58);
    }
    viewPos.xy += rotated * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    dustTime = InstanceAge;
    dustSeed = fract(sin(InstanceSeed * 73.17) * 43758.5453);
}
