package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Opening a pack, the way Master Duel does it: the whole pull laid out as a
 * strip of face-down cards, swiped through one at a time, each turning over as
 * it reaches the middle.
 * <p>
 * The reveal is <em>presentation only</em>. What was pulled is decided on the
 * server when the pack is unsealed; this screen is told the result and
 * dramatises it. Closing early, or never opening the screen at all, changes
 * nothing about what the player received.
 * <p>
 * The turn is a horizontal squash through zero width — the standard way to fake
 * a card flip in two dimensions — with the back drawn while the card is still
 * past halfway and the face after, so the swap happens exactly when the card is
 * edge-on and cannot be seen.
 */
public class PackOpeningScreen extends Screen
{
    private static final String LAYOUT = "pack_opening";

    /** Past this many pixels a press is a swipe rather than a click. */
    private static final double DRAG_SLOP = 5;

    /** Which part of the ceremony is running. */
    private enum Stage
    {
        /** The strip: cards in a row, turned over as they are reached. */
        STRIP,
        /** All cards turned: the whole pull laid out at once. */
        SUMMARY
    }

    private final String setName;
    private final List<Integer> codes;
    private final List<String> rarities;

    private Stage stage = Stage.STRIP;

    /**
     * Where the strip is, measured in cards. Whole numbers centre a card;
     * everything between is mid-swipe. Eased toward {@link #focus} rather than
     * set, so a swipe carries rather than snapping.
     */
    private float position;
    private int focus;

    /**
     * How far each card has turned, 0 face-down to 1 face-up. A card starts
     * turning when it settles in the middle, so the strip reveals in the order
     * the player swipes through it rather than all at once.
     */
    private final float[] flip;
    /** How far the shine has swept across each rare card once it is up. */
    private final float[] shine;

    /** A drag in progress: where it started, and where the strip was then. */
    private boolean dragging;
    private double dragFromX;
    private float dragFromPosition;
    /** Whether the drag moved far enough to count as a swipe, not a click. */
    private boolean dragged;

    /** First visible row of the summary grid. */
    private int summaryScroll;

    /** Reveals everything, then closes: its label says which it will do. */
    private HubWidgets.TextureButton skip;

    public PackOpeningScreen(String setName, List<Integer> codes, List<String> rarities)
    {
        super(Component.literal("Opening " + setName));
        this.setName = setName;
        this.codes = new ArrayList<>(codes);
        this.rarities = new ArrayList<>(rarities);
        this.flip = new float[this.codes.size()];
        this.shine = new float[this.codes.size()];
    }

    @Override
    protected void init()
    {
        // The cards were added to the collection by the server before this
        // screen was ever asked for, and it has already sent the new one. Doing
        // it here as well would count every card twice.
        EditorState.invalidate();
        // Ask the image pipeline for every card NOW rather than when each one
        // turns. It fetches asynchronously and yields placeholder art until it
        // has the file, so requesting at reveal time meant the card was turned
        // over to show a placeholder and only became itself a moment later.
        requestArt();
        skip = new HubWidgets.TextureButton(width - 96, height - 30, 84, 20,
            Component.literal("Reveal All"), pressed -> revealAll());
        addRenderableWidget(skip);
    }

    /**
     * Nudges the image pipeline for every card in the pull.
     * <p>
     * Called from init and again each tick: a request that is still in flight
     * returns the placeholder, so asking repeatedly is how the real art is
     * picked up once it lands. The calls are cheap after the first, since the
     * pipeline caches per card and size.
     */
    private void requestArt()
    {
        for(int code : codes)
        {
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card != null)
            {
                DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE);
            }
        }
    }

    @Override
    public void tick()
    {
        requestArt();
    }

    /** True when this card is worth a flourish. */
    private boolean isRare(int at)
    {
        if(at < 0 || at >= rarities.size())
        {
            return false;
        }
        String rarity = rarities.get(at);
        // Anything that is not the base rarity is treated as notable; the mod's
        // rarity names vary by set, so this asks what it is NOT rather than
        // keeping a list that would fall out of date.
        return rarity != null && !rarity.isBlank()
            && !rarity.equalsIgnoreCase("Common")
            && !rarity.equalsIgnoreCase("C");
    }

    private Properties cardAt(int at)
    {
        return at < 0 || at >= codes.size() ? null
            : DdDatabase.PROPERTIES_LIST.get((long)codes.get(at));
    }

    private boolean allTurned()
    {
        for(float turned : flip)
        {
            if(turned < 1F)
            {
                return false;
            }
        }
        return true;
    }

    /** Turns everything at once, for a player who does not want the ceremony. */
    private void revealAll()
    {
        if(stage == Stage.SUMMARY)
        {
            onClose();
            return;
        }
        Arrays.fill(flip, 1F);
        Arrays.fill(shine, 1F);
        toSummary();
    }

    private void toSummary()
    {
        stage = Stage.SUMMARY;
        summaryScroll = 0;
        playSound(SoundEvents.PLAYER_LEVELUP, 1.2F);
    }

    // ---- input ----

    private void moveTo(int index)
    {
        // Past the last card the strip is finished, so the swipe that would
        // have gone off the end is what opens the summary.
        if(index >= codes.size() && allTurned())
        {
            toSummary();
            return;
        }
        int clamped = Math.max(0, Math.min(codes.size() - 1, index));
        if(clamped != focus)
        {
            focus = clamped;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        if(stage == Stage.SUMMARY)
        {
            onClose();
            return true;
        }
        // A press starts a possible drag. Whether it was a drag or a click is
        // decided on release, by how far it travelled.
        dragging = true;
        dragged = false;
        dragFromX = mouseX;
        dragFromPosition = position;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy)
    {
        if(!dragging || stage != Stage.STRIP)
        {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        float spacing = Math.max(1F, spacing());
        position = dragFromPosition - (float)((mouseX - dragFromX) / spacing);
        position = Mth.clamp(position, 0F, Math.max(0, codes.size() - 1));
        if(Math.abs(mouseX - dragFromX) > DRAG_SLOP)
        {
            dragged = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if(dragging && stage == Stage.STRIP)
        {
            dragging = false;
            if(dragged)
            {
                // Let go mid-swipe and the nearest card takes the middle.
                moveTo(Math.round(position));
            }
            else
            {
                // A click, not a swipe: the card under the cursor comes to the
                // middle, and a click on the middle one moves along.
                int under = cardUnder(mouseX);
                moveTo(under == focus ? focus + 1 : under);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Which card the cursor is over, by how far it is from the middle. */
    private int cardUnder(double mouseX)
    {
        float spacing = Math.max(1F, spacing());
        return Math.round(position + (float)((mouseX - width / 2.0) / spacing));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if(stage == Stage.SUMMARY)
        {
            summaryScroll = Math.max(0, summaryScroll - (int)Math.signum(delta));
            return true;
        }
        moveTo(focus - (int)Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if(stage == Stage.STRIP)
        {
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)
            {
                moveTo(focus - 1);
                return true;
            }
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)
            {
                moveTo(focus + 1);
                return true;
            }
        }
        else if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)
        {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch)
    {
        if(minecraft != null)
        {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    // ---- rendering ----

    private int cardWidth()
    {
        return Layout.of(LAYOUT).i("card.width", 128);
    }

    /** How far apart the cards sit along the strip. */
    private float spacing()
    {
        return cardWidth() * Layout.of(LAYOUT).f("strip.spacing", 0.62F);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        Layout layout = Layout.of(LAYOUT);
        if(skip != null)
        {
            skip.setMessage(Component.literal(stage == Stage.SUMMARY ? "Close"
                : allTurned() ? "Summary" : "Reveal All"));
        }

        if(stage == Stage.SUMMARY)
        {
            renderSummary(poseStack, layout);
            super.render(poseStack, mouseX, mouseY, partialTick);
            return;
        }

        advanceAnimation(layout);
        renderStrip(poseStack, layout);

        int centreX = width / 2;
        font.drawShadow(poseStack, setName, centreX - font.width(setName) / 2F, 18, 0xFFF4D089);
        String progress = (focus + 1) + " / " + codes.size();
        font.drawShadow(poseStack, progress, centreX - font.width(progress) / 2F, 30, 0xFF9FA6B4);

        String hint = allTurned() ? "Click past the end for the summary"
            : "Click, scroll or press Space";
        font.drawShadow(poseStack, hint, centreX - font.width(hint) / 2F, height - 22, 0xFF7A8090);

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    /**
     * Eases the strip toward the focused card and turns whatever has settled in
     * the middle.
     */
    private void advanceAnimation(Layout layout)
    {
        float glide = Mth.clamp(layout.f("strip.glide", 0.28F), 0.02F, 1F);
        if(!dragging)
        {
            position += (focus - position) * glide;
            if(Math.abs(focus - position) < 0.002F)
            {
                position = focus;
            }
        }

        // A card turns once it is close enough to the middle to be the one
        // being looked at. Dragging turns nothing: the player is still choosing
        // which card that is.
        if(!dragging && focus >= 0 && focus < flip.length && Math.abs(position - focus) < 0.25F)
        {
            if(flip[focus] <= 0F)
            {
                playSound(SoundEvents.BOOK_PAGE_TURN, 1.1F);
            }
            flip[focus] = Math.min(1F, flip[focus] + layout.f("flip.speed", 0.12F));
        }
        for(int i = 0; i < flip.length; i++)
        {
            if(flip[i] >= 1F && isRare(i) && shine[i] < 1F)
            {
                shine[i] = Math.min(1F, shine[i] + layout.f("shine.speed", 0.035F));
            }
        }
    }

    /**
     * The strip: every card in a row, the middle one full size and the rest
     * shrinking away to either side.
     * <p>
     * Drawn from the outside in, so a nearer card overlaps a further one — with
     * no depth buffer to lean on, the order things are painted in IS the depth.
     */
    private void renderStrip(PoseStack poseStack, Layout layout)
    {
        int cardW = cardWidth();
        int cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int centreX = width / 2;
        int centreY = height / 2 - 6;
        float spacing = spacing();
        float shrink = layout.f("strip.shrink", 0.26F);
        // Far enough that the last card drawn is already past the edge of the
        // window. A fixed count was fine while distance faded a card out before
        // it was culled; with solid cards, anything culled while still on
        // screen pops out of existence, so the reach follows the window.
        int reach = Math.max(layout.i("strip.reach", 5),
            Mth.ceil((width / 2F + cardW) / Math.max(1F, spacing)));

        List<Integer> order = new ArrayList<>();
        for(int i = 0; i < codes.size(); i++)
        {
            if(Math.abs(i - position) <= reach)
            {
                order.add(i);
            }
        }
        // Furthest first: the middle card is painted last and sits on top.
        order.sort((a, b) -> Float.compare(Math.abs(b - position), Math.abs(a - position)));

        for(int index : order)
        {
            float offset = index - position;
            float distance = Math.abs(offset);
            float scale = 1F / (1F + distance * shrink);
            int drawW = Math.round(cardW * scale);
            int drawH = Math.round(cardH * scale);
            int x = Math.round(centreX + offset * spacing);
            int y = centreY - drawH / 2;

            // Solid, however far down the strip it is. Fading the distant ones
            // made the backs look like ghosts of cards rather than cards
            // waiting their turn; size alone carries the depth, and the reach
            // is set so the furthest one is already at the screen edge, which
            // is what keeps it from popping in.
            drawCard(poseStack, index, x, y, drawW, drawH, 1F);
        }
    }

    /** One card of the strip, turned as far as it has turned. */
    private void drawCard(PoseStack poseStack, int index, int centreX, int y,
        int w, int h, float alpha)
    {
        float turned = flip[index];
        // Squashed through zero width: at the halfway point the card is
        // edge-on, which is exactly when the back can become the face unseen.
        float squash = Math.abs(Mth.cos(turned * (float)Math.PI));
        int drawnW = Math.max(1, Math.round(w * squash));
        int x = centreX - drawnW / 2;
        boolean faceUp = turned >= 0.5F;

        if(faceUp && isRare(index))
        {
            // A frame behind the card rather than a tint on it, so the art
            // stays the brightest thing on screen.
            NineSlice.draw(poseStack, HubTextures.PANEL, x - 4, y - 4, drawnW + 8, h + 8,
                NineSlice.HOVER, 3, 0.7F * alpha);
        }

        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        Properties card = faceUp ? cardAt(index) : null;
        if(card != null)
        {
            DuelTextures.bindSmooth(DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
            // Sampled out of the letterboxed square, as everywhere else, or the
            // art stretches.
            DdBlitUtil.blit(poseStack, x, y, drawnW, h,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
                DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);
        }
        else
        {
            DuelTextures.bindSmooth(DuelTextures.COVER);
            DdBlitUtil.fullBlit(poseStack, x, y, drawnW, h);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        if(faceUp && isRare(index) && shine[index] < 1F)
        {
            drawShine(poseStack, x, y, drawnW, h, shine[index]);
        }
    }

    private void renderSummary(PoseStack poseStack, Layout layout)
    {
        int pad = layout.i("summary.pad", 14);
        int gap = layout.i("summary.gap", 6);
        int top = layout.i("summary.top", 46);
        int bottom = layout.i("summary.bottom", 40);

        int usableW = width - pad * 2;
        int usableH = height - top - bottom;

        // As many columns as fit at the preferred size, then the card width is
        // recomputed so the row fills the space exactly instead of leaving a
        // ragged margin.
        int preferred = layout.i("summary.cardWidth", 62);
        int columns = Math.max(1, (usableW + gap) / (preferred + gap));
        int cardW = Math.max(16, (usableW - (columns - 1) * gap) / columns);
        int cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));

        int rows = (codes.size() + columns - 1) / columns;
        int visibleRows = Math.max(1, (usableH + gap) / (cardH + gap));
        int maxScroll = Math.max(0, rows - visibleRows);
        summaryScroll = Math.max(0, Math.min(summaryScroll, maxScroll));

        int gridW = columns * cardW + (columns - 1) * gap;
        int startX = (width - gridW) / 2;

        String title = codes.size() + " cards from " + setName;
        font.drawShadow(poseStack, title, width / 2F - font.width(title) / 2F, 22, 0xFFF4D089);

        for(int row = 0; row < visibleRows; row++)
        {
            for(int column = 0; column < columns; column++)
            {
                int index = (row + summaryScroll) * columns + column;
                if(index >= codes.size())
                {
                    break;
                }
                Properties card = cardAt(index);
                if(card == null)
                {
                    continue;
                }
                int x = startX + column * (cardW + gap);
                int y = top + row * (cardH + gap);
                drawFace(poseStack, card, x, y, cardW, cardH);
                if(isRare(index))
                {
                    // A still highlight rather than a sweep: several at once
                    // would be a light show, and the point is only to pick
                    // them out.
                    NineSlice.draw(poseStack, HubTextures.PANEL, x - 3, y - 3, cardW + 6, cardH + 6,
                        NineSlice.HOVER, 3, 0.55F);
                    drawFace(poseStack, card, x, y, cardW, cardH);
                }
            }
        }

        // Only claim there is more when there is, and say how much.
        if(maxScroll > 0)
        {
            String position = "scroll  " + Math.min(codes.size(),
                (summaryScroll + visibleRows) * columns) + " / " + codes.size();
            font.drawShadow(poseStack, position, width / 2F - font.width(position) / 2F,
                height - 30, 0xFF7A8090);
        }
        String hint = "Click to close";
        font.drawShadow(poseStack, hint, width / 2F - font.width(hint) / 2F, height - 18, 0xFF9FA6B4);
    }

    private void drawFace(PoseStack poseStack, Properties card, int x, int y, int w, int h)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        DuelTextures.bindSmooth(DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        // Sampled out of the letterboxed square, as everywhere else, or the art
        // stretches.
        DdBlitUtil.blit(poseStack, x, y, w, h,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);
    }

    /**
     * A band of light crossing the card once. Drawn from white.png at low alpha
     * and clipped to the card's own rectangle, so it reads as a sheen on the
     * card rather than a shape floating over it.
     */
    private void drawShine(PoseStack poseStack, int x, int y, int w, int h, float progress)
    {
        int bandW = Math.max(6, w / 4);
        int travel = w + bandW * 2;
        int bandX = Math.round(x - bandW + progress * travel);
        int left = Math.max(x, bandX);
        int right = Math.min(x + w, bandX + bandW);
        if(right <= left)
        {
            return;
        }
        float alpha = (1F - Math.abs(progress - 0.5F) * 2F) * 0.5F;
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 0.97F, 0.8F, Math.max(0F, alpha));
        RenderSystem.setShaderTexture(0, DuelTextures.WHITE);
        DdBlitUtil.fullBlit(poseStack, left, y, right - left, h);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
