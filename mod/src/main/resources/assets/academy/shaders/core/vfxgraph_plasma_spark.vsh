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

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    vec2 velocity = (View * vec4(InstanceVel, 0.0)).xy;
    float speed = length(velocity);
    vec2 tangent = speed > 0.001 ? velocity / speed : vec2(0.0, 1.0);
    vec2 normal = vec2(-tangent.y, tangent.x);
    viewPos.xy += (tangent * off.y * (2.0 + min(speed * 0.18, 3.0)) + normal * off.x * 0.55) * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
