#version 120

#include "/lib/common.glsl"
#include "/lib/water.glsl"

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    vec3 wp = (gbufferModelViewInverse * view).xyz + cameraPosition;

    float wave = yapWaveHeight(wp.xz, frameTimeCounter);
    wp.y += wave;
    view = gbufferModelView * vec4(wp - cameraPosition, 1.0);

    viewPos = view.xyz;
    worldPos = wp;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    texcoord = gl_MultiTexCoord0;
    lmcoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
