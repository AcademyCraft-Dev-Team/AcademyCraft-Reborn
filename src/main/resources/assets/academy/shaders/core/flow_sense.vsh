#version 330
layout(std140) uniform FlowView {
    mat4 ViewProjection;
    vec4 PixelSize;
};
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in float MarkerSize;
out vec2 markerUv;
out vec4 markerColor;
void main() {
    gl_Position = ViewProjection * vec4(Position, 1.0);
    gl_Position.xy += UV0 * MarkerSize * PixelSize.xy * gl_Position.w;
    markerUv = UV0;
    markerColor = Color;
}
