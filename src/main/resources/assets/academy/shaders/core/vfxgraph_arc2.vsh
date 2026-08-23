#version 330

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

in vec3 Position;
in vec3 Normal;
in vec2 UV0;
in vec4 Color;

out vec3 vNormal;
out vec2 texCoord0;
out vec4 vColor;
out vec3 vViewDir;

void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    vNormal = Normal;
    texCoord0 = UV0;
    vColor = Color;
    vViewDir = normalize(-Position);
}