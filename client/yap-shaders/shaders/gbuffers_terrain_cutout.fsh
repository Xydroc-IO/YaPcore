#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"

void main() {
    // Cut on *texture* alpha only — never multiply in glcolor.a (chunk fade / fog
    // can drag combined alpha under the threshold and erase whole canopies).
    vec4 tex = texture2D(texture, texcoord.st);
    float dist = length(viewPos);

    // Near: keep a firm cutout (avoids screen-door). Far: leaf faces shrink to a
    // few pixels and bilinear edges sit in the mid-alphas — a fixed 0.5 test
    // discards the entire canopy while solid trunks still draw.
    float cut = mix(0.42, 0.06, smoothstep(10.0, 110.0, dist));
    if (tex.a < cut) {
        discard;
    }

    vec3 albedo = tex.rgb * glcolor.rgb;
    vec3 light = texture2D(lightmap, lmcoord.st).rgb;

    // Night: trunks are high-albedo so they stay visible; green canopies crush
    // to black-on-black without a higher floor + tiny lift.
    float dayness = clamp(luma(skyColor) * 2.5, 0.0, 1.0);
    float night = 1.0 - dayness;
    light = max(light, vec3(mix(0.22, 0.09, dayness)));
    albedo *= light;
    albedo += vec3(0.014, 0.024, 0.012) * night;

    vec3 N = normalize(normal);
    // Softer fog on cutout so canopies don't dissolve into night sky first
    albedo = applyFog(albedo, dist * 0.52, fogColor);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(albedo, 1.0);
    gl_FragData[1] = vec4(encodeNormal(N), 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
