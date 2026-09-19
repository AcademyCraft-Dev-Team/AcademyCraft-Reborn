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
out float cloudTime;
out float cloudPhase;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    // Mildly flattened rolling puffs, never thin streaks or ribbon geometry.
    vec3 tangent = mat3(View) * InstanceVel;
    float angle = atan(tangent.y, tangent.x);
    float c = cos(angle), s = sin(angle);
    off *= vec2(1.28, 0.88);
    viewPos.xy += vec2(off.x * c - off.y * s, off.x * s + off.y * c) * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    cloudTime = InstanceAge;
    cloudPhase = InstanceRot;
}
