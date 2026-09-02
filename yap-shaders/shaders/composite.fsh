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
    // Raymarch clouds over sky (and soft-over distant fog)
    if (depth >= 1.0 - 1e-5) {
        vec3 viewP = screenToView(vec3(uv, 1.0));
        vec3 worldDir = normalize(mat3(gbufferModelViewInverse) * viewP);
        color = yapSkyWithClouds(worldDir, sunPosition, color, rainStrength);
    } else {
        // Soft veil when looking through the cloud slab toward far terrain
        vec3 viewP = screenToView(vec3(uv, depth));
        float dist = length(viewP);
        if (dist > 180.0) {
            vec3 worldDir = normalize(mat3(gbufferModelViewInverse) * viewP);
            vec4 c = yapVolumetricClouds(worldDir, sunPosition, rainStrength);
            float veil = c.a * smoothstep(180.0, 520.0, dist) * 0.55;
            color = mix(color, c.rgb, clamp(veil, 0.0, 0.75));
        }
    }
#endif

    float opaqueDepth = texture2D(depthtex1, uv).r;
    bool water = mat.r > 0.5;
    if (!water && depth < 1.0 && opaqueDepth < 1.0 && depth + 1e-4 < opaqueDepth) {
        float blueBias = color.b - max(color.r, color.g) * 0.55;
        water = blueBias > 0.035;
    }

    if (depth < 1.0 && water) {
        vec3 N = decodeNormal(nrm.xy);
        if (mat.r <= 0.5) {
            float w = yapWaveHeight(uv * 28.0, frameTimeCounter);
            N = normalize(vec3(w * 1.2, 1.0, w * 0.9));
        }

        vec3 viewP = screenToView(vec3(uv, depth));
        vec3 V = normalize(-viewP);
        vec3 L = normalize(sunPosition);

        float thick = 0.25;
        if (opaqueDepth < 1.0) {
            thick = clamp(abs(linearizeDepth(opaqueDepth) - linearizeDepth(depth)) * far * 0.18,
                          0.02, 2.5);
        }
        float depthMix = max(clamp(nrm.z, 0.0, 1.0), clamp(thick * 0.55, 0.0, 1.0));

        vec3 refractCol = color;
#ifdef REFRACTION
        // Subtle lensing — strong warp looks fake
        vec2 distort = N.xy * (0.018 + 0.012 * WAVE_STRENGTH);
        distort.x *= aspectRatio;
        distort += 0.0035 * vec2(
            sin(frameTimeCounter * 1.1 + uv.y * 18.0),
            cos(frameTimeCounter * 0.95 + uv.x * 18.0));
        vec2 refrUV = clamp(uv + distort, vec2(0.002), vec2(0.998));
        refractCol = texture2D(colortex0, refrUV).rgb;
        refractCol = yapWaterAbsorb(refractCol, thick * 1.4);
        vec3 body = yapWaterAlbedo(depthMix, rainStrength);
        refractCol = mix(refractCol, body, clamp(0.12 + depthMix * 0.55, 0.12, 0.78));
#else
        refractCol = mix(refractCol, yapWaterAlbedo(depthMix, rainStrength), 0.45);
#endif

        vec4 ssr = yapSSR(viewP, N, colortex0, depthtex0);
        vec3 skyFallback = yapSkyReflectionFallback(reflect(-V, N), sunPosition, skyColor, fogColor);
        vec3 refl = mix(skyFallback, ssr.rgb, clamp(ssr.a, 0.0, 1.0) * 0.9);

        float F = yapFresnelSchlick(max(dot(N, V), 0.0), 0.02);
        F = clamp(F + 0.04 * rainStrength, 0.05, 0.9);

        color = mix(refractCol, refl, F);
        color += yapWaterSpecular(N, V, L);

        // Shore foam only in very thin water
        float foam = 1.0 - smoothstep(0.02, 0.12, thick);
        foam *= 0.4 + 0.6 * abs(sin(uv.x * viewWidth * 0.08 + frameTimeCounter * 0.9));
        color = mix(color, vec3(0.88, 0.93, 0.96), foam * 0.35 * clamp(WAVE_STRENGTH, 0.4, 1.2));

        color = applyFog(color, length(viewP) * 0.45, fogColor);
    } else if (depth < 1.0 && isEyeInWater == 1) {
        vec3 N = decodeNormal(nrm.xy);
        vec3 viewP = screenToView(vec3(uv, depth));
        vec3 wpos = (gbufferModelViewInverse * vec4(viewP, 1.0)).xyz + cameraPosition;
        color = yapApplyCaustics(color, wpos, N, sunPosition, 0.75);
        color = yapWaterAbsorb(color, 0.55);
    }

    if (isEyeInWater == 1) {
        color = yapUnderwaterCaustics(color, uv, frameTimeCounter);
        color = mix(color, vec3(0.02, 0.12, 0.22), 0.18);
        color *= 0.94;
    }

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, 1.0);
}
