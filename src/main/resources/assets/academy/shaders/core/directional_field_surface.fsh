#version 330
#moj_import <academy:directional_field.glsl>
uniform sampler2D SceneDepth;
uniform sampler2D TransparentDepth;
uniform sampler2D TransparentMask;
uniform sampler2D EntityMask;
uniform sampler2D SelectedMask;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    float depth = texture(SceneDepth, texCoord).r;
    float surface = depth > 1e-7 ? unionAt(reconstruct(texCoord, depth)) : 0.0;
    float entity = texture(EntityMask, texCoord).a;
    float selected = texture(SelectedMask, texCoord).a;
    // Entity membership comes from its center; its actual rendered material supplies coverage.
    surface = surface * (1.0 - entity) + selected;
    float transparent = texture(TransparentMask, texCoord).a;
    float transparentDepth = texture(TransparentDepth, texCoord).r;
    float front = transparentDepth > 1e-7 ? unionAt(reconstruct(texCoord, transparentDepth)) : 0.0;
    surface = mix(surface, front, transparent);
    float illumination = 0.78 + SunLight.w * 0.22;
    surface *= illumination * (0.94 + 0.06 * sin(FieldParams.x * 1.7));
    fragColor = vec4(clamp(surface, 0.0, 1.0), 0.0, 0.0, 1.0);
}
