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
    vec2 velocity = (View * vec4(InstanceVel, 0.0)).xy;
    float speed = length(velocity);
    vec2 tangent = speed > 0.001 ? velocity / speed : vec2(0.0, 1.0);
    vec2 normal = vec2(-tangent.y, tangent.x);
    viewPos.xy += (tangent * off.y * (2.0 + min(speed * 0.18, 3.0)) + normal * off.x * 0.55) * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
