package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.duel.profile.Sleeves;
import de.cas_ual_ty.dueldimension.net.ProfilePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * What a deck is printed on: a grid of every sleeve the mod has, with the one
 * this deck wears marked and the ones the player has not bought dimmed rather
 * than hidden.
 * <p>
 * <b>Unowned sleeves are shown on purpose.</b> A cosmetic nobody knows exists
 * is a cosmetic nobody buys, and a grid with holes in it also reads as broken.
 * They are drawn at half strength — dim enough to say "not yours", bright
 * enough to judge the art you would be paying for — and clicking one refuses
 * with a line saying where it is sold.
 * <p>
 * The tiles are the sleeve art itself and the frames are the deck editor's own
 * chip texture, so the only thing the font draws here is the names.
 *
 * <h2>The deck is resolved, never held</h2>
 * This screen asks {@link EditorState#deck()} every time it needs the open deck
 * instead of taking one in its constructor. A profile sync replaces every
 * {@link DeckList} object in the list with a fresh one carrying the same deck
 * (the reason {@link EditorState#indexOf} exists), so a deck captured when the
 * picker opened would be an orphan by the time it was clicked: the swatch would
 * move and nothing real would change.
 *
 * <h2>Ownership is asked, never asserted</h2>
 * The grey-out is a courtesy. {@code DeckEdits.setDeckSleeve} checks ownership
 * again on the server, and a refusal arrives as a red chat line plus a full
 * profile re-sync that undoes the optimistic change made here — so a client
 * that lies about what it owns changes nothing.
 */
public class SleevePickerScreen extends Screen
{
    /** How much screen is left around the panel. */
    private static final int MARGIN = 6;
    /** Never so wide that the sleeves become a line to read along. */
    private static final int MAX_WIDTH = 520;
    private static final int PAD = 10;

    /**
     * How wide one sleeve is drawn, in GUI units.
     * <p>
     * <b>Measured against the GUI, not the framebuffer.</b> The client runs at
     * 1634&times;920 and {@code guiScale:0} resolves to 3, so a screen is
     * {@code ceil(1634/3)} &times; {@code ceil(920/3)} = <b>545&times;307 GUI
     * units</b>. Sizing a tile against 1634 would make it three times too
     * large and fit four sleeves on a page.
     * <p>
     * 32 is chosen so the whole set fits without scrolling at that size: with
     * {@link #GAP} the cell is 40&times;55, which gives
     * {@code (520 - 20 + 8) / 40 = 12} columns and
     * {@code 233 / 55 = 4} rows of room — 48 slots for 38 sleeves. The
     * scrolling below still exists because a smaller window gets fewer.
     */
    private static final int TILE_W = 32;

    /**
     * Space between cells.
     * <p>
     * Wider than the editor's 2 because every tile here wears a {@link #FRAME}
     * on all four sides; at a smaller gap the neighbouring frames would overlap
     * and the grid would read as one continuous box rather than as choices.
     */
    private static final int GAP = 8;

    /** How much of the chip behind a tile shows around its art. */
    private static final int FRAME = 3;

    /** Room above the grid for the deck's name and the owned count. */
    private static final int HEADER_H = 16;

    /**
     * Room below the grid: a 20-tall button row, and 6 of air above it.
     * <p>
     * The two are one constant because the panel's height is derived from the
     * grid, so the gap between the last row and the buttons is whatever is left
     * of this after the buttons -- stating it as two numbers in two places is
     * how that gap becomes zero the first time either moves.
     */
    private static final int FOOTER_H = 26;

    /**
     * The sizes the sleeve art exists at on disk, smallest first.
     * <p>
     * Every sleeve ships all seven, so the same list serves them all; see
     * {@link #textureSizeFor}.
     */
    // Stops at CardSleevesType.MAX_SIZE, which is what the catalogue ships.
    private static final int[] SIZES = { 16, 32, 64, 128, 256, 512 };

    private final Screen parent;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int columns;
    private int visibleRows;
    private int tileH;
    /** Recorded in init so the draw and the hit test cannot drift apart. */
    private int gridTop;
    private int scroll;
    private int maxScroll;

    /** Why the last click was refused; cleared by the next one that is not. */
    private String refusal = "";

    public SleevePickerScreen(Screen parent)
    {
        super(Component.literal("Sleeves"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        // Whole columns and whole rows, and no more of either: the panel is
        // sized to the grid rather than to the window, so it is never trailed
        // by a strip of empty recess. Same shape as BinderPackScreen, which is
        // the other scrollable grid of card-shaped tiles in this mod -- except
        // that one fills the height, and there are 38 sleeves rather than a
        // set's worth of cards, so here the content decides.
        int usable = Math.min(MAX_WIDTH, width - MARGIN * 2);
        tileH = Math.max(8, Math.round(TILE_W / DuelTextures.CARD_ASPECT));
        columns = Math.max(1, (usable - PAD * 2 + GAP) / (TILE_W + GAP));
        panelW = columns * (TILE_W + GAP) - GAP + PAD * 2;

        int rows = (CardSleevesType.VALUES.length + columns - 1) / columns;
        // Everything the panel spends on something other than tiles.
        int chrome = PAD * 2 + HEADER_H + FOOTER_H;
        int room = height - MARGIN * 2 - chrome;
        visibleRows = Math.clamp(room / (tileH + GAP), 1, rows);
        panelH = chrome + visibleRows * (tileH + GAP);

        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        gridTop = top + PAD + HEADER_H;
        maxScroll = Math.max(0, rows - visibleRows);
        scroll = Math.clamp(scroll, 0, maxScroll);

        addRenderableWidget(new HubWidgets.TextureButton(left + PAD,
            top + panelH - PAD - 20, 60, 20, Component.literal("Back"), pressed -> onClose()));
    }

    // ---- drawing ----

    /**
     * Draws a sleeve at its true card proportions.
     * <p>
     * <b>Sampled through the letterbox window, never blitted whole.</b> Sleeve
     * art is stored in a SQUARE file with the card occupying exactly
     * {@code u 0.199..0.801, v 0.0625..0.9375} — the same window
     * {@link DuelTextures#CARD_U0} describes for card images, verified against
     * the alpha bounds of the files themselves. Stretching the whole square
     * into a card-shaped rectangle squeezes the art horizontally by a third.
     * <p>
     * Public because the deck editor's button wears the deck's current sleeve,
     * and one drawing routine means the two can never disagree about how a
     * sleeve is sampled.
     */
    public static void drawSleeve(GuiGraphicsExtractor poseStack, CardSleevesType sleeve,
        int x, int y, int width, int height, int tint)
    {
        DdBlitUtil.blit(poseStack, (sleeve == null ? Sleeves.DEFAULT : sleeve)
                .getMainRL(textureSizeFor(width)), x, y, width, height,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, tint);
    }

    /**
     * Which of the seven files on disk to ask for, given how wide the sleeve is
     * being drawn.
     * <p>
     * The GUI is measured in scaled units and the texture in real pixels, so
     * the scale is part of the sum: a 32-unit tile at {@code guiScale} 3 is 96
     * screen pixels, and only {@code U1 - U0} = 60.2% of the file's width is
     * the card, so the file has to be {@code 96 / 0.602} = 160 across before
     * there is a texel for every pixel. 256 is the first size that covers it.
     * <p>
     * Asked at draw time rather than fixed, because the answer changes with the
     * player's GUI scale — a fixed 256 is blurry at scale 4 and wasteful at 1.
     */
    private static int textureSizeFor(int drawnWidth)
    {
        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        int needed = Math.round(drawnWidth * scale / (DuelTextures.CARD_U1 - DuelTextures.CARD_U0));
        for(int size : SIZES)
        {
            if(size >= needed)
            {
                return size;
            }
        }
        return SIZES[SIZES.length - 1];
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

        // The dim the editor draws, not extractBackground: that BLURS in 26.2,
        // the blur is once per frame, and a screen opening over another that
        // already asked for it took the client down.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, panelW, panelH);

        DeckList deck = EditorState.deck();
        CardSleevesType worn = deck.sleeve();

        // Which deck is being dressed, because the picker is opened from an
        // editor that is on one deck of several.
        String title = font.plainSubstrByWidth(deck.name(), panelW - PAD * 2 - 100);
        poseStack.text(font, title, left + PAD, top + PAD, MenuInk.title(), MenuInk.shadow());

        // Counted against what a player can actually end up holding, not against
        // every constant in the enum. Three sleeves are supporters' rewards that
        // no shop stocks, so the whole-enum total was a target nobody could ever
        // reach -- buy every sleeve on sale and it still read 35 / 38 forever.
        // A patron's own sleeves count once they have them, so their total goes
        // up rather than their progress being capped below full.
        java.util.Set<CardSleevesType> held = EditorState.profile().ownedSleeves();
        int obtainable = 0;
        for(CardSleevesType sleeve : CardSleevesType.VALUES)
        {
            if(Sleeves.isFree(sleeve) || Sleeves.isPurchasable(sleeve) || held.contains(sleeve))
            {
                obtainable++;
            }
        }
        int owned = held.size();
        String count = owned + " / " + obtainable + " owned";
        poseStack.text(font, count, left + panelW - PAD - font.width(count), top + PAD,
            MenuInk.body(), MenuInk.shadow());

        // One recess behind the whole grid rather than a frame per empty cell,
        // as the editor's own grids do.
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left + PAD - 2, gridTop - 2,
            panelW - PAD * 2 + 4, visibleRows * (tileH + GAP) + 4);

        CardSleevesType hovered = null;
        for(int cell = 0; cell < columns * visibleRows; cell++)
        {
            int index = cell + scroll * columns;
            if(index >= CardSleevesType.VALUES.length)
            {
                break;
            }
            CardSleevesType sleeve = CardSleevesType.VALUES[index];
            int x = left + PAD + (cell % columns) * (TILE_W + GAP);
            int y = gridTop + (cell / columns) * (tileH + GAP);

            boolean over = mouseX >= x - FRAME && mouseX < x + TILE_W + FRAME
                && mouseY >= y - FRAME && mouseY < y + tileH + FRAME;
            if(over)
            {
                hovered = sleeve;
            }

            // The chip is a three-state atlas -- idle, hovered, lit -- which is
            // exactly the three things a tile has to say, and it is the same
            // texture the editor's filter chips wear.
            int row = sleeve == worn ? NineSlice.SELECTED
                : over ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, x - FRAME, y - FRAME,
                TILE_W + FRAME * 2, tileH + FRAME * 2, row, 3);

            // Half strength for one the player does not have. The same mark the
            // editor puts on a card it will not let you add another copy of, so
            // "you cannot have this" looks the same in both grids -- and unlike
            // hiding it, the art is still legible enough to want.
            drawSleeve(poseStack, sleeve, x, y, TILE_W, tileH,
                EditorState.profile().ownsSleeve(sleeve)
                    ? DdBlitUtil.NO_TINT : DdBlitUtil.alpha(0.5F));
        }

        scrollbar(poseStack);

        // What the deck is wearing, spelled out: the marked tile says which one
        // it is, and this says what it is called. Beside the Back button, and
        // cut to the room actually left over -- the longest sleeve name is
        // wider than the panel is at the smallest window the game allows.
        int wearingX = left + PAD + 60 + 8;
        poseStack.text(font, font.plainSubstrByWidth("Wearing:  " + nameOf(worn).getString(),
                left + panelW - PAD - wearingX),
            wearingX, top + panelH - PAD - 20 + (20 - font.lineHeight) / 2 + 1,
            MenuInk.body(), MenuInk.shadow());

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);

        if(!refusal.isEmpty())
        {
            int textWidth = font.width(refusal);
            NineSlice.draw(poseStack, HubTextures.PANEL, left + panelW / 2 - textWidth / 2 - 8,
                top + panelH - PAD - FOOTER_H - 20, textWidth + 16, 18);
            poseStack.text(font, refusal, left + panelW / 2 - textWidth / 2,
                top + panelH - PAD - FOOTER_H - 15, 0xFFFF8A80, true);
        }

        if(hovered != null)
        {
            List<Component> lines = new ArrayList<>();
            lines.add(nameOf(hovered));
            if(hovered == worn)
            {
                lines.add(Component.literal("This deck is wearing these"));
            }
            else if(EditorState.profile().ownsSleeve(hovered))
            {
                lines.add(Component.literal(hovered.isCardBack()
                    ? "Click to take this deck's sleeves off"
                    : "Click to dress this deck"));
            }
            else if(hovered.isPatreonReward)
            {
                // Never on sale, so saying "buy it in the shop" would send a
                // player looking for something that is not there.
                lines.add(Component.literal("A supporter's sleeves - not sold"));
            }
            else
            {
                lines.add(Component.literal("Not owned - sold in the Sleeve Shop"));
            }
            // setComponentTooltipForNextFrame, not setTooltipForNextFrame: the
            // latter's List overloads take FormattedCharSequence.
            poseStack.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
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


    /**
     * The grid's scrollbar, in the panel's padding rather than over the last
     * column, so no tile is half covered by furniture.
     */
    private void scrollbar(GuiGraphicsExtractor poseStack)
    {
        int rows = (CardSleevesType.VALUES.length + columns - 1) / columns;
        if(maxScroll <= 0)
        {
            return;
        }
        int x = left + panelW - PAD + 2;
        int height = visibleRows * (tileH + GAP);
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, gridTop, 4, height, 0, 2);
        int thumbH = Math.max(12, height * visibleRows / Math.max(1, rows));
        int thumbY = gridTop + (height - thumbH) * scroll / maxScroll;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
    }

    /**
     * What a sleeve is called, in the player's language.
     * <p>
     * Read off the item the sleeve registers rather than from a table typed out
     * here, so a sleeve added to the enum is named by the lang file it already
     * needs. {@code BuiltInRegistries.ITEM} is a DEFAULTED registry, so an id it
     * does not know yields {@code minecraft:air} and never null — the miss is
     * therefore tested by identity, and falls back to the key the item would
     * have had.
     */
    private static Component nameOf(CardSleevesType sleeve)
    {
        CardSleevesType target = sleeve == null ? Sleeves.DEFAULT : sleeve;
        if(target.isCardBack())
        {
            // The plain back is not a sleeve, it is the absence of one, and it
            // has no item to take a name from -- the registration loop skips it.
            // It used to fall through to a translation key nothing defines and
            // print raw, which is why taking sleeves off looked impossible.
            return Component.literal("No Sleeves");
        }
        Item item = target.getItem();
        return Component.translatable(item == Items.AIR
            ? "item." + DuelDimension.MOD_ID + "." + target.getResourceName()
            : item.getDescriptionId());
    }

    // ---- interaction ----

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        boolean doubleClick = false;
        if(event.button() == 0)
        {
            CardSleevesType sleeve = sleeveAt(event.x(), event.y());
            if(sleeve != null)
            {
                choose(sleeve);
                return true;
            }
        }
        return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
    }

    /**
     * Dresses the open deck, if the player owns the sleeve.
     * <p>
     * Applied here as well as asked for, so the mark moves on the click rather
     * than on the round trip — the same optimism {@link EditorState#publish}
     * and {@link EditorState#wear} use. The sleeve travels as its <b>id</b> and
     * not its enum index: the index is a position in a list of cosmetics that
     * grows, and this request ends in something written to disk.
     */
    private void choose(CardSleevesType sleeve)
    {
        if(!EditorState.isSynced())
        {
            // Before the first sync the open deck is a placeholder that belongs
            // to nobody, so there is nothing to dress.
            refusal = "Still waiting for your collection";
            return;
        }
        if(!EditorState.profile().ownsSleeve(sleeve))
        {
            refusal = sleeve.isPatreonReward
                ? "Those sleeves are a supporter's, not for sale"
                : "You do not own those sleeves yet - the Sleeve Shop sells them";
            return;
        }
        refusal = "";
        DeckList deck = EditorState.deck();
        deck.setSleeve(sleeve);
        ClientPlayNetworking.send(
            new ProfilePayloads.SetDeckSleeve(deck.name(), Sleeves.nameOf(sleeve)));
    }

    /** Which sleeve is under the cursor, on the grid the renderer laid out. */
    private CardSleevesType sleeveAt(double mouseX, double mouseY)
    {
        for(int cell = 0; cell < columns * visibleRows; cell++)
        {
            int index = cell + scroll * columns;
            if(index >= CardSleevesType.VALUES.length)
            {
                break;
            }
            int x = left + PAD + (cell % columns) * (TILE_W + GAP);
            int y = gridTop + (cell / columns) * (tileH + GAP);
            if(mouseX >= x - FRAME && mouseX < x + TILE_W + FRAME
                && mouseY >= y - FRAME && mouseY < y + tileH + FRAME)
            {
                return CardSleevesType.VALUES[index];
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if(maxScroll > 0)
        {
            scroll = Math.clamp(scroll - (int)Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreen(parent);
        }
    }

    /**
     * As the editor is: a screen that waits for the server has to let the
     * server run. A pause screen stops a single-player world ticking, and the
     * profile sync that answers a refused sleeve would never arrive.
     */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
