#version 330
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in vec3 surfacePosition;
in float materialProgress;
in float materialPhase;
in float materialMode;
out vec4 fragColor;

void main() {
    // The cross section is closed. Reject its inward-facing half so translucent back surfaces
    // cannot paint over the front when the shared VFX pass deliberately leaves depth unwritten.
    if (!gl_FrontFacing) discard;
    float alpha = vertexColor.a;
    float v = texCoord.y;
    float x = abs(texCoord.x * 2.0 - 1.0);
    // Delamination opens narrow slits from the tip; it never turns the body into a feather sprite.
    if (materialMode < 0.5) {
        float release = smoothstep(0.50, 0.94, materialProgress);
        float irregular = sin(v * 17.0 + materialPhase) * 0.055 + sin(v * 31.0 - materialPhase) * 0.018;
        alpha *= 1.0 - smoothstep(1.03 - release * 0.95 + irregular, 1.11 - release * 0.95 + irregular, v);
    }
    vec3 geometricNormal = normalize(cross(dFdx(surfacePosition), dFdy(surfacePosition)));
    float light = 0.90 + 0.10 * abs(dot(geometricNormal, normalize(vec3(0.3, 0.9, -0.35))));
    float margin = smoothstep(0.80, 1.0, x);
    float grain = texture(Sampler0, texCoord * 0.065 + materialPhase * 0.03).r;
    vec3 whiteMatter = vec3(0.98, 0.978, 0.967) * (light - margin * 0.025 + (grain - 0.5) * 0.012);
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    alpha *= smoothstep(0.0, 0.000008, gl_FragCoord.z - texture(Sampler1, screenUv).r);
    if (alpha < 0.003) discard;
    fragColor = vec4(vertexColor.rgb * whiteMatter, alpha);
}
