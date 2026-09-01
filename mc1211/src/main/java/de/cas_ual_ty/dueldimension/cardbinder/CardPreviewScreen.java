package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.clientutil.hub.MenuInk;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.BoardPip;
import de.cas_ual_ty.dueldimension.clientutil.FieldQuad;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.clientutil.UnownedPipelines;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import de.cas_ual_ty.dueldimension.rarity.RarityEntry;
import de.cas_ual_ty.dueldimension.rarity.RarityLayer;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One card, held up and turned.
 *
 * <h2>Why the card is drawn as a quad and not blitted</h2>
 * A GUI in 26.2 poses with a {@code Matrix3x2f}, which is two dimensional by
 * construction — there is no rotation about X or Y to be had, so a blit can
 * never show a card edge-on. The four corners are therefore rotated and
 * projected here, in plain arithmetic, and handed to {@link FieldQuad} as an
 * arbitrary quad. That is the same road the duel field takes for its perspective
 * zones, through the same {@link BoardPip} window, because a screen has no
 * render pass of its own to submit geometry into.
 *
 * <h2>The foil</h2>
 * The rarity's own layer images, over the card at the same corners and in the
 * order {@code ydm_db/rarities/*.json} lists them — the same list
 * {@code CardRenderUtil.renderDuelCardAdvanced} composites for a flat card,
 * asked of {@link DdDatabase#getRarity(String)} rather than inferred from what
 * the rarity is called.
 * <p>
 * <b>The duel field's mask cannot be used here.</b> That path writes a scalar
 * into the framebuffer's alpha at the cursor and reads it back on the next
 * pass ({@code FoilPipelines.MASK} then {@code FOIL}), which works because the
 * screen's alpha is scratch nobody else reads. Inside a {@link BoardPip} it is
 * not scratch: the region is rendered to its own RGBA target and composited
 * back through that alpha, so writing 0.25 under the cursor punches a hole in
 * the card rather than masking the foil.
 * <p>
 * So the mask is a number instead of a channel — {@link #highlight}, a band
 * that sweeps across the face as the card turns, evaluated per mesh vertex and
 * handed to the quad as its alpha. It carries the same meaning the pipelines
 * did: a {@code NORMAL} layer shows where the band is and an {@code INVERTED}
 * one shows where it is not, which is why the database pairs an
 * {@code _active} image with a {@code _passive} one — a foil is a crossfade
 * between two printings of the same frame, not a shine added on top.
 */
public class CardPreviewScreen extends Screen
{
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 300;
    private static final int PAD = 10;

    /** Half the card's width in the preview's own units; height follows the aspect. */
    private static final float HALF_W = 0.34F;
    /** How far the eye is from the card. Bigger is a flatter, longer lens. */
    private static final float EYE = 2.6F;
    /** Radians per pixel dragged. */
    private static final float DRAG_SPEED = 0.012F;
    /** How far the card may be tipped, so it never turns inside out. */
    private static final float MAX_PITCH = 1.1F;
    /** Idle drift, so a card left alone still shows its foil moving. */
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
    private static final float MAX_ZOOM = 3F;

    /**
     * How much of the card a foil layer covers where the band is brightest.
     * <p>
     * The layer images are binary-alpha stencils — {@code secret_rare.png} and
     * {@code ultimate_rare.png} are opaque over the whole 421x614 face — so
     * their alpha says <em>where</em> and never <em>how much</em>. Drawn at 1
     * they are a coloured sheet laid over the art, which is the "sticker" look
     * this whole path exists to avoid. Everything under 1 is the foil.
     */
    private static final float FOIL_STRENGTH = 0.55F;
    /**
     * Bright bands across the card at once.
     * <p>
     * The alpha is interpolated between mesh vertices, so {@link #STEPS} + 1
     * samples across the face is the resolution available: much above two
     * cycles and the band stops being a band and starts being a stipple.
     */
    private static final float FOIL_BANDS = 1.6F;
    /**
     * Lit as if in full daylight, as {@code FieldQuad} does. The board is a
     * picture, not a thing in the world, and its pipeline is unlit anyway --
     * but the vertex format declares the element and an unset one reads
     * whatever the buffer held last.
     */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final Screen parent;
    private final CardHolder card;
    private final String rarity;
    private final boolean held;
    /** The database's own layer list for this rarity, or null if it has none. */
    private final RarityEntry rarityEntry;

    private float yaw;
    private float pitch = -0.12F;
    private float zoom = 1F;
    private boolean dragging;
    private double lastX;
    private double lastY;
    private long lastFrame;

    public CardPreviewScreen(Screen parent, CardHolder card, String rarity, boolean held)
    {
        super(Component.literal(card.getCard().getName()));
        this.parent = parent;
        this.card = card;
        this.rarity = rarity == null ? "" : rarity;
        this.held = held;
        // Exact, case-sensitive lookup, and deliberately so: a set's rarity
        // string and a rarity file's own "rarity" field are the same string.
        // Anything the database does not declare -- "Common", "Short Print",
        // "Shatterfoil Rare" -- has no foil, which is an answer and not a gap
        // to be filled in by guessing at the name.
        this.rarityEntry = DdDatabase.getRarity(this.rarity);
    }

    @Override
    protected void init()
    {
        super.init();
        // A mod pipeline is not in getStaticPipelines(), so nothing validates
        // its shader at reload and a bad one would surface as a crash at the
        // first unowned card. Asking here turns that into a log line and the
        // dim fallback, and re-asking per init picks up a resource reload.
        UnownedPipelines.refresh();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

        long now = net.minecraft.Util.getMillis();
        long since = lastFrame == 0L ? 0L : now - lastFrame;
        lastFrame = now;
        if(!dragging)
        {
            yaw += IDLE_SPIN * since;
        }

        poseStack.fillGradient(0, 0, width, height, 0xD0101010, 0xE0101010);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, PANEL_W, PANEL_H);

        String name = font.plainSubstrByWidth(card.getCard().getName(), PANEL_W - PAD * 2);
        poseStack.text(font, name, left + (PANEL_W - font.width(name)) / 2, top + PAD,
            MenuInk.title(), MenuInk.shadow());

        String label = (rarity.isEmpty() ? "No rarity recorded" : rarity)
            + (held ? "" : "   — not yet pulled");
        poseStack.text(font, label, left + (PANEL_W - font.width(label)) / 2, top + PAD + 12,
            held ? MenuInk.body() : MenuInk.dim(), MenuInk.shadow());

        int viewTop = top + PAD + 28;
        int viewBottom = top + PANEL_H - PAD - 22;
        BoardPip.draw(poseStack, left + PAD, viewTop, left + PANEL_W - PAD, viewBottom,
            (pose, collector) -> paint(pose, collector,
                (left + PANEL_W - PAD) - (left + PAD), viewBottom - viewTop));

        String hint = "Drag to turn";
        poseStack.text(font, hint, left + (PANEL_W - font.width(hint)) / 2,
            top + PANEL_H - PAD - 10, MenuInk.dim(), MenuInk.shadow());

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
    }

    /**
     * No background from vanilla, because this screen draws before
     * {@code super.render} and vanilla draws the background from inside it.
     * <p>
     * In a level that background is the BLUR and nothing else -- the panorama
     * and {@code renderMenuBackground} are both gated on there being no level --
     * so leaving it in place blurs everything this screen has already put down,
     * which is the whole interface. 26.2 refuses it too, in the same words:
     * <blockquote>fillGradient, not extractBackground: that one blurs.</blockquote>
     * The dim, where this screen wants one, is its own and goes down first.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }


    /** Draws the turned card, then its foil, inside the picture-in-picture pass. */
    private void paint(com.mojang.blaze3d.vertex.PoseStack pose,
        de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector collector, int viewW, int viewH)
    {
        // Call order is draw order from here; see FieldQuad.resetLayers. This
        // screen used to skip it, and the counter it feeds is static -- so it
        // climbed by the card's whole mesh every frame for as long as the
        // preview stayed open, growing the layer map without bound.
        FieldQuad.resetLayers();

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
            mesh(pose, collector, DuelTextures.COVER, scale, 1F, 0F, -1F, 1F, 1F, false);
            pose.popPose();
            return;
        }

        // One image whether the card is held or not: an unowned face is greyed
        // by the fragment shader UnownedPipelines.mesh selects, not by a second
        // desaturated copy of the file.
        ResourceLocation face = DuelTextures.cardSmooth(card.getCard(), card.imageIndex,
            DuelTextures.PREVIEW_CARD_SIZE);
        // That hands back EDOPro's own unknown.png while the art is still
        // downloading or after the download failed, and that texture is
        // already card-shaped where the mod's cached art is letterboxed inside
        // a square. Sampling the placeholder through the card window would crop
        // and stretch it. Same test BoardRenderer.drawCardAtCorners makes.
        boolean edoproArt = face.equals(DuelTextures.UNKNOWN);
        // ...and the placeholder is not the card, so it must not be greyed
        // either. cardUnowned used to return the plain UNKNOWN in exactly this
        // case, so an unowned card with no art yet has always drawn in full
        // colour; this is that behaviour carried across deliberately.
        mesh(pose, collector, face, scale,
            edoproArt ? 0F : DuelTextures.CARD_U0,
            edoproArt ? 0F : DuelTextures.CARD_V0,
            edoproArt ? 1F : DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            edoproArt ? 1F : DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1F,
            !held && !edoproArt);

        if(rarityEntry == null || !held)
        {
            // No foil on a card you have not pulled: the page is telling you
            // what you are missing, and dressing it up would read as owning it.
            pose.popPose();
            return;
        }

        // How strongly the whole treatment shows at this angle, applied once so
        // every layer of a rarity fades together.
        float gain = FOIL_STRENGTH * edgeOn();

        // In the database's order, so each layer lands over the last -- exactly
        // as CardRenderUtil walks rarity.layers for a flat card. A Gold Rare's
        // list is [rare, gold_rare_active, gold_rare_passive]: the holostamp,
        // then the bright gold frame where the band is, then the dull gold
        // frame where it is not.
        for(RarityLayer layer : rarityEntry.layers)
        {
            // The info image, not the main one. They are the same picture at
            // two sizes -- ImageHandler tags them 256 and 64 -- and this card
            // is drawn several hundred pixels tall.
            foilLayer(pose, collector, layer.getInfoImageResourceLocation(), scale,
                layer.type.invertedRendering, gain);
        }
        pose.popPose();
    }

    /**
     * One rarity layer over the card, with the highlight in its vertex colours.
     * <p>
     * Not {@code FieldQuad.drawCorners} per cell, and the reason is the look:
     * that overload takes one alpha for the whole quad, so a sweeping band
     * would come out as sixty-four flat blocks. Submitting the mesh as one
     * piece of custom geometry lets each vertex carry its own alpha, which the
     * pipeline interpolates across the cell — and collapses sixty-four
     * submissions per layer into one. {@code CoinModel} takes the same route
     * for the same reason.
     * <p>
     * The layer is sampled through the card's own UV window: rarity overlays
     * are letterboxed by {@code ImageHandler.adjustRawImage} with exactly the
     * margin the card art gets, so the two register pixel for pixel.
     */
    private void foilLayer(com.mojang.blaze3d.vertex.PoseStack pose,
        de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector collector, ResourceLocation texture,
        float scale, boolean inverted, float gain)
    {
        FieldQuad.submit(pose, collector, texture, (vertexPose, buffer) ->
        {
            for(int row = 0; row < STEPS; row++)
            {
                float ty = row / (float)STEPS;
                float ty1 = (row + 1) / (float)STEPS;
                for(int column = 0; column < STEPS; column++)
                {
                    float tx = column / (float)STEPS;
                    float tx1 = (column + 1) / (float)STEPS;

                    // Top-left, bottom-left, bottom-right, top-right: the
                    // winding FieldQuad.draw uses, and going round the other
                    // way turns the face away and it vanishes.
                    foilVertex(buffer, vertexPose, tx, ty, scale, inverted, gain);
                    foilVertex(buffer, vertexPose, tx, ty1, scale, inverted, gain);
                    foilVertex(buffer, vertexPose, tx1, ty1, scale, inverted, gain);
                    foilVertex(buffer, vertexPose, tx1, ty, scale, inverted, gain);
                }
            }
        });
    }

    /**
     * One foil vertex: the card point, projected, carrying its own highlight.
     * <p>
     * {@code INVERTED} is the complement and nothing more. That is what the
     * blend it replaces meant — {@code FOIL} showed a layer where the mask's
     * alpha was low and {@code FOIL_INVERTED} where it was high — and it is
     * why a NORMAL and an INVERTED layer of the same rarity sum to a constant:
     * the pair crossfades between two printings of one frame rather than
     * stacking two coats of foil.
     */
    private void foilVertex(com.mojang.blaze3d.vertex.VertexConsumer buffer,
        com.mojang.blaze3d.vertex.PoseStack.Pose vertexPose, float tx, float ty,
        float scale, boolean inverted, float gain)
    {
        float[] point = project(tx, ty, scale);
        float band = highlight(tx, ty);
        buffer.addVertex(vertexPose, point[0], point[1], 0F)
            .setColor(ScreenUtil.colour(1F, 1F, 1F, gain * (inverted ? 1F - band : band)))
            .setUv(DuelTextures.CARD_U0 + (DuelTextures.CARD_U1 - DuelTextures.CARD_U0) * tx,
                DuelTextures.CARD_V0 + (DuelTextures.CARD_V1 - DuelTextures.CARD_V0) * ty)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(0F, 0F, 1F);
    }

    /**
     * Where the light is catching the card, 0 to 1, at one point on its face.
     * <p>
     * This is the mask the duel field writes into the framebuffer's alpha, as
     * a number — see this class's notes for why that channel is unusable
     * inside a picture-in-picture region. It is the only place the look is
     * decided.
     * <p>
     * The band runs corner to corner because that is how a printed foil's
     * diffraction lines run, and its phase is driven by the angle because a
     * highlight that stays put as the card turns is paint and one that travels
     * is foil. A cosine rather than a spike: the alpha is interpolated between
     * mesh vertices, and a smooth function is the one that survives being
     * sampled nine times across the card.
     */
    private float highlight(float tx, float ty)
    {
        float along = tx * 0.7F + ty * 0.3F;
        float phase = (along * FOIL_BANDS - yaw * 0.55F - pitch * 0.25F)
            * (float)(Math.PI * 2D);
        return 0.5F + 0.5F * (float)Math.cos(phase);
    }

    /**
     * The card, as a grid of quads rather than one.
     * <p>
     * See this class's notes on warping: each sub-vertex is projected in its own
     * right, so the perspective divide is applied before the texture is stepped
     * rather than after, and what error remains is confined to a single small
     * cell.
     *
     * @param desaturate the card is not held, so grey it as it is drawn. Only
     *                   the face passes true: the back is the same board
     *                   whatever the card, and it is not what the player is
     *                   missing
     */
    private void mesh(com.mojang.blaze3d.vertex.PoseStack pose,
        de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector collector, ResourceLocation texture,
        float scale, float u0, float v0, float uSpan, float vSpan, float alpha,
        boolean desaturate)
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
                    u0 + uSpan * tx1, v0 + vSpan * ty1, 1F, alpha, desaturate);
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

    /**
     * How much the foil is showing at this angle.
     * <p>
     * A real foil is brightest when it is catching the light and nearly gone
     * face-on. Tying the strength to how far the card is turned is what stops
     * the effect reading as a sticker laid over the art.
     */
    private float edgeOn()
    {
        float turn = Math.abs((float)Math.sin(yaw)) * 0.8F
            + Math.abs((float)Math.sin(pitch)) * 0.4F;
        return 0.35F + Math.min(0.65F, turn);
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

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        boolean doubleClick = false;
        if(event.button() == 0)
        {
            dragging = true;
            lastX = event.x();
            lastY = event.y();
            return true;
        }
        return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
    }

    @Override
    public boolean mouseDragged(double vanillaX, double vanillaY, int vanillaButton, double dragX, double dragY)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        if(dragging)
        {
            yaw += (float)(event.x() - lastX) * DRAG_SPEED;
            pitch = Math.clamp(pitch + (float)(event.y() - lastY) * DRAG_SPEED,
                -MAX_PITCH, MAX_PITCH);
            lastX = event.x();
            lastY = event.y();
            return true;
        }
        return super.mouseDragged(vanillaX, vanillaY, vanillaButton, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        dragging = false;
        return super.mouseReleased(vanillaX, vanillaY, vanillaButton);
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }
}
