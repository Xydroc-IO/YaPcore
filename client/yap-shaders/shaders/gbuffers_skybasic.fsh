#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/sky.glsl"

varying vec3 viewDir;

void main() {
    // Stars (and void) share this program with the sky disc. Our atmosphere
    // must NOT replace star geometry — that is why night looked starless.
#ifdef MC_RENDER_STAGE_STARS
    if (renderStage == MC_RENDER_STAGE_STARS) {
        float sunY = yapSunElevation(sunPosition);
        float night = 1.0 - yapDayFactor(sunY);
        float rainDim = 1.0 - rainStrength * 0.90;
        // glcolor carries vanilla star brightness (POSITION_COLOR)
        vec3 star = glcolor.rgb * night * rainDim;
        // Filmic final.fsh buries very dim points — mild lift for readability
        star *= 2.35;
        if (luma(star) < 0.0035) {
            discard;
        }
        /* DRAWBUFFERS:012 */
        gl_FragData[0] = vec4(star, 1.0);
        gl_FragData[1] = vec4(0.5, 0.5, 1.0, 1.0);
        gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
#endif

#ifdef MC_RENDER_STAGE_VOID
    if (renderStage == MC_RENDER_STAGE_VOID) {
        // Soft night under-horizon — avoid flat black slab
        vec3 col = vec3(0.004, 0.006, 0.018);
        /* DRAWBUFFERS:012 */
        gl_FragData[0] = vec4(col, 1.0);
        gl_FragData[1] = vec4(0.5, 0.5, 1.0, 1.0);
        gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
#endif

    vec3 dir = normalize(viewDir);
    vec3 col = yapAtmosphere(dir, sunPosition, skyColor, fogColor);
    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(col, 1.0);
    gl_FragData[1] = vec4(0.5, 0.5, 1.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
