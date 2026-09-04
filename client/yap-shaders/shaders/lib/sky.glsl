#ifndef YAP_SKY_GLSL
#define YAP_SKY_GLSL

#include "/lib/clouds.glsl"

vec3 yapAtmosphere(vec3 viewDir, vec3 sunDir, vec3 baseSky, vec3 baseFog) {
    float up = clamp(viewDir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenith = mix(baseFog, baseSky, 0.65) * vec3(0.55, 0.72, 1.05);
    vec3 horizon = mix(baseFog, vec3(1.0, 0.78, 0.55), 0.35);
    vec3 col = mix(horizon, zenith, pow(up, 0.85));

#ifdef SUN_GLOW
    float sun = max(dot(normalize(viewDir), normalize(sunDir)), 0.0);
    col += vec3(1.0, 0.92, 0.75) * pow(sun, 256.0) * 2.2;
    col += vec3(1.0, 0.75, 0.45) * pow(sun, 16.0) * 0.35;
#endif

#ifdef HORIZON_HAZE
    float haze = exp(-abs(viewDir.y) * 4.0);
    col = mix(col, baseFog * 1.1, haze * 0.45);
#endif

    return col;
}

vec3 yapSkyReflectionFallback(vec3 R, vec3 sunDir, vec3 baseSky, vec3 baseFog) {
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * R);
    vec3 col = yapAtmosphere(dir, sunDir, baseSky, baseFog);
    return yapSkyWithClouds(dir, sunDir, col, rainStrength);
}

#endif
