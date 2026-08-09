"""Backface-cull the beam, so its far side is not visible through its near side.

The beacon render type does not cull -- that is why a vanilla beacon looks like
a hollow tube you can see the inside of. Rather than hunt for a translucent
render type that does cull, the two faces pointing away from the camera are
simply not submitted. That is authoritative regardless of what the pipeline's
cull state turns out to be, and it is cheaper than having the GPU throw the
geometry away after transforming it.

Which faces those are is decided per frame from the camera's position, so it
stays correct as the player walks around the beam.
"""
import io

def sub(old, new, label):
    global s
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    s = s.replace(old, new, 1)
    print("  ok:", label)

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/OrichalcosRenderer.java"
s = io.open(p, encoding="utf-8").read()

# ---------- render(): hand the beam where the camera is ----------
sub("""            if(age >= seal.beamAt())
            {
                drawBeam(poseStack, collector, seal, age, gameTime, partial);
            }""",
    """            if(age >= seal.beamAt())
            {
                // Camera offset in the beam's own space, for the face cull.
                drawBeam(poseStack, collector, seal, age, gameTime, partial,
                    (float)(camera.x - seal.x), (float)(camera.z - seal.z));
            }""", "render passes the camera offset")

# ---------- drawBeam signature + pass-through ----------
sub("""    private static void drawBeam(PoseStack poseStack, SubmitNodeCollector collector,
        Seal seal, float age, long gameTime, float partial)
    {""",
    """    private static void drawBeam(PoseStack poseStack, SubmitNodeCollector collector,
        Seal seal, float age, long gameTime, float partial, float toCameraX, float toCameraZ)
    {""", "drawBeam signature")

sub("""        column(collector, poseStack, pose, type, core, v0, v1,
            Math.round(255F * faded) << 24 | rgb);
        column(collector, poseStack, pose, type, glow, v0, v1,
            Math.round(GLOW_ALPHA * faded) << 24 | rgb);""",
    """        column(collector, poseStack, pose, type, core, v0, v1,
            Math.round(255F * faded) << 24 | rgb, toCameraX, toCameraZ);
        column(collector, poseStack, pose, type, glow, v0, v1,
            Math.round(GLOW_ALPHA * faded) << 24 | rgb, toCameraX, toCameraZ);""",
    "column calls")

# ---------- column(): skip the faces pointing away ----------
sub("""    /** One four-sided shell of the beam, from the ground to {@link #BEAM_HEIGHT}. */
    private static void column(SubmitNodeCollector collector, PoseStack poseStack,
        PoseStack.Pose pose, net.minecraft.client.renderer.rendertype.RenderType type,
        float radius, float v0, float v1, int tint)
    {
        collector.submitCustomGeometry(poseStack, type, (unused, buffer) ->
        {
            // Four sides, each corner to the next, so the shell closes. The
            // beacon render type does not cull, so the winding is free.
            float[][] sides = {
                {-radius, -radius, radius, -radius},
                {radius, -radius, radius, radius},
                {radius, radius, -radius, radius},
                {-radius, radius, -radius, -radius}};
            for(float[] side : sides)
            {
                vertex(buffer, pose, side[0], BEAM_HEIGHT, side[1], 0F, v1, tint);
                vertex(buffer, pose, side[0], 0F, side[1], 0F, v0, tint);
                vertex(buffer, pose, side[2], 0F, side[3], 1F, v0, tint);
                vertex(buffer, pose, side[2], BEAM_HEIGHT, side[3], 1F, v1, tint);
            }
        });
    }""",
    """    /**
     * One four-sided shell of the beam, from the ground to {@link #BEAM_HEIGHT}.
     * <p>
     * <b>Back faces are not submitted at all.</b> The beacon render type does
     * not cull -- that is why a vanilla beacon reads as a hollow tube you can
     * see the inside of -- so the two sides pointing away from the camera are
     * dropped here instead. Doing it at submission rather than asking the
     * pipeline to cull is correct whatever that pipeline's cull state is, and
     * it is half the geometry rather than half of it transformed and thrown
     * away.
     */
    private static void column(SubmitNodeCollector collector, PoseStack poseStack,
        PoseStack.Pose pose, net.minecraft.client.renderer.rendertype.RenderType type,
        float radius, float v0, float v1, int tint, float toCameraX, float toCameraZ)
    {
        collector.submitCustomGeometry(poseStack, type, (unused, buffer) ->
        {
            // Four sides, each corner to the next so the shell closes, with the
            // outward normal of each in the last two columns.
            float[][] sides = {
                {-radius, -radius, radius, -radius, 0F, -1F},
                {radius, -radius, radius, radius, 1F, 0F},
                {radius, radius, -radius, radius, 0F, 1F},
                {-radius, radius, -radius, -radius, -1F, 0F}};
            for(float[] side : sides)
            {
                if(side[4] * toCameraX + side[5] * toCameraZ <= 0F)
                {
                    continue;   // facing away; the near side is in front of it
                }
                vertex(buffer, pose, side[0], BEAM_HEIGHT, side[1], 0F, v1, tint);
                vertex(buffer, pose, side[0], 0F, side[1], 0F, v0, tint);
                vertex(buffer, pose, side[2], 0F, side[3], 1F, v0, tint);
                vertex(buffer, pose, side[2], BEAM_HEIGHT, side[3], 1F, v1, tint);
            }
        });
    }""", "column culls back faces")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("done")
