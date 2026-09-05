#ifndef YAP_SKY_GLSL
#define YAP_SKY_GLSL

#include "/lib/clouds.glsl"

// Sun height in world space (−1..1). Drives clean twilight instead of muddy skyColor.
float yapSunElevation(vec3 sunView) {
    vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunView);
    return clamp(sunW.y, -1.0, 1.0);
}

// 0 = night, 1 = full day
float yapDayFactor(float sunY) {
    return smoothstep(-0.12, 0.28, sunY);
}

// How much of the warm sunrise band to show (peaks just after sun clears horizon)
float yapTwilightFactor(float sunY) {
    return exp(-pow((sunY - 0.02) / 0.18, 2.0)) * smoothstep(-0.25, -0.02, sunY + 0.15);
}

vec3 yapAtmosphere(vec3 viewDir, vec3 sunDir, vec3 baseSky, vec3 baseFog) {
    vec3 vd = normalize(viewDir);
    vec3 sd = normalize(sunDir);
    float sunY = yapSunElevation(sunDir);
    float day = yapDayFactor(sunY);
    float twilight = yapTwilightFactor(sunY);
    float up = clamp(vd.y * 0.5 + 0.5, 0.0, 1.0);
    float towardSun = max(dot(vd, sd), 0.0);

    // Explicit palettes — do NOT lean on vanilla fogColor (brown sludge at dawn)
    vec3 nightZenith = vec3(0.008, 0.012, 0.045);
    vec3 nightHorizon = vec3(0.015, 0.02, 0.05);

    vec3 dayZenith = mix(vec3(0.28, 0.48, 0.92), baseSky * vec3(0.7, 0.85, 1.1), 0.35);
    dayZenith = mix(vec3(0.30, 0.50, 0.95), dayZenith, 0.45);
    vec3 dayHorizon = vec3(0.62, 0.78, 0.98);

    // Dawn/dusk: cool blue away from sun, clean coral/gold toward sun only
    vec3 dawnZenith = vec3(0.12, 0.16, 0.38);
    vec3 dawnHorizonCool = vec3(0.35, 0.40, 0.62);
    vec3 dawnHorizonWarm = vec3(1.0, 0.52, 0.28);
    float sunSide = pow(towardSun, 2.4);
    vec3 dawnHorizon = mix(dawnHorizonCool, dawnHorizonWarm, sunSide * 0.85);

    vec3 zenith = mix(nightZenith, dayZenith, day);
    zenith = mix(zenith, dawnZenith, twilight * (1.0 - day * 0.65));

    vec3 horizon = mix(nightHorizon, dayHorizon, day);
    horizon = mix(horizon, dawnHorizon, twilight);

    vec3 col = mix(horizon, zenith, pow(up, 0.78));

    float rain = clamp(rainStrength, 0.0, 1.0);

    // Soft sun disc + atmospheric bloom (localized — not a brown sky flood)
#ifdef SUN_GLOW
    float sunVis = (1.0 - rain * 0.75) * smoothstep(-0.2, 0.05, sunY);
    float disc = pow(towardSun, 420.0);
    float bloom = pow(towardSun, 12.0);
    float scatter = pow(towardSun, 3.5);
    vec3 sunCore = vec3(1.0, 0.96, 0.88);
    vec3 sunBloom = mix(vec3(1.0, 0.70, 0.40), vec3(1.0, 0.88, 0.65), day);
    col += sunCore * disc * 2.8 * sunVis;
    col += sunBloom * bloom * 0.55 * sunVis;
    col += sunBloom * scatter * 0.22 * sunVis * (0.35 + 0.65 * twilight);
#endif

    // Cool atmospheric haze only (never vanilla brown fogColor)
#ifdef HORIZON_HAZE
    float haze = exp(-abs(vd.y) * 5.5);
    vec3 hazeCol = mix(vec3(0.55, 0.62, 0.78), vec3(0.75, 0.82, 0.95), day);
    hazeCol = mix(hazeCol, vec3(0.85, 0.55, 0.40), twilight * sunSide * 0.45);
    col = mix(col, hazeCol, haze * 0.28);
#endif

    // Rain: cooler/dimmer, not black
    if (rain > 0.01) {
        vec3 stormZenith = vec3(0.30, 0.34, 0.40);
        vec3 stormHorizon = vec3(0.40, 0.42, 0.44);
        vec3 storm = mix(stormHorizon, stormZenith, pow(up, 0.7));
        col = mix(col, storm, rain * 0.55);
    }

    return col;
}

vec3 yapSkyReflectionFallback(vec3 R, vec3 sunDir, vec3 baseSky, vec3 baseFog) {
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * R);
    vec3 col = yapAtmosphere(dir, sunDir, baseSky, baseFog);
    return yapSkyWithClouds(dir, sunDir, col, rainStrength);
}

#endif
