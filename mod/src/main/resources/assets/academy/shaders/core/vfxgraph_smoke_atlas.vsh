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
    vec2 offset = Position.xy * 2.0 - 1.0;
    viewPos.xy += offset * InstanceSize;
    gl_Position = Projection * viewPos;

    // update_live 把实体的 0..3 图集帧写入 InstanceRot；旧烟雾本身不消费旋转值。
    float frame = floor(clamp(InstanceRot, 0.0, 3.0) + 0.5);
    vec2 cell = vec2(mod(frame, 2.0), floor(frame * 0.5));
    texCoord = (cell + Position.xy) * 0.5;
    vertexColor = InstanceColor;
}
