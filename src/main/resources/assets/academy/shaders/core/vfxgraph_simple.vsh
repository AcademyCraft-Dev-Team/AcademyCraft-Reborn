#version 330

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    vertexColor = Color;
}
