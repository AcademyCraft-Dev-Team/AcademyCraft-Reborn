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
layout(location = 2) out float cloudTime;
layout(location = 3) out float cloudPhase;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    // Mildly flattened rolling puffs, never thin streaks or ribbon geometry.
    vec3 tangent = mat3(View) * InstanceVel;
    float angle = atan(tangent.y, tangent.x);
    float c = cos(angle), s = sin(angle);
    off *= vec2(1.28, 0.88);
    viewPos.xy += vec2(off.x * c - off.y * s, off.x * s + off.y * c) * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    cloudTime = InstanceAge;
    cloudPhase = InstanceRot;
}
