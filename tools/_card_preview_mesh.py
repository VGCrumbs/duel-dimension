"""The preview card: a projected mesh, larger, and zoomable.

Three faults in one go.

WARPING. The card was one quad. Whatever draws it steps the texture linearly
between the four corners, but a turned card is a trapezoid on screen -- the far
edge is shorter -- so an even step across the screen is an uneven step across
the picture, and the art visibly slides and stretches towards the near edge.
Subdividing fixes it because each sub-vertex is PROJECTED in its own right
rather than interpolated between corners: the perspective divide happens before
the texture is stepped instead of after. It is the same answer
FieldQuad.drawProjected reaches for on the duel field.

UPSIDE DOWN AND REVERSED. Last turn I flipped the quad's y on the theory that
the pass runs its y the other way. It does not: the base transform is
scale(s, s, -s), which negates Z alone, so y still runs downwards exactly as a
GUI's does and the original order was right. The flip is undone, and the
orientation is now stated on the projection itself rather than left implicit in
a corner list, so it cannot be quietly inverted again.

SIZE AND ZOOM. Fill goes from 0.42 of the view to 0.735, which is that times
1.75, and the wheel scales it further.
"""
import io

p = "src/main/java/de/cas_ual_ty/dueldimension/cardbinder/CardPreviewScreen.java"
s = io.open(p, encoding="utf-8").read()

start = s.index("    /** Draws the turned card, then its foil, inside the picture-in-picture pass. */")
end = s.index("    @Override\n    public boolean mouseClicked(")

new = '''    /** Draws the turned card, then its foil, inside the picture-in-picture pass. */
    private void paint(com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector collector, int viewW, int viewH)
    {
        // BoardPip hands over a space whose origin is the TOP-LEFT of the
        // region, not its middle -- so a card built around (0,0) hangs off that
        // corner, which is exactly what it did. Move to the centre first.
        pose.pushPose();
        pose.translate(viewW / 2F, viewH / 2F, 0F);

        float scale = Math.min(viewW / (HALF_W * 2F),
            viewH / (HALF_W * 2F / DuelTextures.CARD_ASPECT)) * FILL * zoom;

        // Which way it is facing. A card is a sheet with two different sides, so
        // past a quarter turn the back is towards you -- without this it reads
        // as a decal that stays legible from behind.
        if(Math.cos(yaw) < 0F)
        {
            // The back, and NO foil: the reverse of a Yu-Gi-Oh card is the same
            // board whatever the front was printed at, and shining a Secret
            // Rare through it would be inventing a card that does not exist.
            // Mirrored in u, because this is the other side of the sheet.
            mesh(pose, collector, DuelTextures.COVER, scale, 1F, 0F, -1F, 1F, 1F);
            pose.popPose();
            return;
        }

        mesh(pose, collector, held
                ? DuelTextures.cardSmooth(card.getCard(), card.imageIndex,
                    DuelTextures.PREVIEW_CARD_SIZE)
                : DuelTextures.cardUnowned(card.getCard(), card.imageIndex,
                    DuelTextures.PREVIEW_CARD_SIZE),
            scale, DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1F);

        if(!foil.any() || !held)
        {
            // No foil on a card you have not pulled: the page is telling you
            // what you are missing, and dressing it up would read as owning it.
            pose.popPose();
            return;
        }

        // The sheets slide with the angle. That single fact is the whole effect:
        // a highlight that stays put as the card turns is paint, one that
        // travels is foil.
        float slide = (yaw * foil.travel) / (float)(Math.PI * 2F);
        float lift = (pitch * foil.travel) / (float)(Math.PI * 2F);

        mesh(pose, collector, CardRarityFoil.FOIL, scale, slide, lift, 1F, 1F,
            foil.sheen * edgeOn());

        if(foil.sparkle > 0F)
        {
            // Faster and the other way, so the specks do not travel locked to
            // the colour bands and give the sheet away as one image.
            mesh(pose, collector, CardRarityFoil.SPARKLE, scale,
                -slide * 1.7F, lift * 1.3F, 1.5F, 1.5F, foil.sparkle * edgeOn());
        }
        pose.popPose();
    }

    /**
     * The card, as a grid of quads rather than one.
     * <p>
     * See this class's notes on warping: each sub-vertex is projected in its own
     * right, so the perspective divide is applied before the texture is stepped
     * rather than after, and what error remains is confined to a single small
     * cell.
     */
    private void mesh(com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector collector, Identifier texture,
        float scale, float u0, float v0, float uSpan, float vSpan, float alpha)
    {
        for(int row = 0; row < STEPS; row++)
        {
            float ty = row / (float)STEPS;
            float ty1 = (row + 1) / (float)STEPS;
            for(int column = 0; column < STEPS; column++)
            {
                float tx = column / (float)STEPS;
                float tx1 = (column + 1) / (float)STEPS;

                float[] a = project(tx, ty, scale);
                float[] b = project(tx1, ty, scale);
                float[] c = project(tx1, ty1, scale);
                float[] d = project(tx, ty1, scale);

                FieldQuad.drawCorners(pose, collector, texture,
                    new FieldQuad.Corners(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]),
                    u0 + uSpan * tx, v0 + vSpan * ty,
                    u0 + uSpan * tx1, v0 + vSpan * ty1, 1F, alpha);
            }
        }
    }

    /**
     * A point on the card, turned and projected.
     *
     * @param tx across the face, 0 at the left edge and 1 at the right
     * @param ty down the face, 0 at the TOP. This pass keeps a GUI y, which runs
     *           downwards -- its base transform is scale(s, s, -s) and negates Z
     *           alone -- so the top of the card is the NEGATIVE side. Stated here
     *           rather than left implicit in a corner list, because assuming
     *           otherwise is what drew every card upside down.
     * @return the point in the pass's own two-dimensional coordinates
     */
    private float[] project(float tx, float ty, float scale)
    {
        float halfH = HALF_W / DuelTextures.CARD_ASPECT;
        float x = (tx - 0.5F) * HALF_W * 2F;
        float y = (ty - 0.5F) * halfH * 2F;
        float z = 0F;

        float cosYaw = (float)Math.cos(yaw);
        float sinYaw = (float)Math.sin(yaw);
        float cosPitch = (float)Math.cos(pitch);
        float sinPitch = (float)Math.sin(pitch);

        float rx = x * cosYaw + z * sinYaw;
        float rz = -x * sinYaw + z * cosYaw;
        float ry = y * cosPitch - rz * sinPitch;
        rz = y * sinPitch + rz * cosPitch;

        // A weak perspective divide: the card is a flat sheet a fixed distance
        // away, so dividing by depth is the whole transform.
        float perspective = EYE / Math.max(0.35F, EYE - rz);
        return new float[] {rx * perspective * scale, ry * perspective * scale};
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        // Multiplied, not added: a fixed step is coarse when zoomed out and
        // barely moves when zoomed in, whereas a ratio feels the same at both
        // ends. Bounded so the card cannot be lost off-screen or shrunk away.
        zoom = Math.clamp(zoom * (delta > 0 ? 1.12F : 1F / 1.12F), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

'''
s = s[:start] + new + s[end:]

# constants + the zoom field
s = s.replace("""    /** Idle drift, so a card left alone still shows its foil moving. */
    private static final float IDLE_SPIN = 0.0006F;""",
"""    /** Idle drift, so a card left alone still shows its foil moving. */
    private static final float IDLE_SPIN = 0.0006F;
    /**
     * How much of the view the card fills at rest.
     * <p>
     * 0.42 left a card floating in a lot of empty panel; this is that times
     * 1.75, with just enough margin for a corner to swing out as the card turns
     * without meeting the edge of the window.
     */
    private static final float FILL = 0.735F;
    /**
     * Cells per axis in the card's mesh. Cost is the square of it.
     * <p>
     * Eight is 64 quads, which is nothing, and by then the perspective error
     * within a single cell is well under a pixel.
     */
    private static final int STEPS = 8;
    private static final float MIN_ZOOM = 0.5F;
    private static final float MAX_ZOOM = 3F;""", 1)

s = s.replace("""    private float yaw;
    private float pitch = -0.12F;""",
"""    private float yaw;
    private float pitch = -0.12F;
    private float zoom = 1F;""", 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("mesh, orientation, size and zoom applied")
