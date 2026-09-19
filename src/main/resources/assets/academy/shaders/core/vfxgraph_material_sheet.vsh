#version 330

layout(std140) uniform GraphCamera { mat4 View; mat4 Projection; };
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
out float materialProgress;
out float materialPhase;

void main() {
    // The emitter supplies a surface normal instead of motion velocity.
    vec3 normal = normalize(InstanceVel + vec3(0.0, 0.00001, 0.0));
    vec3 right = normalize(cross(abs(normal.y) > 0.95 ? vec3(1, 0, 0) : vec3(0, 1, 0), normal));
    vec3 up = cross(normal, right);
    vec2 offset = Position.xy * 2.0 - 1.0;
    float c = cos(InstanceRot), s = sin(InstanceRot);
    offset = mat2(c, s, -s, c) * (offset * vec2(0.68, 1.0));
    vec3 world = InstancePos + (right * offset.x + up * offset.y) * InstanceSize;
    gl_Position = Projection * View * vec4(world, 1.0);
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    materialProgress = InstanceAge;
    materialPhase = InstanceRot;
}
