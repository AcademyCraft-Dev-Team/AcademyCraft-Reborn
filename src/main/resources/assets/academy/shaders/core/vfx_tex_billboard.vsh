#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 InstancePos;
in float InstanceSize;
in float InstanceAlpha;
in vec4 InstanceUVRect;

out vec2 texCoord0;
out float vertexAlpha;

void main() {
    vec4 viewPos = ModelViewMat * vec4(InstancePos, 1.0);
    vec2 offset = (Position.xy * 2.0 - 1.0) * InstanceSize;
    viewPos.xy += offset;
    gl_Position = ProjMat * viewPos;
    texCoord0 = vec2(
        InstanceUVRect.x + UV0.x * (InstanceUVRect.z - InstanceUVRect.x),
        InstanceUVRect.y + UV0.y * (InstanceUVRect.w - InstanceUVRect.y)
    );
    vertexAlpha = InstanceAlpha;
}
