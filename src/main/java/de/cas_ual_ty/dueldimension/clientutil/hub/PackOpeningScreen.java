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
import java.util.List;

/**
 * Opening a pack, the way Tag Force does it: one card at a time, face down,
 * turned over by the player.
 * <p>
 * The reveal is <em>presentation only</em>. What was pulled is decided on the
 * server when the pack is unsealed and written onto the opened-pack stack; this
 * screen is told the result and dramatises it. Closing early, or never opening
 * the screen at all, changes nothing about what the player received.
 * <p>
 * The turn is a horizontal squash through zero width — the standard way to fake
 * a card flip in two dimensions — with the back drawn while the card is still
 * past halfway and the face after, so the swap happens exactly when the card is
 * edge-on and cannot be seen.
 */
public class PackOpeningScreen extends Screen
{
    private static final String LAYOUT = "pack_opening";

    /** Stages of one card's reveal, in order. */
    private enum Stage
    {
        /** Sliding in from the deck, still face down. */
        DEALING,
        /** Waiting for the player to turn it. */
        READY,
        /** Mid-turn. */
        FLIPPING,
        /** Face up, shine sweeping across a rare. */
        REVEALED,
        /** All cards turned: the whole pull laid out. */
        SUMMARY
    }

    private final String setName;
    private final List<Integer> codes;
    private final List<String> rarities;

    private Stage stage = Stage.DEALING;
    private int index;
    private long stageStart;
    /** How far the shine has swept, so a rare pull reads as special. */
    private float shine;

    public PackOpeningScreen(String setName, List<Integer> codes, List<String> rarities)
    {
        super(Component.literal("Opening " + setName));
        this.setName = setName;
        this.codes = new ArrayList<>(codes);
        this.rarities = new ArrayList<>(rarities);
    }

    @Override
    protected void init()
    {
        stageStart = System.currentTimeMillis();
        // Everything pulled is already the player's; adding it here means the
        // trunk is right whether or not they sit through the reveal.
        for(int code : codes)
        {
            EditorState.trunk().add(code, 1);
        }
        EditorState.invalidate();
        addRenderableWidget(new HubWidgets.TextureButton(width - 96, height - 30, 84, 20,
            Component.literal("Skip"), pressed -> toSummary()));
    }

    private long elapsed()
    {
        return System.currentTimeMillis() - stageStart;
    }

    private void enter(Stage next)
    {
        stage = next;
        stageStart = System.currentTimeMillis();
    }

    private void toSummary()
    {
        index = codes.size();
        enter(Stage.SUMMARY);
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

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)
        {
            advance();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** One press turns the card, or moves on to the next. */
    private void advance()
    {
        switch(stage)
        {
            case READY ->
            {
                enter(Stage.FLIPPING);
                playSound(SoundEvents.BOOK_PAGE_TURN, 1.1F);
            }
            case REVEALED -> nextCard();
            case SUMMARY -> onClose();
            default ->
            {
                // Mid-animation presses skip to the end of that animation
                // rather than being swallowed, so the reveal never feels stuck.
                stageStart = 0;
            }
        }
    }

    private void nextCard()
    {
        index++;
        shine = 0F;
        if(index >= codes.size())
        {
            enter(Stage.SUMMARY);
            playSound(SoundEvents.PLAYER_LEVELUP, 1.2F);
        }
        else
        {
            enter(Stage.DEALING);
        }
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch)
    {
        if(minecraft != null)
        {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        Layout layout = Layout.of(LAYOUT);

        int cardW = layout.i("card.width", 128);
        int cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int centreX = width / 2;
        int centreY = height / 2 - 10;

        font.drawShadow(poseStack, setName, centreX - font.width(setName) / 2F, 18, 0xFFF4D089);
        String progress = Math.min(index + 1, codes.size()) + " / " + codes.size();
        font.drawShadow(poseStack, progress, centreX - font.width(progress) / 2F, 30, 0xFF9FA6B4);

        switch(stage)
        {
            case DEALING -> renderDealing(poseStack, layout, centreX, centreY, cardW, cardH);
            case READY -> renderCard(poseStack, centreX, centreY, cardW, cardH, 1F, false);
            case FLIPPING -> renderFlipping(poseStack, layout, centreX, centreY, cardW, cardH);
            case REVEALED -> renderRevealed(poseStack, layout, centreX, centreY, cardW, cardH);
            case SUMMARY -> renderSummary(poseStack, layout);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void renderDealing(PoseStack poseStack, Layout layout,
        int centreX, int centreY, int cardW, int cardH)
    {
        long duration = layout.i("deal.ms", 260);
        float t = Mth.clamp(elapsed() / (float)duration, 0F, 1F);
        // Eased so it arrives rather than stops dead.
        float eased = 1F - (1F - t) * (1F - t);
        int fromY = centreY + height / 3;
        int y = Math.round(Mth.lerp(eased, fromY, centreY));
        renderCard(poseStack, centreX, y, cardW, cardH, 1F, false);
        if(t >= 1F)
        {
            enter(Stage.READY);
            playSound(SoundEvents.ITEM_PICKUP, 1.4F);
        }
    }

    private void renderFlipping(PoseStack poseStack, Layout layout,
        int centreX, int centreY, int cardW, int cardH)
    {
        long duration = layout.i("flip.ms", 320);
        float t = Mth.clamp(elapsed() / (float)duration, 0F, 1F);
        // Squash through zero width: past halfway the card is edge-on, which is
        // exactly when the back can be swapped for the face unseen.
        float squash = Math.abs(Mth.cos(t * (float)Math.PI));
        boolean faceUp = t >= 0.5F;
        renderCard(poseStack, centreX, centreY, cardW, cardH, Math.max(0.02F, squash), faceUp);
        if(t >= 1F)
        {
            enter(Stage.REVEALED);
            if(isRare(index))
            {
                playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6F);
            }
        }
    }

    private void renderRevealed(PoseStack poseStack, Layout layout,
        int centreX, int centreY, int cardW, int cardH)
    {
        renderCard(poseStack, centreX, centreY, cardW, cardH, 1F, true);

        if(isRare(index))
        {
            // A single sweep of light across the card, once, rather than a
            // loop: it should read as "this one is special", not as an idle
            // animation the player waits out.
            shine = Mth.clamp(elapsed() / (float)layout.i("shine.ms", 700), 0F, 1F);
            drawShine(poseStack, centreX - cardW / 2, centreY - cardH / 2, cardW, cardH, shine);
        }

        Properties card = cardAt(index);
        if(card != null)
        {
            String name = card.getName() == null ? "" : card.getName();
            int y = centreY + cardH / 2 + 8;
            font.drawShadow(poseStack, name, centreX - font.width(name) / 2F, y,
                isRare(index) ? 0xFFFFD54A : 0xFFE6EAF2);
            String rarity = index < rarities.size() ? rarities.get(index) : "";
            if(!rarity.isBlank())
            {
                font.drawShadow(poseStack, rarity, centreX - font.width(rarity) / 2F, y + 11,
                    isRare(index) ? 0xFFFFB347 : 0xFF9FA6B4);
            }
        }

        String hint = index + 1 < codes.size() ? "Click for the next card" : "Click to finish";
        font.drawShadow(poseStack, hint, centreX - font.width(hint) / 2F, height - 44, 0xFF7A8090);
    }

    private void renderSummary(PoseStack poseStack, Layout layout)
    {
        int columns = Math.max(1, layout.i("summary.columns", 5));
        int cardW = layout.i("summary.cardWidth", 62);
        int cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int gap = layout.i("summary.gap", 6);
        int rows = (codes.size() + columns - 1) / columns;

        int gridW = columns * cardW + (columns - 1) * gap;
        int gridH = rows * cardH + (rows - 1) * gap;
        int startX = (width - gridW) / 2;
        int startY = Math.max(46, (height - gridH) / 2 - 6);

        for(int i = 0; i < codes.size(); i++)
        {
            int x = startX + (i % columns) * (cardW + gap);
            int y = startY + (i / columns) * (cardH + gap);
            Properties card = cardAt(i);
            if(card == null)
            {
                continue;
            }
            drawFace(poseStack, card, x, y, cardW, cardH);
            if(isRare(i))
            {
                // A still highlight rather than a sweep: several at once would
                // be a light show, and the point is only to pick them out.
                NineSlice.draw(poseStack, HubTextures.PANEL, x - 3, y - 3, cardW + 6, cardH + 6,
                    NineSlice.HOVER, 3, 0.55F);
                drawFace(poseStack, card, x, y, cardW, cardH);
            }
        }

        String hint = "Click to close";
        font.drawShadow(poseStack, hint, width / 2F - font.width(hint) / 2F, height - 44, 0xFF7A8090);
    }

    /** One card, squashed horizontally by {@code squash}, face up or down. */
    private void renderCard(PoseStack poseStack, int centreX, int centreY,
        int cardW, int cardH, float squash, boolean faceUp)
    {
        int drawnW = Math.max(1, Math.round(cardW * squash));
        int x = centreX - drawnW / 2;
        int y = centreY - cardH / 2;
        if(faceUp)
        {
            Properties card = cardAt(index);
            if(card != null)
            {
                drawFace(poseStack, card, x, y, drawnW, cardH);
                return;
            }
        }
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        DuelTextures.bindSmooth(DuelTextures.COVER);
        DdBlitUtil.fullBlit(poseStack, x, y, drawnW, cardH);
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
