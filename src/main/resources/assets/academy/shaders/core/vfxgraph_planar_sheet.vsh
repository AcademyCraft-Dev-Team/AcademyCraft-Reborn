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
out float sheetAge;

void main() {
    float halfLength = max(length(InstanceVel), 0.00001);
    vec3 normal = InstanceVel / halfLength;
    vec3 reference = abs(normal.y) > 0.95 ? vec3(1, 0, 0) : vec3(0, 1, 0);
    vec3 tangent = normalize(cross(reference, normal));
    vec3 up = cross(normal, tangent);
    vec3 right = tangent * cos(InstanceRot) + up * sin(InstanceRot);
    vec3 along = cross(normal, right);
    vec2 offset = Position.xy * 2.0 - 1.0;
    vec3 world = InstancePos + right * offset.x * halfLength * InstanceSize + along * offset.y * halfLength;
    gl_Position = Projection * View * vec4(world, 1.0);
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    sheetAge = InstanceAge;
}
