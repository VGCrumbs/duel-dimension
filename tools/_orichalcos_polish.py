"""Seal rotates into place as it grows; beam fades as well as shrinks."""
import io

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/OrichalcosRenderer.java"
s = io.open(p, encoding="utf-8").read()

# ---------- 1. constants ----------
old = """    /** Clear of the ground, so the decal does not fight the block face it sits on. */
    private static final float GROUND_CLEARANCE = 0.02F;"""
new = """    /** Clear of the ground, so the decal does not fight the block face it sits on. */
    private static final float GROUND_CLEARANCE = 0.02F;

    /**
     * How far the seal turns while it closes in, in degrees.
     * <p>
     * One full revolution, timed to land exactly as the seal reaches full size,
     * so it settles into its final orientation rather than stopping at an angle.
     */
    private static final float SPIN_DEGREES = 360F;

    /** The beam's core, as a fraction of its thickness. Vanilla's beacon ratio. */
    private static final float CORE_RATIO = 0.2F;

    /** The beam's outer glow. Also vanilla's. */
    private static final float GLOW_RATIO = 0.25F;

    /** How see-through the glow shell is at full strength, out of 255. */
    private static final int GLOW_ALPHA = 48;"""
assert old in s, "constants anchor"
s = s.replace(old, new, 1)

# ---------- 2. the seal: rotate into place, still fading ----------
old = """        PoseStack.Pose pose = poseStack.last();
        collector.submitCustomGeometry(poseStack,
            // The one entity render type that is truly unlit -- see FieldQuad
            // for how that was established. A seal that dims with the local
            // block light would be a seal that vanishes at night, which is
            // exactly when it is most likely to be seen.
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(SEAL_TEXTURE, 0F, 0F),
            (unused, buffer) ->
            {
                vertex(buffer, pose, -radius, GROUND_CLEARANCE, -radius, 0F, 0F, tint);
                vertex(buffer, pose, -radius, GROUND_CLEARANCE, radius, 0F, 1F, tint);
                vertex(buffer, pose, radius, GROUND_CLEARANCE, radius, 1F, 1F, tint);
                vertex(buffer, pose, radius, GROUND_CLEARANCE, -radius, 1F, 0F, tint);
            });"""
new = """        // Turning as it closes, coming to rest exactly as it reaches full size.
        // Baked into the corner positions rather than done with mulPose:
        // submitCustomGeometry defers the draw, and a Pose captured from the
        // stack is only safe until something pushes over it again -- which the
        // next seal in the loop would.
        float spin = (float)Math.toRadians(SPIN_DEGREES * (1F - progress));
        float cos = Mth.cos(spin);
        float sin = Mth.sin(spin);

        PoseStack.Pose pose = poseStack.last();
        collector.submitCustomGeometry(poseStack,
            // The one entity render type that is truly unlit -- see FieldQuad
            // for how that was established. A seal that dims with the local
            // block light would be a seal that vanishes at night, which is
            // exactly when it is most likely to be seen.
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(SEAL_TEXTURE, 0F, 0F),
            (unused, buffer) ->
            {
                corner(buffer, pose, -radius, -radius, cos, sin, 0F, 0F, tint);
                corner(buffer, pose, -radius, radius, cos, sin, 0F, 1F, tint);
                corner(buffer, pose, radius, radius, cos, sin, 1F, 1F, tint);
                corner(buffer, pose, radius, -radius, cos, sin, 1F, 0F, tint);
            });"""
assert old in s, "seal draw anchor"
s = s.replace(old, new, 1)

# ---------- 3. the beam: drawn here so alpha actually applies ----------
start = s.index("    /**\n     * The soul leaving, as a beacon beam.")
end = s.index("    /**\n     * One corner, with every element the format requires.")
new = """    /**
     * The soul leaving, as a beam of light.
     * <p>
     * Drawn here rather than through {@code BeaconRenderer.submitBeaconBeam},
     * which cannot fade. That helper draws its core with
     * {@code beaconBeam(texture, false)}, and the {@code false} selects
     * {@code BEACON_BEAM_OPAQUE} -- a pipeline with no blending, so the alpha
     * byte of the colour it is handed is ignored and the core stays solid green
     * until its radius reaches zero. Both shells are drawn here on the
     * translucent variant instead, which is the same render type vanilla uses
     * for the glow, so alpha reaches the core too.
     * <p>
     * The beam therefore both narrows and fades over the eight seconds.
     */
    private static void drawBeam(PoseStack poseStack, SubmitNodeCollector collector,
        Seal seal, float age, long gameTime, float partial)
    {
        float faded = seal.remaining(age);
        if(faded <= 0F)
        {
            return;
        }
        float thickness = BEAM_THICKNESS * faded;
        float core = CORE_RATIO * thickness;
        float glow = GLOW_RATIO * thickness;
        if(core <= 0F)
        {
            return;
        }

        // Vanilla's scroll, so the beam crawls the way a beacon's does rather
        // than standing there as a static stripe.
        float time = Math.floorMod(gameTime, 40) + partial;
        float v0 = -1F + Mth.frac(-time * 0.2F - Mth.floor(-time * 0.1F));
        // Tied to the beam's FULL thickness, not its current one, so the
        // texture does not stretch as the beam narrows.
        float v1 = v0 + BEAM_HEIGHT * 0.5F / (CORE_RATIO * BEAM_THICKNESS);

        net.minecraft.client.renderer.rendertype.RenderType type =
            net.minecraft.client.renderer.rendertype.RenderTypes
                .beaconBeam(BeaconRenderer.BEAM_LOCATION, true);
        PoseStack.Pose pose = poseStack.last();
        int rgb = BEAM_COLOUR & 0xFFFFFF;

        column(collector, poseStack, pose, type, core, v0, v1,
            Math.round(255F * faded) << 24 | rgb);
        column(collector, poseStack, pose, type, glow, v0, v1,
            Math.round(GLOW_ALPHA * faded) << 24 | rgb);
    }

    /** One four-sided shell of the beam, from the ground to {@link #BEAM_HEIGHT}. */
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
    }

"""
s = s[:start] + new + s[end:]

# ---------- 4. the rotating-corner helper ----------
old = """    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float z, float u, float v, int tint)"""
new = """    /** A ground-plane corner at (x, z), turned about the centre by cos/sin. */
    private static void corner(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float z, float cos, float sin, float u, float v, int tint)
    {
        vertex(buffer, pose, x * cos - z * sin, GROUND_CLEARANCE, x * sin + z * cos, u, v, tint);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float z, float u, float v, int tint)"""
assert old in s, "vertex anchor"
s = s.replace(old, new, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("seal spin + beam alpha fade written")
