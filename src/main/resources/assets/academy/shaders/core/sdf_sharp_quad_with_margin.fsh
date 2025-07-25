#version 150

in vec2 texCoord0;

uniform vec2 u_size;
uniform vec4 u_margins;
uniform vec4 u_fillColor;

out vec4 fragColor;

void main() {
    if (u_size.x <= 0.0 || u_size.y <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec4 feather_uv = vec4(
    u_margins.x / u_size.x,
    u_margins.y / u_size.y,
    u_margins.z / u_size.x,
    u_margins.w / u_size.y
    );

    float alphaL = smoothstep(0.0, feather_uv.x, texCoord0.x);
    float alphaT = smoothstep(0.0, feather_uv.y, texCoord0.y);
    float alphaR = smoothstep(1.0, 1.0 - feather_uv.z, texCoord0.x);
    float alphaB = smoothstep(1.0, 1.0 - feather_uv.w, texCoord0.y);

    float alpha = min(min(alphaL, alphaT), min(alphaR, alphaB));

    fragColor = vec4(u_fillColor.rgb, u_fillColor.a * alpha);
}