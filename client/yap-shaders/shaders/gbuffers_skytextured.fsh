#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/sky.glsl"

void main() {
    vec4 tex = texture2D(texture, texcoord.st) * glcolor;
    // Fade sun/moon/stars with sun elevation so dawn doesn't keep a night sky
    float sunY = yapSunElevation(sunPosition);
    float day = yapDayFactor(sunY);
    // Stars die as twilight starts; sun texture can stay
    float lumaT = luma(tex.rgb);
    bool likelyStar = tex.a > 0.1 && lumaT > 0.6 && tex.r > 0.5 && abs(tex.r - tex.b) < 0.15;
    if (likelyStar) {
        float starVis = 1.0 - smoothstep(-0.08, 0.12, sunY);
        tex.rgb *= starVis;
        tex.a *= starVis;
        if (tex.a < 0.02) discard;
    } else {
        // Sun/moon discs — gentle fade at opposite times handled by vanilla alpha
        tex.rgb *= 1.05;
    }
    /* DRAWBUFFERS:012 */
    gl_FragData[0] = tex;
    gl_FragData[1] = vec4(0.5, 0.5, 1.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
