#version 330

// Vanilla's core/entity.fsh, VERBATIM -- #moj_imports and #ifdefs intact --
// plus the two lines marked below. Verbatim-plus-two is what makes this
// re-syncable against a vanilla update by diffing; a hand-flattened copy of a
// 63-line shader full of #ifdefs would not be.
//
// The defines that decide which branches compile are not written here: they
// come off BREEZE_WIND, which UnownedPipelines copies wholesale (ALPHA_CUTOUT
// 0.1, APPLY_TEXTURE_MATRIX, NO_OVERLAY, NO_CARDINAL_LIGHTING).

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    // THE TWO ADDED LINES. Before the ALPHA_CUTOUT test rather than after,
    // which is free: this touches rgb only and the test reads a. Before the
    // faceVertexColor multiply, which is not free -- see card_desaturate.fsh
    // for why the tint has to be the last word on colour.
    float grey = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(vec3(grey), color.rgb, 0.15);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif

#ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    // The dissolve effect entirely replaces translucency
    faceVertexColor.a = 1.0;
#endif

    color *= faceVertexColor * ColorModulator;
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
