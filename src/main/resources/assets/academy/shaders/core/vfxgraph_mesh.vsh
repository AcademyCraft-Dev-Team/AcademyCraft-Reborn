#version 330

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

in vec3 Position;
in vec3 InstancePos;
in float InstanceSize;
in vec4 InstanceColor;
in float InstanceRot;

out vec4 vertexColor;

void main() {
    float c = cos(InstanceRot);
    float s = sin(InstanceRot);
    vec3 local = Position - 0.5;
    vec3 rot = vec3(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    vec3 world = InstancePos + rot * InstanceSize;
    gl_Position = Projection * View * vec4(world, 1.0);
    vertexColor = InstanceColor;
}
