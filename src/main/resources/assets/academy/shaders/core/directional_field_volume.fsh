#version 330
#moj_import <academy:directional_field.glsl>
uniform sampler2D SceneDepth;
uniform sampler2D TransparentDepth;
uniform sampler2D TransparentMask;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 ray = normalize(reconstruct(texCoord, 0.5));
    float depth = texture(SceneDepth, texCoord).r;
    float limit = depth > 1e-7 ? length(reconstruct(texCoord, depth)) : 100000.0;
    float translucent = texture(TransparentMask, texCoord).a;
    float glassDepth = texture(TransparentDepth, texCoord).r;
    float glassDistance = glassDepth > 1e-7 ? length(reconstruct(texCoord, glassDepth)) : limit;
    vec3 sun = normalize(SunLight.xyz);
    vec3 tangent = normalize(cross(abs(sun.y) > 0.95 ? vec3(1, 0, 0) : vec3(0, 1, 0), sun));
    vec3 bitangent = cross(sun, tangent);
    float result = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= int(FieldParams.y)) break;
        vec3 center = FieldOrigins[i].xyz;
        float radius = FieldOrigins[i].w;
        float b = dot(ray, center);
        float discriminant = b * b - dot(center, center) + radius * radius;
        if (discriminant <= 0.0) continue;
        float root = sqrt(discriminant);
        float start = max(0.15, b - root), end = min(limit, b + root);
        if (end <= start) continue;
        float stepSize = (end - start) / 32.0;
        float density = 0.0;
        for (int s = 0; s < 32; s++) {
            float distance = start + (float(s) + 0.5) * stepSize;
            vec3 p = ray * distance;
            float coverage = fieldAt(p, i);
            if (coverage <= 0.0) continue;
            vec3 world = p + NoiseAnchor.xyz;
            vec2 band = vec2(dot(world, tangent), dot(world, bitangent));
            float shafts = fieldNoise(band * 0.7 + vec2(FieldParams.x * 0.014, 0));
            shafts = 0.12 + 0.88 * smoothstep(0.3, 0.8, shafts);
            float transmission = distance > glassDistance ? 1.0 - translucent * 0.75 : 1.0;
            density += coverage * shafts * transmission * stepSize;
        }
        float angle = 0.75 + 0.25 * pow(abs(dot(ray, sun)), 4.0);
        float amount = (1.0 - exp(-density * 0.11)) * angle * FieldDirections[i].w;
        result = max(result, amount);
    }
    fragColor = vec4(result * (0.6 + SunLight.w * 0.4), 0.0, 0.0, 1.0);
}
