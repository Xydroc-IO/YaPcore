#version 120

#include "/lib/common.glsl"
#include "/lib/water.glsl"
#include "/lib/sky.glsl"
#include "/lib/ssr.glsl"
#include "/lib/caustics.glsl"
#include "/lib/clouds.glsl"

varying vec4 texcoord;

void main() {
    vec2 uv = texcoord.st;
    vec3 color = texture2D(colortex0, uv).rgb;
    float depth = texture2D(depthtex0, uv).r;
    vec4 mat = texture2D(colortex2, uv);
    vec4 nrm = texture2D(colortex1, uv);

#ifdef VOLUMETRIC_CLOUDS
    // Raymarch clouds over sky only. Skip distant-terrain veil (was a full cloud march per far pixel).
    if (depth >= 1.0 - 1e-5) {
        vec3 viewP = screenToView(vec3(uv, 1.0));
        vec3 worldDir = normalize(mat3(gbufferModelViewInverse) * viewP);
        color = yapSkyWithClouds(worldDir, sunPosition, color, rainStrength);
    }
#endif

    float opaqueDepth = texture2D(depthtex1, uv).r;
    // Only the water gbuffer flag — never guess from color (that painted waves onto grass/sky tint).
    bool water = mat.r > 0.5;

    // Surface treatment ABOVE-water only (underwater slab guard kept).
    // Restored: world-space wave normals + full SSR (the “real water” look).
    if (depth < 1.0 && water && isEyeInWater == 0) {
        vec3 viewP = screenToView(vec3(uv, depth));
        vec3 wpos = (gbufferModelViewInverse * vec4(viewP, 1.0)).xyz + cameraPosition;
        vec3 Nworld = yapWaterNormal(wpos.xz, frameTimeCounter, vec3(0.0, 1.0, 0.0));
        vec3 N = normalize(mat3(gbufferModelView) * Nworld);
        vec3 Ng = decodeNormal(nrm.rgb);
        // Prefer continuous wave field over block-face normals
        N = normalize(mix(Ng, N, 0.92));

        vec3 V = normalize(-viewP);
        vec3 L = normalize(sunPosition);
        if (dot(L, L) < 0.01) {
            L = normalize(moonPosition);
        }

        float thick = 0.25;
        if (opaqueDepth < 1.0) {
            thick = clamp(abs(linearizeDepth(opaqueDepth) - linearizeDepth(depth)) * far * 0.18,
                          0.02, 2.5);
        }
        float depthMix = max(clamp(mat.g, 0.0, 1.0), clamp(thick * 0.55, 0.0, 1.0));

        vec3 refractCol = color;
#ifdef REFRACTION
        vec2 distort = N.xy * (0.070 + 0.045 * WAVE_STRENGTH);
        distort.x *= aspectRatio;
        vec2 refrUV = clamp(uv + distort, vec2(0.002), vec2(0.998));
        refractCol = texture2D(colortex0, refrUV).rgb;
        refractCol = yapWaterAbsorb(refractCol, thick * 1.55);
        vec3 body = yapWaterAlbedo(depthMix, rainStrength);
        refractCol = mix(refractCol, body, clamp(0.12 + depthMix * 0.55, 0.12, 0.80));
#else
        refractCol = mix(refractCol, yapWaterAlbedo(depthMix, rainStrength), 0.45);
#endif

        float cosNV = max(dot(N, V), 0.0);
        float F = yapFresnelSchlick(cosNV, 0.028);
        F = clamp(F + 0.08 * (1.0 - cosNV) + 0.05 * rainStrength, 0.10, 0.93);

        vec3 skyFallback = yapSkyReflectionFallback(reflect(-V, N), sunPosition, skyColor, fogColor);
        vec4 ssr = yapSSR(viewP, N, colortex0, depthtex0);
        vec3 refl = mix(skyFallback, ssr.rgb, clamp(ssr.a, 0.0, 1.0) * 0.96);

        color = mix(refractCol, refl, F);
        color += yapWaterSpecular(N, V, L);

        // Soft shore foam only — no beach wash / run-up
        float foam = 1.0 - smoothstep(0.02, 0.10, thick);
        foam *= 0.22;
        color = mix(color, vec3(0.78, 0.88, 0.92), foam * 0.18 * clamp(WAVE_STRENGTH, 0.4, 1.2));

        color = applyFog(color, length(viewP) * 0.45, fogColor);
    } else if (depth < 1.0 && isEyeInWater == 1) {
        vec3 N = decodeNormal(nrm.rgb);
        vec3 viewP = screenToView(vec3(uv, depth));
        vec3 wpos = (gbufferModelViewInverse * vec4(viewP, 1.0)).xyz + cameraPosition;
        float dist = length(viewP);
        color = yapApplyCaustics(color, wpos, N, sunPosition, 0.55);
        color = yapWaterAbsorb(color, clamp(dist * 0.012, 0.25, 1.4));
        color = applyFog(color, dist * 0.85, fogColor);
    }

    if (isEyeInWater == 1) {
        color = yapUnderwaterCaustics(color, uv, frameTimeCounter);
        color = mix(color, vec3(0.03, 0.14, 0.26), 0.22);
        color *= 0.90;
    }

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, 1.0);
}
