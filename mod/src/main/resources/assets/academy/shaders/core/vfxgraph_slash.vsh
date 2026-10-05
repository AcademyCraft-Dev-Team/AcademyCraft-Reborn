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
    float mirror = floor(InstanceRot * 0.25 + 0.5);
    float progress = clamp(InstanceRot - 4.0 * mirror, 0.0, 1.0);
    float life = sin(progress * 3.14159265);
    float frame = floor(clamp(progress * 4.0, 0.0, 3.0));
    float sizeScale = 0.92 + 0.18 * life;
    float widthGrow = 0.82 + 0.18 * min(1.0, progress * 1.7);
    float width = InstanceSize * 5.0 * sizeScale * widthGrow;
    float height = InstanceSize * 1.05 * sizeScale;
    float flip = mirror > 0.5 ? -1.0 : 1.0;
    vec3 offset = vec3((Position.x * 2.0 - 1.0) * width * 0.5,
            height * 0.5,
            (Position.y * 2.0 - 1.0) * width * 0.5 * flip);
    float theta = radians(270.0 - InstanceVel.x);
    float phi = radians(-InstanceVel.y);
    float cosp = cos(phi);
    float sinp = sin(phi);
    vec3 pitched = vec3(offset.x, offset.y * cosp - offset.z * sinp, offset.y * sinp + offset.z * cosp);
    float cost = cos(theta);
    float sint = sin(theta);
    vec3 world = vec3(InstancePos.x + pitched.x * cost + pitched.z * sint,
            InstancePos.y + pitched.y,
            InstancePos.z - pitched.x * sint + pitched.z * cost);
    gl_Position = Projection * View * vec4(world, 1.0);
    vec2 cell = vec2(mod(frame, 2.0), floor(frame * 0.5));
    texCoord = (cell + Position.xy) * 0.5;
    vertexColor = InstanceColor;
    vertexColor.a *= life * 0.92;
}
