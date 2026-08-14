#version 330

// Vanilla's core/position_tex_color.fsh, verbatim, plus the two lines marked
// below. Kept verbatim so it can be re-synced against the vanilla file by
// diffing rather than by reading: everything except the desaturation and the
// split of the vertexColor multiply is Mojang's.
//
// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Vanilla samples and multiplies vertexColor on one line. Split, because
    // the desaturation has to land on the TEXEL and not on the tinted result:
    // DdBlitUtil's tints are how a buried card is dimmed and how an unowned
    // one pulses red, and greying after the multiply would eat the pulse. The
    // CPU pass this replaced greyed the file, so the tint has always been the
    // last word on colour.
    vec4 color = texture(Sampler0, texCoord0);
    float grey = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(vec3(grey), color.rgb, 0.15);
    color *= vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
