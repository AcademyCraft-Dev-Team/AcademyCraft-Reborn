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
    float maximumStrength = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= int(FieldParams.y)) break;
        maximumStrength = max(maximumStrength, FieldStyles[i].x);
    }
    surface = surface * (1.0 - entity) + selected * maximumStrength;
    float transparent = texture(TransparentMask, texCoord).a;
    float transparentDepth = texture(TransparentDepth, texCoord).r;
    float front = transparentDepth > 1e-7 ? unionAt(reconstruct(texCoord, transparentDepth)) : 0.0;
    surface = mix(surface, front, transparent);
    float illumination = 0.78 + SunLight.w * 0.22;
    vec3 point = reconstruct(texCoord, depth) + NoiseAnchor.xyz;
    float featherLight = fieldNoise(vec2(point.x + point.y * 0.4, point.z) * 0.85);
    float veins = 0.70 + 0.30 * smoothstep(0.28, 0.72, featherLight);
    surface *= illumination * veins * (0.94 + 0.06 * sin(FieldParams.x * 1.7));
    fragColor = vec4(clamp(surface, 0.0, 1.0), 0.0, 0.0, 1.0);
}
