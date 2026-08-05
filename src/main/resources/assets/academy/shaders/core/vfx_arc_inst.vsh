#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec3 Corner0Pos;
in vec2 Corner0Uv;
in vec3 Corner0Color;
in vec3 Corner1Pos;
in vec2 Corner1Uv;
in vec3 Corner1Color;
in vec3 Corner2Pos;
in vec2 Corner2Uv;
in vec3 Corner2Color;
in vec3 Corner3Pos;
in vec2 Corner3Uv;
in vec3 Corner3Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    int c = gl_VertexID;
    vec3 p = c == 0 ? Corner0Pos : (c == 1 ? Corner1Pos : (c == 2 ? Corner2Pos : Corner3Pos));
    vec2 uv = c == 0 ? Corner0Uv : (c == 1 ? Corner1Uv : (c == 2 ? Corner2Uv : Corner3Uv));
    vec3 col = c == 0 ? Corner0Color : (c == 1 ? Corner1Color : (c == 2 ? Corner2Color : Corner3Color));
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    texCoord0 = uv;
    vertexColor = vec4(col, 1.0);
}
