#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec3 viewPosition;

out vec4 fragColor;

mat2 rotate2d(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat2(cosine, -sine, sine, cosine);
}

vec2 directionToUv(vec3 direction) {
    return vec2(
        0.5 + atan(direction.z, direction.x) / TWO_PI,
        0.5 + asin(clamp(direction.y, -1.0, 1.0)) / PI
    );
}

vec3 sampleCosmicLayer(vec2 skyUv, float layer, float time) {
    float scale = 0.85 + layer * 0.53;
    float angle = layer * 2.39996323 + time * (0.0014 + layer * 0.00011);
    vec2 drift = vec2(
        time * (0.00075 + layer * 0.00009),
        -time * (0.00042 + layer * 0.00005)
    );
    vec2 uv = rotate2d(angle) * (skyUv - 0.5) * scale + 0.5 + drift;
    return texture(Sampler1, fract(uv)).rgb;
}

void main() {
    vec4 mask = texture(Sampler0, texCoord0);
    if (mask.a <= 0.005) discard;

    float time = GameTime * 24000.0;
    vec3 direction = normalize(-viewPosition);
    vec2 skyUv = directionToUv(direction);

    vec3 cosmic = vec3(0.0);
    float totalWeight = 0.0;
    for (int i = 0; i < 6; ++i) {
        float layer = float(i);
        float weight = 1.0 / (1.0 + layer * 0.42);
        cosmic += sampleCosmicLayer(skyUv, layer, time) * weight;
        totalWeight += weight;
    }
    cosmic /= totalWeight;

    float luminance = max(cosmic.r, max(cosmic.g, cosmic.b));
    float star = smoothstep(0.32, 0.92, luminance);
    float twinkle = 0.78 + 0.22 * sin(time * 0.19 + skyUv.x * 91.0 + skyUv.y * 57.0);
    vec3 animatedTint = 0.5 + 0.5 * cos(
        time * 0.025 + skyUv.xyx * 7.0 + vec3(0.0, 2.0, 4.0)
    );
    vec3 color = vec3(0.075, 0.09, 0.16) + cosmic * 2.35;
    color += animatedTint * (0.08 + star * 0.22 * twinkle);
    color += vec3(0.48, 0.60, 1.0) * star * twinkle;
    color = clamp(color, 0.0, 1.0);

    float alpha = smoothstep(0.0, 0.65, mask.a) * (0.76 + star * 0.20);
    fragColor = vec4(color, alpha) * ColorModulator;
}
