#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec3 Position;
layout(location = 0) out vec2 texCoord;

void main(){
    gl_Position = vec4(Position.x, Position.y, 0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}