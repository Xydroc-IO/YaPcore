#version 120

#include "/lib/common.glsl"

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    viewPos = view.xyz;
    worldPos = (gbufferModelViewInverse * view).xyz + cameraPosition;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    texcoord = gl_MultiTexCoord0;
    lmcoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
