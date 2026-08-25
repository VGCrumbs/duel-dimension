package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardTextureCache;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.ImageHandler;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent;
import de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent;
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
 * <p>
 * Ported to retained mode: {@code render} became {@code extractRenderState}, and
 * every {@code RenderSystem.setShaderColor}+blit became a {@link DdBlitUtil}
 * blit with a tint carrying the alpha. Scissor clipping moved from
 * {@code RenderSystem.enableScissor} to the extractor's own scissor, which
 * takes GUI-space CORNERS -- not the bottom-origin framebuffer pixels Forge's
 * did. Converting to the old convention is a real bug; see
 * {@code DeckEditorScreen.clipToDeckView}. The Forge {@code bindSmooth}
 * per-draw call is gone; card faces instead use Duel Dimension's separate
 * smooth resource path, whose texture metadata selects Minecraft 26.2's
 * bilinear sampler without changing cards drawn elsewhere.
 * <p>
 * TODO(M3): the card flip, shine sweep and rare-frame here are the honest 2D
 * squash the Forge build shipped and port straight across — but a true 3D
 * card-flip reveal, if the duel field's card renderer grows one, would replace
 * the horizontal-squash fake. Left as the faithful 2D port until then.
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

    /**
     * How far the summary grid is scrolled, in pixels, and where it is heading.
     * <p>
     * Pixels rather than a row number: a grid that moves a whole row per notch
     * jumps, and with rows a hundred pixels tall the jump is most of the
     * screen. Held as a pair so the wheel sets a destination and the drawing
     * eases toward it.
     */
    private float summaryScroll;
    private float summaryTarget;

    /** Reveals everything, then closes: its label says which it will do. */
    private HubWidgets.TextureButton skip;

    /**
     * Where closing the reveal goes back to.
     * <p>
     * Whatever was on screen when the packs were bought, which in practice is
     * the shop. Buying used to drop you out to the world, so opening ten packs
     * meant walking back to the counter nine times; the shop is where you were
     * and where you are most likely going next. Null when a pack was opened from
     * somewhere with no screen behind it, and then this closes as it always did.
     */
    private final Screen parent;

    public PackOpeningScreen(Screen parent, String setName, List<Integer> codes,
        List<String> rarities)
    {
        super(Component.literal("Opening " + setName));
        this.parent = parent;
        this.setName = setName;
        this.codes = new ArrayList<>(codes);
        this.rarities = new ArrayList<>(rarities);
        this.flip = new float[this.codes.size()];
        this.shine = new float[this.codes.size()];
    }

    @Override
    public void onClose()
    {
        if(parent != null)
        {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
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
     * <p>
     * <b>{@link ImageHandler} and not {@link DuelTextures}, deliberately.</b>
     * DuelTextures admits the ResourceLocation to {@link CardTextureCache} as it
     * hands it back, and this method throws that ResourceLocation away — so every
     * card in the pull was marked resident within a tick or two without a
     * single texture being created, and the reveal then drew nine or
     * twenty-four 512px cards that all bypassed the first-sighting ration in
     * one frame. Only the pipeline nudge is wanted here; it lives in
     * getReplacementImage.
     */
    private void requestArt()
    {
        for(int code : codes)
        {
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card != null)
            {
                ImageHandler.getReplacementImage(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE);
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
        summaryScroll = 0F;
        summaryTarget = 0F;
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
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        MouseButtonEvent event = new MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        boolean doubleClick = false;

        // The menu is tested BEFORE super, so a click landing on it cannot fall
        // through to the strip underneath and advance the pull.
        if(menuCard >= 0)
        {
            boolean handled = clickMenu(event.x(), event.y());
            menuCard = -1;
            if(handled)
            {
                return true;
            }
        }
        if(event.button() == 1)
        {
            int over = cardUnder(event.x());
            // Same rule as the shift peek: only a card already turned over. A
            // menu on a face-down card would name it before it is revealed.
            if(over >= 0 && over < codes.size() && over < flip.length && flip[over] >= 1F)
            {
                menuCard = over;
                menuX = (int)event.x();
                menuY = (int)event.y();
                return true;
            }
        }
        if(super.mouseClicked(vanillaX, vanillaY, vanillaButton))
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
        dragFromX = event.x();
        dragFromPosition = position;
        return true;
    }

    @Override
    public boolean mouseDragged(double vanillaX, double vanillaY, int vanillaButton, double dx, double dy)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        MouseButtonEvent event = new MouseButtonEvent(vanillaX, vanillaY, vanillaButton);

        double mouseX = event.x();
        if(!dragging || stage != Stage.STRIP)
        {
            return super.mouseDragged(vanillaX, vanillaY, vanillaButton, vanillaDragX, vanillaDragY);
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
    public boolean mouseReleased(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        MouseButtonEvent event = new MouseButtonEvent(vanillaX, vanillaY, vanillaButton);

        double mouseX = event.x();
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
        return super.mouseReleased(vanillaX, vanillaY, vanillaButton);
    }

    /** The card whose menu is open, or -1. */
    private int menuCard = -1;
    private int menuX;
    private int menuY;
    /** Why the last menu choice did nothing, shown until the next one. */
    private String menuNotice = "";

    private static final int MENU_ROW = 12;
    /** Narrow enough to still read as a menu when every row is short. */
    private static final int MENU_W_MIN = 130;
    /** Text inset either side, counted once so the width and the draw agree. */
    private static final int MENU_PAD = 6;

    /**
     * Wide enough for its widest row, because a deck name is whatever the
     * player typed and a fixed width cut "Add to Starter Deck: Codebreaker"
     * off mid-word. Bounded by the window so a very long name makes an
     * ellipsis rather than a menu wider than the screen.
     */
    private int menuW(java.util.List<String> rows)
    {
        int widest = 0;
        for(String row : rows)
        {
            widest = Math.max(widest, font.width(row));
        }
        int room = Math.max(MENU_W_MIN, width - 8);
        return Math.max(MENU_W_MIN, Math.min(room, widest + MENU_PAD * 2));
    }

    /** The row as it fits, tailed with an ellipsis when it does not. */
    private String fit(String row, int room)
    {
        if(font.width(row) <= room)
        {
            return row;
        }
        String tail = "...";
        int budget = room - font.width(tail);
        StringBuilder kept = new StringBuilder();
        for(int i = 0; i < row.length() && font.width(kept.toString() + row.charAt(i)) <= budget; i++)
        {
            kept.append(row.charAt(i));
        }
        return kept + tail;
    }

    /** The rows: favourite first, then one per deck the player may edit. */
    private java.util.List<String> menuRows()
    {
        java.util.List<String> rows = new java.util.ArrayList<>();
        Properties card = cardAt(menuCard);
        int code = card == null ? 0 : (int)card.getId();
        rows.add(EditorState.isFavourite(code) ? "Unfavourite" : "Favourite");
        for(de.cas_ual_ty.dueldimension.duel.profile.DeckList deck : EditorState.ownDecks())
        {
            rows.add("Add to " + deck.name());
        }
        return rows;
    }

    private void drawMenu(GuiGraphicsExtractor poseStack)
    {
        java.util.List<String> rows = menuRows();
        int panelW = menuW(rows);
        int panelH = rows.size() * MENU_ROW + 8;
        // Pulled back inside the window, so a menu opened near an edge is not
        // half off the screen and half unclickable.
        int left = Math.max(0, Math.min(menuX, width - panelW));
        int top = Math.max(0, Math.min(menuY, height - panelH));
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, panelW, panelH);
        for(int i = 0; i < rows.size(); i++)
        {
            poseStack.text(font, fit(rows.get(i), panelW - MENU_PAD * 2),
                left + MENU_PAD, top + 4 + i * MENU_ROW + 2,
                i == 0 ? 0xFFF4D089 : 0xFFC2C9D6, true);
        }
    }

    /** @return true if the click was inside the menu, whatever it chose */
    private boolean clickMenu(double mouseX, double mouseY)
    {
        java.util.List<String> rows = menuRows();
        int panelW = menuW(rows);
        int panelH = rows.size() * MENU_ROW + 8;
        int left = Math.max(0, Math.min(menuX, width - panelW));
        int top = Math.max(0, Math.min(menuY, height - panelH));
        if(mouseX < left || mouseX >= left + panelW || mouseY < top || mouseY >= top + panelH)
        {
            return false;
        }
        int row = (int)((mouseY - top - 4) / MENU_ROW);
        Properties card = cardAt(menuCard);
        if(card == null || row < 0 || row >= rows.size())
        {
            return true;
        }
        menuNotice = "";
        if(row == 0)
        {
            EditorState.toggleFavourite((int)card.getId());
            return true;
        }
        java.util.List<de.cas_ual_ty.dueldimension.duel.profile.DeckList> decks =
            EditorState.ownDecks();
        int at = row - 1;
        if(at < decks.size())
        {
            String refusal = EditorState.addToDeck(decks.get(at), (int)card.getId());
            menuNotice = refusal == null ? "" : refusal;
        }
        return true;
    }

    /** Whether either shift key is down right now, event or no event. */
    private static boolean shiftHeld()
    {
        com.mojang.blaze3d.platform.Window window =
            net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * The held-shift reading panel: a card's name and its own words.
     * <p>
     * Drawn at the bottom rather than under the cursor. The cursor is on the
     * card, and a panel following it would cover the very thing being read
     * about; down here it sits in the empty band the hint line already uses.
     */
    private void drawPeek(GuiGraphicsExtractor poseStack, Properties peek)
    {
        if(peek == null)
        {
            return;
        }
        int panelW = Math.min(width - 40, 320);
        int left = (width - panelW) / 2;
        java.util.List<net.minecraft.util.FormattedCharSequence> body =
            font.split(Component.literal(peek.getText() == null ? "" : peek.getText()),
                panelW - 12);
        // Capped so a wall of text on a long effect cannot grow up over the
        // cards themselves; the info screen is where the full thing lives.
        int rows = Math.min(body.size(), 6);
        int panelH = 16 + rows * 10 + 8;
        int top = height - 30 - panelH;

        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, panelW, panelH);
        String name = peek.getName() == null ? "" : peek.getName();
        poseStack.text(font, name, left + 6, top + 6, 0xFFF4D089, true);
        for(int i = 0; i < rows; i++)
        {
            poseStack.text(font, body.get(i), left + 6, top + 20 + i * 10, 0xFFC2C9D6, true);
        }
        if(body.size() > rows)
        {
            String more = "...";
            poseStack.text(font, more, left + panelW - 6 - font.width(more),
                top + panelH - 12, 0xFF7A8090, true);
        }
    }

    /** Which card the cursor is over, by how far it is from the middle. */
    private int cardUnder(double mouseX)
    {
        float spacing = Math.max(1F, spacing());
        return Math.round(position + (float)((mouseX - width / 2.0) / spacing));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if(stage == Stage.SUMMARY)
        {
            // A notch moves most of a row rather than exactly one, so the grid
            // does not settle back into the same alignment every time and the
            // movement reads as scrolling rather than paging.
            summaryTarget -= (float)delta * Layout.of(LAYOUT).f("summary.step", 42F);
            return true;
        }
        moveTo(focus - (int)Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        KeyEvent event = new KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);

        int key = event.key();
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
        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
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
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, the blur is once-per-frame, and the frame a screen
        // opens over another that already asked for it took the client down.
        // Same decision as EngineDuelScreen, for the same crash.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        Layout layout = Layout.of(LAYOUT);
        if(skip != null)
        {
            skip.setMessage(Component.literal(stage == Stage.SUMMARY ? "Close"
                : allTurned() ? "Summary" : "Reveal All"));
        }

        if(stage == Stage.SUMMARY)
        {
            renderSummary(poseStack, layout);
            super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
            return;
        }

        advanceAnimation(layout);
        renderStrip(poseStack, layout);

        int centreX = width / 2;
        poseStack.text(font, setName, centreX - font.width(setName) / 2, 18, 0xFFF4D089, true);
        String progress = (focus + 1) + " / " + codes.size();
        poseStack.text(font, progress, centreX - font.width(progress) / 2, 30, 0xFF9FA6B4, true);

        // Hold shift over a card you have already turned to read it, without
        // leaving the opening and losing your place in the strip.
        // Not Screen.hasShiftDown(): that reads the modifier carried by a key
        // or mouse EVENT, and nothing is being pressed here -- the player is
        // just holding shift while moving the cursor. This asks the window
        // directly, the same way the deck editor's shift-scroll does.
        if(shiftHeld())
        {
            int over = cardUnder(mouseX);
            // flip >= 1 means FULLY turned. Anything less is still face down or
            // mid-turn, and showing its text would hand the player the reveal
            // before the card does -- the one thing this screen exists for.
            if(over >= 0 && over < codes.size() && over < flip.length && flip[over] >= 1F)
            {
                drawPeek(poseStack, cardAt(over));
            }
        }

        if(menuCard >= 0)
        {
            drawMenu(poseStack);
        }
        if(!menuNotice.isEmpty())
        {
            poseStack.text(font, menuNotice, width / 2 - font.width(menuNotice) / 2,
                height - 34, 0xFFE08A8A, true);
        }

        String hint = allTurned() ? "Click past the end for the summary"
            : "Click, scroll or press Space";
        poseStack.text(font, hint, centreX - font.width(hint) / 2, height - 22, 0xFF7A8090, true);

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
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
    private void renderStrip(GuiGraphicsExtractor poseStack, Layout layout)
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
    private void drawCard(GuiGraphicsExtractor poseStack, int index, int centreX, int y,
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

        int tint = DdBlitUtil.alpha(alpha);
        Properties card = faceUp ? cardAt(index) : null;
        if(card != null)
        {
            // Sampled out of the letterboxed square, as everywhere else, or the
            // art stretches. The window is now given as its two corners.
            DdBlitUtil.blit(poseStack, DuelTextures.cardSmooth(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
                x, y, drawnW, h,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, tint);
        }
        else
        {
            DdBlitUtil.fullBlit(poseStack, DuelTextures.COVER, x, y, drawnW, h, tint);
        }

        if(faceUp && isRare(index) && shine[index] < 1F)
        {
            drawShine(poseStack, x, y, drawnW, h, shine[index]);
        }
    }

    private void renderSummary(GuiGraphicsExtractor poseStack, Layout layout)
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
        int pitch = cardH + gap;
        // The scroll is in pixels over the whole grid, so the last row can come
        // to rest against the bottom edge instead of the grid stopping a row
        // early because a row is the smallest unit it can move.
        float maxScroll = Math.max(0F, rows * pitch - gap - usableH);
        summaryTarget = Mth.clamp(summaryTarget, 0F, maxScroll);
        summaryScroll += (summaryTarget - summaryScroll)
            * Mth.clamp(layout.f("summary.glide", 0.3F), 0.02F, 1F);
        if(Math.abs(summaryTarget - summaryScroll) < 0.4F)
        {
            summaryScroll = summaryTarget;
        }

        int firstRow = (int)(summaryScroll / pitch);
        // What is left over after the whole rows: the offset that makes the top
        // row slide rather than snap.
        int slide = Math.round(summaryScroll - firstRow * pitch);
        int visibleRows = Math.max(1, (usableH + gap) / pitch);

        int gridW = columns * cardW + (columns - 1) * gap;
        int startX = (width - gridW) / 2;

        String title = codes.size() + " cards from " + setName;
        poseStack.text(font, title, width / 2 - font.width(title) / 2, 22, 0xFFF4D089, true);

        // Clipped to the grid's own strip, so a row halfway off the top is cut
        // rather than drawn over the title.
        clipToSummary(poseStack, top, usableH, true);
        // One row more than fits, because scrolling means a partial row at each
        // end rather than a whole number of them.
        for(int row = 0; row <= visibleRows; row++)
        {
            for(int column = 0; column < columns; column++)
            {
                int index = (row + firstRow) * columns + column;
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
                int y = top + row * pitch - slide;
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

        clipToSummary(poseStack, top, usableH, false);

        // Only claim there is more when there is, and say how much.
        if(maxScroll > 0F)
        {
            String position = "scroll  " + Math.min(codes.size(),
                (firstRow + visibleRows) * columns) + " / " + codes.size();
            poseStack.text(font, position, width / 2 - font.width(position) / 2,
                height - 30, 0xFF7A8090, true);
        }
        String hint = "Click to close";
        poseStack.text(font, hint, width / 2 - font.width(hint) / 2, height - 18, 0xFF9FA6B4, true);
    }

    /**
     * Limits drawing to the summary's own strip, or lifts that limit.
     * <p>
     * Scissor coordinates are real framebuffer pixels measured from the BOTTOM
     * of the window, while everything else here is scaled GUI pixels from the
     * top, so the rectangle is converted rather than passed through. The scissor
     * itself is the extractor's now rather than {@code RenderSystem}'s.
     */
    private void clipToSummary(GuiGraphicsExtractor poseStack, int top, int height, boolean on)
    {
        if(!on)
        {
            poseStack.disableScissor();
            return;
        }
        // GUI-space corners; see DeckEditorScreen.clipToDeckView for why the
        // framebuffer conversion this used to do put the rectangle wrong.
        poseStack.enableScissor(0, top, this.width, top + height);
    }

    private void drawFace(GuiGraphicsExtractor poseStack, Properties card, int x, int y, int w, int h)
    {
        // Sampled out of the letterboxed square, as everywhere else, or the art
        // stretches.
        DdBlitUtil.blit(poseStack, DuelTextures.cardSmooth(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
            x, y, w, h,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
    }

    /**
     * A band of light crossing the card once. Drawn from white.png at low alpha
     * and clipped to the card's own rectangle, so it reads as a sheen on the
     * card rather than a shape floating over it.
     */
    private void drawShine(GuiGraphicsExtractor poseStack, int x, int y, int w, int h, float progress)
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
        DdBlitUtil.fullBlit(poseStack, DuelTextures.WHITE, left, y, right - left, h,
            DdBlitUtil.tint(1F, 0.97F, 0.8F, Math.max(0F, alpha)));
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
