#version 330

in vec3 Position;
in vec4 InstanceColor;

out vec4 vertexColor;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
    vertexColor = InstanceColor;
}
