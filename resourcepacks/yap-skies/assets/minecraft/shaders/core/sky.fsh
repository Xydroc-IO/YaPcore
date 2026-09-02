#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec3 viewDir;

out vec4 fragColor;

void main() {
    vec3 dir = normalize(viewDir);
    float up = clamp(dir.y, 0.0, 1.0);
    float down = clamp(-dir.y, 0.0, 1.0);

    vec3 base = ColorModulator.rgb;
    vec3 fog = FogColor.rgb;
    float dayness = clamp(dot(base, vec3(0.30, 0.55, 0.15)) * 2.8, 0.0, 1.0);

    vec3 zenith = mix(base, base * vec3(0.78, 0.88, 1.12), 0.55);
    float warmth = clamp((fog.r * 1.15 + fog.g * 0.55) - fog.b * 1.35, 0.0, 1.0);
    vec3 warmHaze = vec3(1.02, 0.52, 0.20);
    vec3 horizon = mix(fog, mix(fog, warmHaze, 0.50), warmth * dayness);
    float hz = exp(-up * 5.2);
    vec3 col = mix(zenith, horizon, hz);
    col = mix(col, fog, down);

    vec3 nightZenith = mix(fog, base * vec3(0.55, 0.62, 0.95), 0.40);
    col = mix(nightZenith, col, dayness);

    float grain = fract(sin(dot(dir.xz, vec2(12.9898, 78.233))) * 43758.5453);
    col += (grain - 0.5) * 0.012 * mix(0.35, 1.0, dayness);

    fragColor = apply_fog(
        vec4(col, ColorModulator.a),
        sphericalVertexDistance,
        cylindricalVertexDistance,
        0.0,
        FogSkyEnd,
        FogSkyEnd,
        FogSkyEnd,
        FogColor
    );
}
