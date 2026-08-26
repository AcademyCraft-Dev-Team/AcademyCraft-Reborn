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
    vec2 offset = Position.xy * 2.0 - 1.0;
    viewPos.xy += offset * InstanceSize;
    gl_Position = Projection * viewPos;

    // update_live 把实体的 0..3 图集帧写入 InstanceRot；旧烟雾本身不消费旋转值。
    float frame = floor(clamp(InstanceRot, 0.0, 3.0) + 0.5);
    vec2 cell = vec2(mod(frame, 2.0), floor(frame * 0.5));
    texCoord = (cell + Position.xy) * 0.5;
    vertexColor = InstanceColor;
}
