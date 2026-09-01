package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardImageManager;
import de.cas_ual_ty.dueldimension.clientutil.CardTextureCache;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.ImageHandler;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

    /**
     * Which revealed cards the collection did not already hold.
     * <p>
     * The server's answer, taken before it added them. Read by index against
     * {@link #codes}, and short or empty is fine -- it reads as "not new",
     * which is the safe way round for a badge.
     */
    private final List<Boolean> fresh;

    /**
     * Which set each card came out of, parallel to {@link #codes}.
     * <p>
     * A tin is several real booster packs in a box, and this is the only thing
     * that says which pack a card was in -- the pull happened server side and a
     * card records its printing, not its packet. Empty, short, or all one value
     * for every ordinary product, which is what {@link #groups} reads as "not a
     * tin" and lays out exactly as it always did.
     */
    private final List<String> sources;

    /**
     * The card runs, one per pack, in pull order: {@code {firstIndex, count}}.
     * <p>
     * Empty when the cards did not come from more than one set. Built from
     * consecutive runs rather than by collecting equal codes, because a tin
     * holding two Ancient Prophecy packs opened two SEPARATE packs -- grouping
     * by set would merge them into one row of eighteen and lose the fact that
     * the product contained two.
     */
    private final List<int[]> groups = new ArrayList<>();

    public PackOpeningScreen(Screen parent, String setName, List<Integer> codes,
        List<String> rarities, List<Boolean> fresh)
    {
        this(parent, setName, codes, rarities, fresh, List.of());
    }

    public PackOpeningScreen(Screen parent, String setName, List<Integer> codes,
        List<String> rarities, List<Boolean> fresh, List<String> sources)
    {
        super(Component.literal("Opening " + setName));
        this.parent = parent;
        this.setName = setName;
        this.codes = new ArrayList<>(codes);
        this.rarities = new ArrayList<>(rarities);
        this.fresh = new ArrayList<>(fresh == null ? List.of() : fresh);
        this.sources = new ArrayList<>(sources == null ? List.of() : sources);
        this.flip = new float[this.codes.size()];
        this.shine = new float[this.codes.size()];
        buildGroups();
    }

    /**
     * Splits the pull into one run per pack.
     * <p>
     * Left empty unless the cards really did come from more than one set: a
     * product whose cards are all its own is not a tin, and drawing one icon
     * above one row would be a worse version of the layout it already has.
     */
    private void buildGroups()
    {
        if(sources.size() < codes.size())
        {
            return;
        }
        boolean varies = false;
        for(String source : sources)
        {
            if(source != null && !source.isEmpty() && !source.equals(sources.get(0)))
            {
                varies = true;
                break;
            }
        }
        if(!varies)
        {
            return;
        }
        int start = 0;
        for(int i = 1; i <= codes.size(); i++)
        {
            if(i == codes.size() || !sources.get(i).equals(sources.get(start)))
            {
                groups.add(new int[] {start, i - start});
                start = i;
            }
        }
    }

    /** The set a group came from, for its icon and its label. */
    private de.cas_ual_ty.dueldimension.set.CardSet groupSet(int[] group)
    {
        String code = group[0] < sources.size() ? sources.get(group[0]) : "";
        if(code == null || code.isEmpty())
        {
            return null;
        }
        // The trailing "#n" is the pack's position in the tin, which is what
        // kept two packs of one booster apart. It is not part of the code.
        int tag = code.indexOf(
            de.cas_ual_ty.dueldimension.set.CompositionCardPuller.PACK_TAG);
        return de.cas_ual_ty.dueldimension.shop.ShopStock.setOf(
            tag < 0 ? code : code.substring(0, tag));
    }

    @Override
    public void onClose()
    {
        if(parent != null)
        {
            minecraft.setScreenAndShow(parent);
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
     * DuelTextures admits the Identifier to {@link CardTextureCache} as it
     * hands it back, and this method throws that Identifier away — so every
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
        return isNotable(rarity);
    }

    /**
     * Rarities worth a frame and a shine: Super Rare and above.
     *
     * <h2>Why not "anything that is not Common"</h2>
     * That was the rule, on the reasoning that rarity names vary by set so
     * asking what a card is NOT would age better than a list. It ages fine and
     * is still wrong, because "not Common" is not the same as "notable". Across
     * the shipped set data there are 3,789 plain Rares, 297 Shatterfoil, 440
     * Starfoil, 215 Mosaic, 364 Short Prints and 740 Duel Terminal parallels --
     * every one of which is a base-rarity pull wearing a foil.
     * <p>
     * Battle Pack 3 is the case that showed it: 165 Common, 55 Rare and 237
     * Shatterfoil Rare. Over half the set tripped the test, so almost every card
     * in a pack came out framed -- and a highlight that fires on everything is
     * not a highlight, it reads as a border someone forgot to remove.
     * <p>
     * So it is a keyword list after all, of the tiers that ARE special. A
     * rarity nobody here has seen gets no frame, which is the safe way round:
     * a missed highlight is invisible, a spurious one looks like a bug.
     */
    private static boolean isNotable(String rarity)
    {
        if(rarity == null || rarity.isBlank())
        {
            return false;
        }
        String lower = rarity.toLowerCase(java.util.Locale.ROOT);
        for(String word : NOTABLE)
        {
            if(lower.contains(word))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The tiers that earn a highlight, by the word that identifies them.
     * <p>
     * Substrings rather than whole names, because the compounds are endless --
     * "Prismatic Secret Rare", "Duel Terminal Super Parallel Rare", "Ghost Gold
     * Rare" -- and every one of them is notable for the word it contains.
     */
    private static final String[] NOTABLE = {
        "super", "ultra", "ultimate", "secret", "ghost", "collector",
        "starlight", "platinum", "prismatic", "gold", "quarter century",
        "grand master", "10000",
    };

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
            revealBetween(focus, clamped);
            focus = clamped;
        }
    }

    /**
     * Turns over every card the focus just jumped across.
     * <p>
     * <b>The skip this fixes.</b> Only the FOCUSED card is turned, by
     * {@link #advanceAnimation}, and only once the strip has eased close enough
     * to it. Scroll two notches before the glide catches up and the focus goes
     * from three to five while the strip is still somewhere near three -- so
     * card four is slid past and never turned, and stays face down through the
     * rest of the opening. Scroll fast down a sixteen-card pack and most of it
     * arrives at the summary unrevealed. The faster you scroll the more you
     * miss, which is the opposite of what scrolling faster should do.
     * <p>
     * So anything stepped over is turned immediately: passing a card counts as
     * seeing it. The destination still animates normally -- it is the one being
     * looked at -- and the sound is left to that one, because a page turn per
     * card across a fast scroll is a noise, not feedback.
     */
    private void revealBetween(int from, int to)
    {
        int lo = Math.min(from, to);
        int hi = Math.max(from, to);
        for(int i = lo; i <= hi; i++)
        {
            if(i != to && i >= 0 && i < flip.length)
            {
                flip[i] = 1F;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        // The menu is tested BEFORE super, so a click landing on it cannot fall
        // through to the strip underneath and advance the pull.
        if(menuGroup >= 0)
        {
            boolean handled = clickPackMenu(event.x(), event.y());
            menuGroup = -1;
            if(handled)
            {
                return true;
            }
        }
        if(menuCard >= 0)
        {
            boolean handled = clickMenu(event.x(), event.y());
            menuCard = -1;
            if(handled)
            {
                return true;
            }
        }
        if(event.button() == 1 && stage == Stage.SUMMARY)
        {
            int overCard = summaryCardUnder(event.x(), event.y());
            if(overCard >= 0)
            {
                menuCard = overCard;
                menuGroup = -1;
                menuX = (int)event.x();
                menuY = (int)event.y();
                return true;
            }
            int overPack = summaryPackUnder(event.x(), event.y());
            if(overPack >= 0)
            {
                menuGroup = overPack;
                menuCard = -1;
                menuX = (int)event.x();
                menuY = (int)event.y();
                return true;
            }
            return true;
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
        if(super.mouseClicked(event, doubleClick))
        {
            return true;
        }
        if(stage == Stage.SUMMARY)
        {
            // Deliberately NOT a close. The summary is the one screen a player
            // reads rather than dismisses -- starring a card, peeking at a
            // description, scrolling a tin's six packs -- and closing it on any
            // stray click threw all of that away. The Close button and Escape
            // are the ways out, and both are on screen.
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
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy)
    {
        double mouseX = event.x();
        if(!dragging || stage != Stage.STRIP)
        {
            return super.mouseDragged(event, dx, dy);
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
    public boolean mouseReleased(MouseButtonEvent event)
    {
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
        return super.mouseReleased(event);
    }

    /**
     * Which pack's icon a menu is open on, as an index into {@link #groups},
     * or -1.
     * <p>
     * Separate from {@link #menuCard} rather than a mode on it: the two name
     * different things -- a printing and a product -- and are starred through
     * different messages against different keys, a passcode and a set code.
     */
    private int menuGroup = -1;

    /** Where each pack's icon was drawn: {x, y, w, h, groupIndex}. */
    private final java.util.List<int[]> summaryIcons = new java.util.ArrayList<>();

    /** The pack whose icon is under the cursor, or -1. */
    private int summaryPackUnder(double mouseX, double mouseY)
    {
        for(int i = summaryIcons.size() - 1; i >= 0; i--)
        {
            int[] icon = summaryIcons.get(i);
            if(mouseX >= icon[0] && mouseX < icon[0] + icon[2]
                && mouseY >= icon[1] && mouseY < icon[1] + icon[3])
            {
                return icon[4];
            }
        }
        return -1;
    }

    private de.cas_ual_ty.dueldimension.set.CardSet menuPackSet()
    {
        return menuGroup >= 0 && menuGroup < groups.size()
            ? groupSet(groups.get(menuGroup)) : null;
    }

    private java.util.List<String> packMenuRows()
    {
        de.cas_ual_ty.dueldimension.set.CardSet set = menuPackSet();
        boolean starred = set != null
            && EditorState.profile() != null
            && EditorState.profile().isFavouritePack(set.code);
        return java.util.List.of(starred ? "Unfavourite pack" : "Favourite pack");
    }

    /**
     * Acts on a click in the pack menu, or reports that it missed.
     * <p>
     * Sent rather than applied, as the card star is: the profile is the
     * server's, and the mark appears when the answer syncs back.
     */
    private boolean clickPackMenu(double mouseX, double mouseY)
    {
        java.util.List<String> rows = packMenuRows();
        int panelW = menuW(rows);
        int panelH = rows.size() * MENU_ROW + 8;
        int left = Math.max(0, Math.min(menuX, width - panelW));
        int topEdge = Math.max(0, Math.min(menuY, height - panelH));
        if(mouseX < left || mouseX >= left + panelW
            || mouseY < topEdge || mouseY >= topEdge + panelH)
        {
            return false;
        }
        de.cas_ual_ty.dueldimension.set.CardSet set = menuPackSet();
        if(set != null && set.code != null)
        {
            EditorState.toggleFavouritePack(set.code);
        }
        return true;
    }

    /** The pack menu, drawn last so nothing is over it. */
    private void drawPackMenu(GuiGraphicsExtractor poseStack)
    {
        if(menuGroup < 0)
        {
            return;
        }
        java.util.List<String> rows = packMenuRows();
        int panelW = menuW(rows);
        int panelH = rows.size() * MENU_ROW + 8;
        int left = Math.max(0, Math.min(menuX, width - panelW));
        int topEdge = Math.max(0, Math.min(menuY, height - panelH));
        NineSlice.draw(poseStack, HubTextures.PANEL, left, topEdge, panelW, panelH);
        for(int i = 0; i < rows.size(); i++)
        {
            poseStack.text(font, rows.get(i), left + MENU_PAD,
                topEdge + 4 + i * MENU_ROW + 2, MenuInk.body(), MenuInk.shadow());
        }
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
                i == 0 ? MenuInk.title() : MenuInk.body(), MenuInk.shadow());
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
    /**
     * How far the peek panel's rules text is scrolled, in lines.
     * <p>
     * Kept with {@link #peekCard} rather than on its own: the panel shows
     * whatever the cursor is over, so an offset that outlived the card it was
     * taken on would open the next card halfway down its own effect.
     */
    private int peekScroll;
    private int peekCard = -1;

    /**
     * Where the reading panel starts: level with the focused card's own text
     * box.
     * <p>
     * The same geometry {@code renderStrip} lays the middle card out with, so
     * the two cannot drift apart. There is no cap on lines any more -- the
     * layout in drawPeek takes the room between here and the window's foot and
     * fits as many as go.
     */
    private int peekTop()
    {
        Layout layout = Layout.of(LAYOUT);
        int cardH = Math.round(cardWidth() / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int centreY = height / 2 - 6;
        return centreY - cardH / 2 + Math.round(cardH * CARD_TEXT_TOP);
    }

    /**
     * How much of the window is left below the panel.
     * <p>
     * The panel used to stop 30 above the bottom to clear the "Click, scroll or
     * press Space" hint, which cost four lines of every card's effect to keep
     * visible a sentence the player has already read by the time they are
     * holding shift to read a card. It covers the hint instead, and now that
     * the scroll bar has replaced the counter that used to sit under the panel,
     * there is nothing left to leave room for either.
     */
    private static final int PEEK_BOTTOM = 2;

    /**
     * How far the scroll bar sits from the window's right edge, and how wide it
     * is.
     * <p>
     * OUTSIDE the panel now. A gutter inside it cost six pixels of every line
     * on every card, scrolling or not, and put the bar in the middle of the
     * screen where nothing else in the interface has one. Against the window's
     * edge it costs the text nothing and lands where a scroll bar is looked
     * for.
     */
    private static final int PEEK_BAR_INSET = 3;
    private static final int PEEK_BAR_W = 4;

    /**
     * Where a real card's text box starts, as a fraction of the card's height.
     * <p>
     * The reading panel is lined up with it, so it begins exactly where the
     * card it is transcribing begins -- covering the printed text and never the
     * artwork.
     */
    private static final float CARD_TEXT_TOP = 0.72F;

    /**
     * Points the peek at a card, forgetting where the last one was scrolled to.
     * <p>
     * Called with the index under the cursor every frame the panel is up.
     */
    private void peekAt(int index)
    {
        if(index != peekCard)
        {
            peekCard = index;
            peekScroll = 0;
        }
    }

    private void drawPeek(GuiGraphicsExtractor poseStack, Properties peek)
    {
        if(peek == null)
        {
            return;
        }
        // AS WIDE AS THE CARD NEEDS, rather than a fixed 340. The panel takes
        // the longer of the name and the row of fact plates and the description
        // wraps to that, so a long name gets the room to print it and a short
        // one stops being given a slab it does not fill.
        int panelW = CardInfoPanel.preferredWidth(font, peek, width - 40, false);
        int left = (width - panelW) / 2;
        // TOP FROM THE CARD, HEIGHT FROM WHAT IS LEFT.
        //
        // The other way round -- height from the contents, top from the bottom
        // edge -- made a long effect climb up over the artwork, and capped the
        // description at a fixed number of lines whatever the window could hold.
        // Anchored to the card's own text box, the room is whatever lies
        // between there and the foot of the window.
        int top = peekTop();
        // The bar goes against the WINDOW's edge rather than inside the panel:
        // a gutter inside cost six pixels of every line on every card, and put
        // a scroll bar in the middle of the screen. This is the one caller that
        // can pin it there, because this is the one panel that does not move.
        int barX = width - PEEK_BAR_INSET - PEEK_BAR_W;
        CardInfoPanel.Layout at = CardInfoPanel.draw(poseStack, font, peek,
            left, top, panelW, height - PEEK_BOTTOM - top, peekScroll, barX, false);
        // Clamped here rather than where the scroll happens, because the panel
        // is the only thing that knows how long this card's text actually is.
        peekScroll = Mth.clamp(peekScroll, 0, at.maxScroll());
    }

    /** Which card the cursor is over, by how far it is from the middle. */
    /**
     * Which summary tile the cursor is over, or -1.
     * <p>
     * Walked backwards, so the tile drawn LAST wins where two overlap. They do
     * not overlap today, and a hit test that disagrees with what is on top is
     * the kind of thing that only starts mattering after someone changes the
     * layout.
     */
    private int summaryCardUnder(double mouseX, double mouseY)
    {
        for(int i = summaryTiles.size() - 1; i >= 0; i--)
        {
            int[] tile = summaryTiles.get(i);
            if(mouseX >= tile[0] && mouseX < tile[0] + tile[2]
                && mouseY >= tile[1] && mouseY < tile[1] + tile[3])
            {
                return tile[4];
            }
        }
        return -1;
    }

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
            // Shift is already what opens the reading panel, so while it is
            // held the wheel belongs to that panel rather than to the list
            // underneath -- scrolling the grid would move the card out from
            // under the cursor and close the very thing being read.
            if(shiftHeld() && summaryCardUnder(mouseX, mouseY) >= 0)
            {
                peekScroll -= (int)Math.signum(delta);
                return true;
            }
            // A notch moves most of a row rather than exactly one, so the grid
            // does not settle back into the same alignment every time and the
            // movement reads as scrolling rather than paging.
            summaryTarget -= (float)delta * Layout.of(LAYOUT).f("summary.step", 42F);
            return true;
        }
        // The same rule the summary above follows, for the same reason: while
        // the reading panel is open the wheel is the panel's, not the strip's.
        // Without this the wheel walked to the next card -- which moves the
        // card out from under the cursor and closes the very text being read,
        // so a long effect could be opened and never scrolled.
        //
        // Gated on the SAME condition that decides whether the panel is drawn,
        // deliberately: a wheel that scrolled a panel nobody could see would be
        // a wheel that had stopped turning pages for no visible reason.
        if(shiftHeld())
        {
            int over = cardUnder(mouseX);
            if(over >= 0 && over < codes.size() && over < flip.length && flip[over] >= 1F)
            {
                peekScroll -= (int)Math.signum(delta);
                return true;
            }
        }
        moveTo(focus - (int)Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
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
        return super.keyPressed(event);
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
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
    {
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
            // The same reading panel the strip offers, on the same key. A
            // summary tile is small enough that its rules text is decoration
            // rather than something you can read, so the one screen where every
            // card is on show at once is exactly where this is wanted.
            if(shiftHeld())
            {
                int over = summaryCardUnder(mouseX, mouseY);
                peekAt(over);
                if(over >= 0)
                {
                    int peek = over;
                    de.cas_ual_ty.dueldimension.clientutil.Layering.foreground(poseStack, () -> drawPeek(poseStack, cardAt(peek)));
                }
            }
            super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
            // After super, so a menu opened near the Close button is drawn over
            // it rather than under. Without these two the summary's right-click
            // set the menu and nothing ever drew it -- this branch returns
            // before the strip's own menu draw further down.
            if(menuCard >= 0)
            {
                de.cas_ual_ty.dueldimension.clientutil.Layering.foreground(poseStack, () -> drawMenu(poseStack));
            }
            if(menuGroup >= 0)
            {
                de.cas_ual_ty.dueldimension.clientutil.Layering.foreground(poseStack, () -> drawPackMenu(poseStack));
            }
            return;
        }

        advanceAnimation(layout);
        renderStrip(poseStack, layout);

        int centreX = width / 2;
        poseStack.text(font, setName, centreX - font.width(setName) / 2, 18, MenuInk.title(), MenuInk.shadow());
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
            boolean readable = over >= 0 && over < codes.size() && over < flip.length
                && flip[over] >= 1F;
            // Point the peek at it, the way the summary does. This was missing
            // and cost nothing while the strip's panel could not be scrolled;
            // now that the wheel reaches it, a scroll kept from the last card
            // would open this one halfway down its own effect -- which is the
            // whole reason peekAt exists.
            peekAt(readable ? over : -1);
            if(readable)
            {
                int peek = over;
                de.cas_ual_ty.dueldimension.clientutil.Layering.foreground(poseStack, () -> drawPeek(poseStack, cardAt(peek)));
            }
        }

        if(menuCard >= 0)
        {
            de.cas_ual_ty.dueldimension.clientutil.Layering.foreground(poseStack, () -> drawMenu(poseStack));
        }
        if(menuGroup >= 0)
        {
            de.cas_ual_ty.dueldimension.clientutil.Layering.foreground(poseStack, () -> drawPackMenu(poseStack));
        }
        if(!menuNotice.isEmpty())
        {
            poseStack.text(font, menuNotice, width / 2 - font.width(menuNotice) / 2,
                height - 34, 0xFFE08A8A, true);
        }

        String hint = allTurned() ? "Click past the end for the summary"
            : "Click, scroll or press Space";
        poseStack.text(font, hint, centreX - font.width(hint) / 2, height - 22, MenuInk.dim(), MenuInk.shadow());

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
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
            drawNewBadge(poseStack, index, x, y, drawH, flip[index]);
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


    /**
     * Where every summary tile was drawn last frame, as {x, y, w, h, index}.
     * <p>
     * Recorded rather than recomputed: the grid's columns, cell size and scroll
     * offset are all worked out inside {@code renderSummary}, and a hit test
     * that derived them a second time would be a copy that drifts. The shift
     * peek needs to know which tile the cursor is on, and this is the cheapest
     * honest way to tell it.
     */
    private final java.util.List<int[]> summaryTiles = new java.util.ArrayList<>();

    /**
     * The summary of a product that was several packs: each pack's icon at the
     * left of its own band, that pack's cards laid out to the right.
     * <p>
     * A tin held five real boosters, and a flat grid of forty-five cards says
     * nothing about which came from where -- which is the whole of what a tin
     * IS. One band per pack, in the order they were opened, so two Ancient
     * Prophecy packs read as two packs rather than as one double-length row.
     * <p>
     * Runs only when {@link #groups} is non-empty, so every ordinary product
     * keeps the plain grid.
     */
    private void renderGroupedSummary(GuiGraphicsExtractor poseStack, Layout layout)
    {
        int pad = layout.i("summary.pad", 14);
        int gap = layout.i("summary.gap", 6);
        int top = layout.i("summary.top", 46);
        int bottom = layout.i("summary.bottom", 40);

        int usableW = width - pad * 2;
        int usableH = height - top - bottom;

        // Measured twice: once at the roomy size, and again tighter if that
        // turned out to scroll. One extra pass, and it cannot oscillate --
        // the second result is used whatever it comes to.
        int preferred = preferredCardWidth(layout);
        // The icon is square and as tall as a card, so a band is exactly one
        // card tall when the pack held few enough to fit on one line.
        int iconW = Math.round(preferred / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int cardsW = usableW - iconW - gap * 2 - BAND_PAD * 2;
        int columns = Math.max(1, (cardsW + gap) / (preferred + gap));
        int cardW = Math.max(16, (cardsW - (columns - 1) * gap) / columns);
        int cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int total = bandsHeight(columns, cardH + gap, gap);
        if(total > usableH)
        {
            preferred = preferredCardWidth(layout, true);
            iconW = Math.round(preferred / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
            cardsW = usableW - iconW - gap * 2 - BAND_PAD * 2;
            columns = Math.max(1, (cardsW + gap) / (preferred + gap));
            cardW = Math.max(16, (cardsW - (columns - 1) * gap) / columns);
            cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
            total = bandsHeight(columns, cardH + gap, gap);
        }
        int pitch = cardH + gap;

        float maxScroll = Math.max(0F, total - usableH);
        // No fade at all when nothing can move: a strip that fits has no edge
        // to run off, so dimming its top row is a gradient over nothing.
        int feather = maxScroll > 0F ? FEATHER : 0;
        int badgeFeather = maxScroll > 0F ? BADGE_FEATHER : 0;
        summaryTarget = Mth.clamp(summaryTarget, 0F, maxScroll);
        summaryScroll += (summaryTarget - summaryScroll)
            * Mth.clamp(layout.f("summary.glide", 0.3F), 0.02F, 1F);
        if(Math.abs(summaryTarget - summaryScroll) < 0.4F)
        {
            summaryScroll = summaryTarget;
        }
        setFade(summaryScroll, maxScroll);

        String title = codes.size() + " cards from " + setName;
        poseStack.text(font, title, width / 2 - font.width(title) / 2, 22, MenuInk.title(), MenuInk.shadow());

        java.util.List<int[]> badges = new java.util.ArrayList<>();
        summaryTiles.clear();
        summaryIcons.clear();

        // Inside the container, and after the icon's column.
        int cardsX = pad + BAND_PAD + iconW + gap * 2;
        clipToSummary(poseStack, top, usableH, true);
        int bandY = top - Math.round(summaryScroll);
        for(int[] group : groups)
        {
            int rows = Math.max(1, (group[1] + columns - 1) / columns);
            int contentH = rows * pitch - gap;
            int bandH = contentH + BAND_PAD * 2;
            // Whole bands off either edge cost nothing but still advance the
            // cursor, so the scroll stays in step with the content.
            if(bandY + bandH >= top && bandY <= top + usableH)
            {
                // One container per pack, so the screen reads as a stack of
                // packs rather than as a grid with pictures down the side --
                // which is the whole point of showing a tin's contents this
                // way.
                // The container fades with its own band. It is drawn inside a
                // scissor widened by the feather, so at full opacity it would
                // be the one thing still hitting a hard edge -- the very cut
                // the fade exists to remove.
                featheredPanel(poseStack, HubTextures.PANEL_INSET,
                    pad, bandY, usableW, bandH, top, top + usableH, feather, 1F);
                // Centred against its own cards, not hung from the top of the
                // band: a pack whose cards wrap onto two rows is twice as tall
                // as the icon, and a top-aligned icon then sits against the
                // first row with nothing beside the second.
                // The icon and its label are centred as one block, and the
                // icon is shrunk to leave the label room -- otherwise a band
                // exactly one card tall has the name written past its own
                // container and over the pack below.
                int nameH = nameHeight(group, iconW);
                int iconDraw = Math.max(16, Math.min(iconW, contentH - nameH));
                int iconY = bandY + BAND_PAD + (contentH - iconDraw - nameH) / 2;
                drawGroupIcon(poseStack, group, pad + BAND_PAD, iconY, iconDraw, iconW,
                    top, top + usableH);
                summaryIcons.add(new int[] {pad + BAND_PAD, iconY, iconW,
                    iconDraw + nameH, groups.indexOf(group)});
                for(int k = 0; k < group[1]; k++)
                {
                    int index = group[0] + k;
                    Properties card = cardAt(index);
                    if(card == null)
                    {
                        continue;
                    }
                    int x = cardsX + (k % columns) * (cardW + gap);
                    int y = bandY + BAND_PAD + (k / columns) * pitch;
                    drawFace(poseStack, card, x, y, cardW, cardH, top, top + usableH);
                    if(isRare(index))
                    {
                        featheredHover(poseStack, x - 3, y - 3, cardW + 6, cardH + 6,
                            top, top + usableH);
                        drawFace(poseStack, card, x, y, cardW, cardH, top, top + usableH);
                    }
                    summaryTiles.add(new int[] {x, y, cardW, cardH, index});
                    if(index < fresh.size() && Boolean.TRUE.equals(fresh.get(index)))
                    {
                        badges.add(new int[] {x + (cardW - summaryBadgeW(cardW)) / 2,
                            y - summaryBadgeH(cardW) / 2 - 2, y});
                    }
                }
            }
            bandY += bandH + BAND_GAP;
        }
        clipToSummary(poseStack, top, usableH, false);
        drawBadges(poseStack, badges, top, usableH, cardH);

    }

    /**
     * The NEW badges, once nothing is clipped, so each may hang over the edge
     * of its tile the way it does on the strip.
     * <p>
     * A second pass rather than a wider scissor: widening it lets the TILES
     * bleed too, so a row scrolling off the top creeps out from under the clip
     * instead of being cut. Here only the badges escape, and each only while
     * its own tile is still on the strip.
     */
    private void drawBadges(GuiGraphicsExtractor poseStack, java.util.List<int[]> badges,
        int top, int usableH, int cardH)
    {
        // Proportioned on the tile's WIDTH, which is what the badge sits across.
        int cardW = Math.round(cardH * DuelTextures.CARD_ASPECT);
        for(int[] badge : badges)
        {
            float alpha = featherAt(badge[1] + summaryBadgeH(cardW) / 2, top, top + usableH,
                BADGE_FEATHER);
            if(alpha <= 0F)
            {
                continue;
            }
            // Drawn at the file's own size inside a scaled matrix, so every
            // texel gets the same whole number of screen pixels.
            float scale = badgeScale(cardW);
            poseStack.pose().pushMatrix();
            poseStack.pose().scale(scale, scale);
            DdBlitUtil.fullBlit(poseStack, HubTextures.NEW_BADGE,
                Math.round(badge[0] / scale), Math.round(badge[1] / scale),
                HubTextures.NEW_BADGE_W, HubTextures.NEW_BADGE_H, DdBlitUtil.alpha(alpha));
            poseStack.pose().popMatrix();
        }
    }

    /**
     * Screen pixels per font pixel for the pack label.
     * <p>
     * <b>A whole number, which is what makes the label integer-scaled.</b> A
     * glyph is drawn at {@code steps / guiScale}, so every font pixel lands on
     * exactly {@code steps} screen pixels and none is dropped or doubled -- the
     * previous 0.5 was 1.5 screen pixels per font pixel at a GUI scale of 3,
     * which is what made the small text look uneven.
     * <p>
     * Two rather than one: one step is the font at its native size, which is
     * too small to read under an icon. It also falls out that the label is the
     * same physical size at every GUI scale, since the scale cancels.
     */
    private static final int NAME_FONT_STEPS = 2;

    /** At most this many lines, so a long product name cannot eat the band. */
    private static final int NAME_MAX_LINES = 3;

    private float nameScale()
    {
        return (float)(NAME_FONT_STEPS / guiScale());
    }

    /**
     * A pack's name, wrapped to its icon column.
     * <p>
     * Wrapped rather than trimmed: the column is a card wide and product names
     * are not, so trimming cut most of them mid-word -- "2019 Gold Sarcophagus
     * Tin M" says less than the same words over two lines.
     */
    private java.util.List<net.minecraft.util.FormattedCharSequence> nameLines(
        int[] group, int columnW)
    {
        de.cas_ual_ty.dueldimension.set.CardSet set = groupSet(group);
        if(set == null || set.name == null)
        {
            return java.util.List.of();
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(
            Component.literal(set.name), Math.max(8, Math.round(columnW / nameScale())));
        return lines.size() <= NAME_MAX_LINES ? lines : lines.subList(0, NAME_MAX_LINES);
    }

    private int nameHeight(int[] group, int columnW)
    {
        return Math.round(nameLines(group, columnW).size() * font.lineHeight * nameScale()) + 2;
    }

    /**
     * How tall the bands come to at this column count and row pitch.
     *
     * <h2>Term for term with the draw, deliberately</h2>
     * This is the number the scroll limit comes from, so any disagreement with
     * what {@code renderGroupedSummary} actually lays out shows up as a summary
     * that cannot be scrolled all the way to its last row. It got there by
     * subtracting ONE {@code pitch} for the whole list where the draw drops a
     * {@code gap} from EVERY band -- and pitch is {@code cardH + gap}, so on a
     * four-band tin with tall cards the total came out well over a hundred
     * pixels short and the bottom band could not be reached.
     * <p>
     * It could not have been right, either: {@code gap} is not recoverable from
     * {@code pitch} alone, and this was never handed one. That is why the
     * parameter is here now.
     * <p>
     * So each term below names the line in the loop it accounts for, and the
     * only arithmetic is the sum. The two dead terms that used to sit at the
     * end -- {@code Math.round(pitch * 0F)} and {@code pitch - Math.max(1,
     * pitch)}, both identically zero -- are gone with them.
     */
    private int bandsHeight(int columns, int pitch, int gap)
    {
        int total = 0;
        for(int[] group : groups)
        {
            int rows = Math.max(1, (group[1] + columns - 1) / columns);
            // contentH, then bandH, then the advance -- the same three lines
            // the loop runs, in the same order.
            total += rows * pitch - gap + BAND_PAD * 2 + BAND_GAP;
        }
        // Nothing follows the last band, so it is owed no gap after it.
        return Math.max(0, total - BAND_GAP);
    }

    /** Inset between a pack's container and what it holds. */
    private static final int BAND_PAD = 5;
    /** Space between one pack's container and the next. */
    private static final int BAND_GAP = 4;

    /** One band's pack: its icon, with its name beneath. */
    private void drawGroupIcon(GuiGraphicsExtractor poseStack, int[] group, int x, int y,
        int size, int columnW, int top, int bottom)
    {
        float alpha = featherAt(y + size / 2, top, bottom, FEATHER);
        de.cas_ual_ty.dueldimension.set.CardSet set = groupSet(group);
        Identifier icon = set == null ? DuelTextures.COVER
            : CardImageManager.peekTextureCard(DuelTextures.setIconSmooth(set),
                ClientProxy.activeSetInfoImageSize, true);
        if(icon == null || icon == DuelTextures.UNKNOWN)
        {
            icon = DuelTextures.COVER;
        }
        featheredBlit(poseStack, icon, x + (columnW - size) / 2, y, size, size,
            0F, 0F, 1F, 1F, top, bottom, FEATHER);
        if(set != null && set.code != null && EditorState.profile() != null
            && EditorState.profile().isFavouritePack(set.code))
        {
            // Same mark, same corner as the shop shelf, so a starred product
            // reads the same wherever it is seen.
            int star = Math.max(7, size / 5);
            featheredBlit(poseStack, HubTextures.STAR,
                x + (columnW - size) / 2 + size - star - 2, y + 2, star, star,
                0F, 0F, 1F, 1F, top, bottom, FEATHER);
        }
        if(set == null || set.name == null)
        {
            return;
        }
        // Every line centred in the column, at a whole-pixel font scale. Drawn
        // inside a scaled matrix at divided coordinates, so the text lands
        // where it was asked for rather than where the scale would put it.
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
            nameLines(group, columnW);
        if(lines.isEmpty())
        {
            return;
        }
        // The label fades with everything else: its alpha byte scaled, since
        // the text call takes one packed colour rather than a tint.
        int label = (Math.round(0xFF * featherAt(y + size + 2 + font.lineHeight / 2,
            top, bottom, FEATHER)) << 24) | 0x00C2C9D6;
        float scale = nameScale();
        poseStack.pose().pushMatrix();
        poseStack.pose().scale(scale, scale);
        for(int line = 0; line < lines.size(); line++)
        {
            int lineW = Math.round(font.width(lines.get(line)) * scale);
            int lineX = x + (columnW - lineW) / 2;
            poseStack.text(font, lines.get(line), Math.round(lineX / scale),
                Math.round((y + size + 2) / scale) + line * font.lineHeight, label, true);
        }
        poseStack.pose().popMatrix();
    }

    private void renderSummary(GuiGraphicsExtractor poseStack, Layout layout)
    {
        if(!groups.isEmpty())
        {
            renderGroupedSummary(poseStack, layout);
            return;
        }
        int pad = layout.i("summary.pad", 14);
        int gap = layout.i("summary.gap", 6);
        int top = layout.i("summary.top", 46);
        int bottom = layout.i("summary.bottom", 40);

        int usableW = width - pad * 2;
        int usableH = height - top - bottom;

        // As many columns as fit at the preferred size, then the card width is
        // recomputed so the row fills the space exactly instead of leaving a
        // ragged margin.
        // Measured twice: once at the roomy size, and again tighter if that
        // turned out to scroll. One extra pass, and it cannot oscillate --
        // the second result is used whatever it comes to.
        int preferred = preferredCardWidth(layout);
        int columns = Math.max(1, (usableW + gap) / (preferred + gap));
        int cardW = Math.max(16, (usableW - (columns - 1) * gap) / columns);
        int cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int rows = (codes.size() + columns - 1) / columns;
        boolean scrolls = rows * (cardH + gap) - gap > usableH;
        if(scrolls)
        {
            preferred = preferredCardWidth(layout, true);
            columns = Math.max(1, (usableW + gap) / (preferred + gap));
            cardW = Math.max(16, (usableW - (columns - 1) * gap) / columns);
            cardH = Math.round(cardW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
            rows = (codes.size() + columns - 1) / columns;
        }
        // No fade at all when nothing can move: a strip that fits has no edge
        // to run off, so dimming its top row is a gradient over nothing.
        int feather = scrolls ? FEATHER : 0;
        int badgeFeather = scrolls ? BADGE_FEATHER : 0;

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
        setFade(summaryScroll, maxScroll);

        int firstRow = (int)(summaryScroll / pitch);
        // What is left over after the whole rows: the offset that makes the top
        // row slide rather than snap.
        int slide = Math.round(summaryScroll - firstRow * pitch);
        int visibleRows = Math.max(1, (usableH + gap) / pitch);

        int gridW = columns * cardW + (columns - 1) * gap;
        int startX = (width - gridW) / 2;

        String title = codes.size() + " cards from " + setName;
        poseStack.text(font, title, width / 2 - font.width(title) / 2, 22, MenuInk.title(), MenuInk.shadow());

        // Clipped to the grid's own strip, so a row halfway off the top is cut
        // rather than drawn over the title.
        // Where each new card's badge goes, collected while the grid is clipped
        // and drawn once it is not.
        java.util.List<int[]> badges = new java.util.ArrayList<>();
        summaryTiles.clear();

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
                drawFace(poseStack, card, x, y, cardW, cardH, top, top + usableH);
                if(isRare(index))
                {
                    // A still highlight rather than a sweep: several at once
                    // would be a light show, and the point is only to pick
                    // them out.
                    featheredHover(poseStack, x - 3, y - 3, cardW + 6, cardH + 6,
                        top, top + usableH);
                    drawFace(poseStack, card, x, y, cardW, cardH, top, top + usableH);
                }
                summaryTiles.add(new int[] {x, y, cardW, cardH, index});
                // Noted, not drawn. The badge hangs over its tile's top edge and
                // the grid is scissored to its own strip, so drawing it here has
                // the clip slice every badge on the top row in half. It is drawn
                // once the clip is released -- see below.
                if(index < fresh.size() && Boolean.TRUE.equals(fresh.get(index)))
                {
                    badges.add(new int[] {x + (cardW - summaryBadgeW(cardW)) / 2,
                        y - summaryBadgeH(cardW) / 2 - 2, y});
                }
            }
        }

        clipToSummary(poseStack, top, usableH, false);

        // The badges, now that nothing is clipped, so each may hang over the
        // edge of its tile the way it does on the strip.
        //
        // A second pass rather than a wider scissor. Widening the scissor was
        // tried and is the wrong tool: it lets the TILES bleed as well, so a row
        // scrolling off the top creeps out from under the clip instead of being
        // cut. Here only the badges escape, and each is drawn only while its own
        // tile is still on the strip -- so none is left floating under the title
        // once its row has gone.
        drawBadges(poseStack, badges, top, usableH, cardH);

        // Only claim there is more when there is, and say how much.
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
        // Widened by the feather: the fade reaches zero at `top` and at
        // `top + height`, so the cut lands where nothing is being drawn and is
        // never visible as an edge.
        poseStack.enableScissor(0, Math.max(0, top - FEATHER), this.width,
            Math.min(this.height, top + height + FEATHER));
    }

    /** A summary tile, faded across itself at the list's edges. */
    private void drawFace(GuiGraphicsExtractor poseStack, Properties card,
        int x, int y, int w, int h, int top, int bottom)
    {
        // Sampled out of the letterboxed square, as everywhere else, or the art
        // stretches.
        featheredBlit(poseStack,
            DuelTextures.cardSmooth(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
            x, y, w, h,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, top, bottom, FEATHER);
    }

    /**
     * Opacity at one HEIGHT on screen.
     * <p>
     * <b>Per pixel row, not per tile.</b> The first version of this asked how
     * opaque a whole CARD should be, from that card's own top and bottom -- and
     * a card is 130 pixels tall against a 26 pixel feather, so it stayed fully
     * opaque until its bottom edge was almost at the boundary and then vanished
     * over the last few pixels of scroll. That is a hard cutoff wearing a fade's
     * clothing. What fades has to be the image itself, across its own height.
     */
    /**
     * Whether anything has actually scrolled past the top of the strip, and the
     * bottom. Set once a frame, before anything is drawn.
     * <p>
     * <b>Nothing inside the strip is ever dimmed.</b> A fade is what content
     * leaving the list looks like, so a side with nothing beyond it has no
     * fade at all -- a list that fits gets none, and a list scrolled to its end
     * keeps its last row at full brightness while its first still fades out.
     */
    private boolean fadeAbove;
    private boolean fadeBelow;

    /** Records which sides have anything beyond them, for this frame. */
    private void setFade(float scroll, float maxScroll)
    {
        fadeAbove = scroll > 0.5F;
        fadeBelow = scroll < maxScroll - 0.5F;
    }

    private float featherAt(int y, int top, int bottom, int feather)
    {
        if(feather <= 0 || (!fadeAbove && !fadeBelow))
        {
            return 1F;
        }
        // Measured from OUTSIDE the strip: zero at `top - feather`, full at
        // `top`. The band used to sit inside the list, so the first row -- the
        // one hard against the top edge and fully on screen -- was the row being
        // dimmed. Now the fade occupies only the space above the strip, which
        // nothing but content on its way out ever reaches, and a row sitting at
        // the boundary is at full brightness.
        // One side at a time, and only the sides that have something beyond
        // them. The band sits OUTSIDE the strip -- zero at `top - feather`,
        // full at `top` -- so a row hard against the boundary and fully on
        // screen is at full brightness, and only what has gone past it fades.
        float t = 1F;
        if(fadeAbove)
        {
            t = Math.min(t, (y - (top - feather)) / (float)feather);
        }
        if(fadeBelow)
        {
            t = Math.min(t, ((bottom + feather) - y) / (float)feather);
        }
        t = Mth.clamp(t, 0F, 1F);
        // Biased hard towards opaque. A straight ramp spends half its band at
        // half brightness, which over any band wide enough not to look like a
        // hard cut means a whole row of cards sitting visibly dimmed while
        // plainly still on the list. The curve puts most of the band at nearly
        // full brightness and does the actual fading in the last unit or two,
        // which is where an edge should be.
        return (float)Math.pow(t, 0.35D);
    }

    /**
     * How tall each slice of a feathered draw is.
     * <p>
     * One unit, not two: the fade bands below are short on purpose, and a
     * short band sliced coarsely is a staircase rather than a gradient. Twelve
     * slices is smooth; six is visible banding. The cost is a dozen extra
     * draws for the two or three tiles actually at an edge.
     */
    private static final int FEATHER_STRIP = 1;

    /**
     * Blits a texture in horizontal slices, each at its own opacity, so the
     * picture fades across itself rather than all at once.
     * <p>
     * One draw for anything in the opaque middle, which is every tile but the
     * two or three at either edge -- the slicing is only paid for where it
     * shows.
     */
    private void featheredBlit(GuiGraphicsExtractor poseStack, Identifier texture,
        int x, int y, int w, int h, float u0, float v0, float u1, float v1,
        int top, int bottom, int feather)
    {
        if(y >= top + feather && y + h <= bottom - feather)
        {
            DdBlitUtil.blit(poseStack, texture, x, y, w, h, u0, v0, u1, v1,
                DdBlitUtil.NO_TINT);
            return;
        }
        for(int sy = y; sy < y + h; sy += FEATHER_STRIP)
        {
            int sh = Math.min(FEATHER_STRIP, y + h - sy);
            float alpha = featherAt(sy + sh / 2, top, bottom, feather);
            if(alpha <= 0F)
            {
                continue;
            }
            // The slice samples its own band of the source, or every slice
            // would draw the whole card squashed into two pixels.
            float va = v0 + (v1 - v0) * (sy - y) / (float)h;
            float vb = v0 + (v1 - v0) * (sy + sh - y) / (float)h;
            DdBlitUtil.blit(poseStack, texture, x, sy, w, sh, u0, va, u1, vb,
                alpha >= 1F ? DdBlitUtil.NO_TINT : DdBlitUtil.alpha(alpha));
        }
    }

    /** The rare card's frame, faded like everything else on the list. */
    private void featheredHover(GuiGraphicsExtractor poseStack, int x, int y, int w, int h,
        int top, int bottom)
    {
        int from = Math.max(y, top - FEATHER);
        int to = Math.min(y + h, bottom + FEATHER);
        if(y >= top + FEATHER && y + h <= bottom - FEATHER)
        {
            NineSlice.draw(poseStack, HubTextures.PANEL, x, y, w, h, NineSlice.HOVER, 3, 0.55F);
            return;
        }
        for(int sy = from; sy < to; sy += FEATHER_STRIP)
        {
            int sh = Math.min(FEATHER_STRIP, to - sy);
            float alpha = featherAt(sy + sh / 2, top, bottom, FEATHER);
            if(alpha <= 0F)
            {
                continue;
            }
            poseStack.enableScissor(0, sy, this.width, sy + sh);
            NineSlice.draw(poseStack, HubTextures.PANEL, x, y, w, h,
                NineSlice.HOVER, 3, 0.55F * alpha);
            poseStack.disableScissor();
        }
    }

    /**
     * A nine-slice panel, faded the same way.
     * <p>
     * Sliced with the scissor rather than by UV, because a nine-slice is nine
     * draws with fixed corners and cannot be sampled by the row. MC's scissor
     * stack intersects on push, so this narrows the list's clip rather than
     * replacing it.
     */
    private void featheredPanel(GuiGraphicsExtractor poseStack, Identifier texture,
        int x, int y, int w, int h, int top, int bottom, int feather, float scale)
    {
        if(y >= top + feather && y + h <= bottom - feather)
        {
            NineSlice.draw(poseStack, texture, x, y, w, h, 0, 1, scale);
            return;
        }
        int from = Math.max(y, top - feather);
        int to = Math.min(y + h, bottom + feather);
        for(int sy = from; sy < to; sy += FEATHER_STRIP)
        {
            int sh = Math.min(FEATHER_STRIP, to - sy);
            float alpha = featherAt(sy + sh / 2, top, bottom, feather);
            if(alpha <= 0F)
            {
                continue;
            }
            poseStack.enableScissor(0, sy, this.width, sy + sh);
            NineSlice.draw(poseStack, texture, x, y, w, h, 0, 1, alpha * scale);
            poseStack.disableScissor();
        }
    }

    /**
     * How far the cards take to fade out at the list's edges.
     * <p>
     * Short, so a card is at full brightness for almost all of its travel and
     * only goes in the last stretch. At 26 the band was taller than the gap
     * between the list's top and the first row, so the whole top row sat inside
     * it and was dimmed while plainly still on the list -- the fade read as the
     * list being murky rather than as an edge.
     */
    /**
     * How wide a summary tile wants to be, measured in REAL pixels.
     * <p>
     * <b>Not a fixed number of GUI units.</b> A unit is
     * {@code window pixels / guiScale}, and Minecraft raises the GUI scale as
     * the window grows -- so a tile written down as 62 units is 186 pixels at
     * scale 3 and 248 at scale 4. The grid therefore got PHYSICALLY bigger and
     * showed FEWER cards the larger the display, which is backwards: a bigger
     * screen should show more of the pull, not less of it.
     * <p>
     * Sizing in pixels and dividing by the scale keeps a tile the same size on
     * the desk at every resolution, so the number of columns rises with the
     * window as it should. The floor is what stops a very large GUI scale from
     * reducing a card to something nobody can identify -- legibility wins over
     * consistency at the point the two disagree.
     */
    /**
     * As {@link #preferredCardWidth(Layout)}, tightened when the grid scrolls.
     * <p>
     * A summary that fits on screen has no reason to be dense -- there is room,
     * so the cards may as well be readable. One that scrolls is a different
     * question: every unit of tile is a card pushed off the bottom, and on a
     * small display that is where the cards run out fastest. So the share is
     * trimmed only in the case that benefits from it.
     */
    private int preferredCardWidth(Layout layout, boolean scrolling)
    {
        if(!scrolling)
        {
            return preferredCardWidth(layout);
        }
        double gui = guiScale();
        float windowH = (float)(height * gui);
        float share = layout.f("summary.cardShare", 0.185F)
            * layout.f("summary.scrollingShare", 0.88F);
        float real = Mth.clamp(windowH * share,
            layout.f("summary.cardPixelsMin", 118F),
            layout.f("summary.cardPixelsMax", 240F));
        return Math.max(layout.i("summary.cardWidthMin", 34), Math.round((float)(real / gui)));
    }

    private int preferredCardWidth(Layout layout)
    {
        double gui = guiScale();
        // A SHARE of the window rather than a flat pixel count. A fixed size
        // keeps a tile the same on the desk, which is right going up and wrong
        // coming down: on a small display the same 168 pixels is a much larger
        // part of the screen, so the grid showed a handful of big cards where
        // it should have shown more smaller ones. Taking a share makes the tile
        // shrink with the window, which is what compact means here.
        //
        // The two bounds are where that stops being true in either direction --
        // below the floor a card cannot be identified, and above the ceiling a
        // very large display would give tiles nobody asked to be that big.
        float windowH = (float)(height * gui);
        float real = Mth.clamp(windowH * layout.f("summary.cardShare", 0.185F),
            layout.f("summary.cardPixelsMin", 118F),
            layout.f("summary.cardPixelsMax", 240F));
        return Math.max(layout.i("summary.cardWidthMin", 34), Math.round((float)(real / gui)));
    }

    private static final int FEATHER = 8;
    /**
     * The badges fade LATER than the cards, over a shorter band right at the
     * edge.
     * <p>
     * They already sit above their own card, so with the longer feather they
     * had they were half gone while the card they belong to was still fully
     * lit -- a row of ghost labels over solid art. A short band means the badge
     * stays solid until it is nearly at the boundary and then goes quickly,
     * which is what reads as it fading only once it is further up. It still
     * reaches zero exactly at the edge, which is what keeps it from floating
     * over the title: badges are drawn outside the clip.
     */
    private static final int BADGE_FEATHER = 5;

    /**
     * How much of a tile's width the NEW badge wants to take.
     * <p>
     * A FRACTION of the tile, not a size of its own. The tiles are now measured
     * in real pixels and so shrink in GUI units as the scale rises; a badge
     * written down as its own number of units would grow against them and end
     * up straddling the card it belongs to. Roughly a third is what the badge
     * occupied at the size both were first drawn at.
     */
    private static final float SUMMARY_BADGE_OF_TILE = 0.32F;

    /**
     * Texels per screen pixel for the badge, always a whole number.
     * <p>
     * <b>Integer scaling has to be measured in REAL pixels, not GUI units.</b>
     * Asking for 20x9 GUI units from a 27x13 file is 0.74 across and 0.69 down
     * -- two different fractions, so nearest sampling drops a different set of
     * rows than columns and the letters come out chewed. There is also no pair
     * of whole GUI units that fixes it: at a GUI scale of 3 the only height
     * whose real size divides 13 is 13 itself.
     * <p>
     * So the size is not rounded at all. The badge is drawn at its own 27x13
     * inside a matrix scaled by {@code factor / guiScale}, which puts exactly
     * {@code factor} screen pixels on every texel in both directions.
     */
    private double guiScale()
    {
        return minecraft == null || minecraft.getWindow() == null ? 1D
            : minecraft.getWindow().getGuiScale();
    }

    private float badgeScale(int cardW)
    {
        double gui = guiScale();
        int factor = Math.max(1,
            (int)Math.round(cardW * SUMMARY_BADGE_OF_TILE * gui / HubTextures.NEW_BADGE_W));
        return (float)(factor / gui);
    }

    private int summaryBadgeW(int cardW)
    {
        return Math.round(HubTextures.NEW_BADGE_W * badgeScale(cardW));
    }

    private int summaryBadgeH(int cardW)
    {
        return Math.round(HubTextures.NEW_BADGE_H * badgeScale(cardW));
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

    /**
     * The NEW badge over a card the collection did not already hold.
     * <p>
     * Above the card rather than on it: a corner badge covers artwork, and this
     * row is the artwork. Centred on the card's own centre, which is what
     * {@code centreX} already is.
     * <p>
     * Only once the card has turned far enough to be readable. A badge over a
     * card back announces the answer before the reveal, which is the one thing
     * a pack opening is for.
     */
    private void drawNewBadge(GuiGraphicsExtractor poseStack, int index, int centreX, int y,
        int drawH, float turn)
    {
        if(index < 0 || index >= fresh.size() || !Boolean.TRUE.equals(fresh.get(index))
            || turn < 0.5F)
        {
            return;
        }
        DdBlitUtil.fullBlit(poseStack, HubTextures.NEW_BADGE,
            centreX - HubTextures.NEW_BADGE_W / 2, y - HubTextures.NEW_BADGE_H - 2,
            HubTextures.NEW_BADGE_W, HubTextures.NEW_BADGE_H);
    }
}
