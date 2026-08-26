package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.CardPresentation;
import com.mojang.blaze3d.systems.RenderSystem;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardImageManager;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.UnownedPipelines;
import net.minecraft.resources.ResourceLocation;
import de.cas_ual_ty.dueldimension.duel.profile.CardQuery;
import de.cas_ual_ty.dueldimension.duel.profile.DeckLimits;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The deck editor, laid out the way Tag Force does it: the deck you are
 * building down the left, the collection you are building it from down the
 * right.
 * <p>
 * Left is a workspace of three grids stacked in play order — Main, then Extra,
 * then Side — so the whole deck is visible at once rather than behind tabs.
 * Right is the trunk under a search field, filter chips and a sort control.
 * <p>
 * Cards move by the inventory gestures a Minecraft player already has: pick up
 * with a click and drop where it goes, or shift-click to send a card to the
 * place it obviously belongs. Every add is checked by {@link DeckLimits}, and a
 * refusal says which rule refused so the player knows whether to collect
 * another copy or free a slot.
 */
public class DeckEditorScreen extends Screen
{
    /** The tunables, read fresh every layout pass so a hot reload takes effect. */
    private static final String LAYOUT = "deck_editor";

    private final Screen parent;

    // Resolved once per init() from the layout file and the window size.
    /** Ten-column cards on the deck workspace. */
    private int deckCardW;
    private int deckCardH;
    /** Denser cards in the searchable collection. */
    private int trunkCardW;

    /**
     * How large the collection draws its cards, as a multiple of the size it
     * would pick on its own.
     * <p>
     * Static so it survives closing and reopening the editor: it is a way of
     * working, not a property of a deck. Everything else follows from the card
     * size -- the column count and the visible row count are both derived from
     * it -- so this one number reflows the whole grid.
     */
    private static float trunkScale = 1F;

    private static final float TRUNK_SCALE_MIN = 0.6F;
    private static final float TRUNK_SCALE_MAX = 2.2F;
    private int trunkCardH;
    private int gap;
    private int pad;
    private int mainColumns;
    private int extraColumns;
    private int trunkColumns;
    private int titleH;
    private int headerH;
    private int sectionGap;
    private int mainRows;

    private EditBox search;
    private int leftX;
    private int rightX;
    private int panelTop;
    private int leftW;
    private int rightW;
    private int panelH;

    /** The card currently held by the cursor, and where it came from. */
    private Properties carried;
    private DeckList.Part carriedFrom;
    private int carriedIndex = -1;
    /**
     * The artwork the carried copy is wearing.
     * <p>
     * Carried on the cursor with the card, because picking a copy up removes it
     * from the deck and putting it down adds it back: without this the art
     * would be dropped by the round trip, and dragging a dressed copy one slot
     * to the left would quietly return it to its printed art.
     */
    private int carriedArt;

    /** Why the last attempted add was refused; cleared on the next action. */
    private String refusal = "";
    /** Slack either side of a scrollbar, so catching it does not need pixel aim. */
    private static final int BAR_GRAB = 3;

    private int trunkScroll;
    /**
     * Where the grid was on the previous frame, so the scroll-velocity gate can
     * ask how far it has travelled. {@code drawing.cpp}:1375's {@code prev_pos}.
     */
    private int lastTrunkScroll;
    /**
     * The collection scrollbar's track, recorded as it is drawn.
     * <p>
     * Taken from the draw rather than recomputed, so the box you can click can
     * never drift from the bar you can see.
     */
    private int trunkBarX;
    private int trunkBarY;
    private int trunkBarH;
    private int trunkBarThumbH;
    private int trunkBarRows;
    private int trunkBarVisibleRows;
    /** Where in the thumb the drag took hold, or -1 when not dragging. */
    private int trunkBarGrab = -1;
    /**
     * The filter drawer's own scrollbar, recorded the same way.
     * <p>
     * Its own rather than borrowed: the drawer's bar is drawn at exactly the x
     * the collection's is, and the collection's is not drawn at all while the
     * drawer is up -- so one set of remembered numbers meant dragging the bar
     * you could see scrubbed the grid you could not.
     */
    private int filterBarX;
    private int filterBarY;
    private int filterBarH;
    private int filterBarThumbH;
    private int filterBarGrab = -1;
    /** How far the deck's three sections are scrolled, in pixels. */
    private int deckScroll;
    /** Whether the extra filters are showing; they cover the trunk grid. */
    private boolean filtersOpen;
    /** How far the filter drawer is scrolled, in pixels. */
    private int filterScroll;
    /** How tall the drawer's contents came out, so the scroll can be bounded. */
    private int filterContentHeight;
    /** The selector whose list is open, if any, and how far that list is scrolled. */
    private DropdownButton openList;
    private int openListScroll;
    /** The four numeric bands, kept as text so a half-typed number is allowed. */
    private EditBox levelMin;
    private EditBox levelMax;
    private EditBox attackMin;
    private EditBox attackMax;
    private EditBox defenceMin;
    private EditBox defenceMax;

    /**
     * Where the mouse went down, so a press-drag-release can be told from a
     * click. A card picked up by pressing is dropped when the button is
     * RELEASED somewhere else; a card picked up by a click-and-let-go stays on
     * the cursor until the next click. Both gestures work, which is what a
     * Minecraft player expects of an inventory.
     */
    private boolean pressedOnCard;
    private double pressX;
    private double pressY;
    /** Past this many pixels a press counts as a drag rather than a click. */
    private static final double DRAG_SLOP = 4;

    /** The right-click menu: what it is on, and where it opened. */
    private Properties menuCard;
    private DeckList.Part menuPart;
    private int menuIndex = -1;
    private int menuX;
    private int menuY;

    /**
     * The artwork picker: which copy is being dressed, and where it lives.
     * <p>
     * An OVERLAY on this screen rather than a screen of its own, and that is
     * not a preference. Minecraft does not draw the screen a new one opened
     * over, so a second {@link Screen} would dim the world and leave the editor
     * invisible behind it — and the one call that does show what is behind,
     * {@code extractBackground}, blurs in 26.2, once per frame, which took the
     * client down the last time two screens asked for it in the same frame.
     * Drawn inside this screen the editor keeps rendering and a scrim dims it.
     */
    private Properties altCard;
    private DeckList.Part altPart;
    private int altIndex = -1;
    /** Which column of the artwork row is leftmost, when they do not all fit. */
    private int altScroll;

    /** How far the hovered card's description is scrolled, and whose it is. */
    private int previewScroll;
    private long previewCard = -1;

    /** The rename field, present only while renaming. */
    private EditBox rename;

    public DeckEditorScreen(Screen parent)
    {
        super(Component.literal("Deck Editor"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        // A mod pipeline is not in getStaticPipelines(), so nothing validates
        // its shader at reload and a bad one would surface as a crash at the
        // first unowned card. Asking here turns that into a log line and the
        // dim fallback, and re-asking per init picks up a resource reload.
        UnownedPipelines.refresh();

        Layout layout = Layout.of(LAYOUT);
        pad = layout.i("panel.pad", 8);
        gap = layout.i("card.gap", 2);
        mainColumns = Math.max(1, layout.i("deck.mainColumns", 10));
        extraColumns = Math.max(1, layout.i("deck.extraColumns", 15));
        titleH = layout.i("deck.titleHeight", 14);
        headerH = layout.i("deck.headerHeight", 12);
        sectionGap = layout.i("deck.sectionGap", 6);

        // Everything else -- the panels, both grids, every control row and the
        // drawer -- comes out of the one ordered pass below.
        remeasure();

        search = new EditBox(font, rightX + pad + 2, panelTop + pad + 2,
            searchWidth(), 14, Component.literal("Search"));
        search.setBordered(false);
        search.setY(search.getY() + (search.getHeight() - 8) / 2);
        search.setResponder(value ->
        {
            EditorState.query().setText(value);
            EditorState.invalidate();
            trunkScroll = 0;
        });
        search.setValue(EditorState.query().text());
        addWidget(search);

        rebuildControls();
    }

    private void rebuildControls()
    {
        clearWidgets();
        // Placed from the same measurements the render pass draws against, and
        // taken again here because the deck's row count -- and so the whole
        // left column -- moves while the screen is up.
        remeasure();

        if(altCard != null)
        {
            // Its Back button is the only widget while it is up, so a click
            // cannot reach the editor it is drawn over -- the same lock-out the
            // hub's delete confirmation uses. Adding the search box would not
            // be harmless: it is a child, so overControl finds it and a click
            // would focus it straight through the scrim.
            buildAltArts();
            return;
        }
        addWidget(search);

        Layout layout = Layout.of(LAYOUT);
        int chipY = panelTop + pad + layout.i("trunk.searchHeight", 16) + 4;
        int chipLeft = rightX + pad;
        int chipH = layout.i("trunk.chipHeight", 14);
        int trunkRoom = Math.max(1, rightW - pad * 2);
        // The same packing measure() counted the band's height from, run again
        // with the same inputs rather than remembered -- it is pure, so the two
        // cannot disagree, and the widgets land exactly where the grid below
        // them was told they would end.
        Packed chips = pack(chipFloors(chipH), chipWidths(chipH), trunkRoom, 2);

        // Kind chips. A chip is on or off, never disabled, so its third atlas
        // row is the lit state rather than a greyed one.
        CardQuery.Kind[] kinds = CardQuery.Kind.values();
        for(int i = 0; i < kinds.length; i++)
        {
            CardQuery.Kind target = kinds[i];
            addRenderableWidget(new ChipButton(chipLeft + chips.x()[i],
                chipY + chips.line()[i] * (chipH + 2), chips.width()[i], chipH,
                Component.literal(label(target)),
                () -> EditorState.query().kinds().contains(target), pressed ->
            {
                EditorState.query().toggleKind(target);
                EditorState.invalidate();
                trunkScroll = 0;
                // The drawer follows the chips: which sub-filters apply is
                // decided by which kinds can be in the pool, so a kind pressed
                // without a rebuild would leave the drawer describing the pool
                // as it was a moment ago.
                rebuildControls();
            }));
        }

        // Starred-only, beside the kind chips because it narrows the pool the
        // same way. Drawn as the star itself rather than the word "Favourites":
        // it is the same mark that appears on the cards.
        int star = kinds.length;
        addRenderableWidget(new StarChip(chipLeft + chips.x()[star],
            chipY + chips.line()[star] * (chipH + 2), chips.width()[star], chipH,
            () -> EditorState.query().favouritesOnly(), pressed ->
        {
            EditorState.query().setFavouritesOnly(!EditorState.query().favouritesOnly());
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        }));

        // Sort cycles through the offered orders; the arrow flips direction.
        // Both sized from the widest label they can ever show, so pressing one
        // cannot re-flow the row it is in.
        int sortW = sortWidth();
        int dirW = dirWidth();
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - sortW - dirW - 2,
            panelTop + pad, sortW, 16, Component.literal(EditorState.query().sort().label()), pressed ->
        {
            EditorState.query().setSort(EditorState.query().sort().next());
            EditorState.invalidate();
            rebuildControls();
        }));
        // An arrow, not the word. ASC and DESC were four and five characters in
        // a thirty-unit button on the one row with nothing to spare; the arrow
        // says it in a square of the row's own height. The narration is still
        // the word, because a screen reader cannot read a triangle.
        boolean descending = EditorState.query().descending();
        addRenderableWidget(new HubWidgets.IconButton(rightX + rightW - pad - dirW, panelTop + pad,
            dirW, 16, descending ? HubTextures.SORT_DOWN : HubTextures.SORT_UP,
            Component.literal(descending ? "Descending" : "Ascending"), pressed ->
        {
            EditorState.query().setDescending(!EditorState.query().descending());
            EditorState.invalidate();
            rebuildControls();
        }));

        // Clear Filters, the drawer's toggle and Unowned, measured and wrapped
        // together. Written out as literals this row needed 264 units of the
        // 166 the panel has at the smallest window the game allows: Unowned was
        // entirely off screen and the very button that opens the drawer was
        // half off it.
        int buttonsY = chipY + chips.lines() * chipH + (chips.lines() - 1) * 2 + 3;
        Packed buttons = pack(barButtonFloors(), barButtonWidths(), trunkRoom, 2);

        // Greyed out when there is nothing to clear, so the button itself
        // reports whether anything is narrowing -- read every frame rather than
        // assigned once, because typing in the search box gives it something to
        // clear without rebuilding (a rebuild there would drop focus mid-word).
        HubWidgets.TextureButton clear = new HubWidgets.TextureButton(
            chipLeft + buttons.x()[0], buttonsY + buttons.line()[0] * (DRAWER_ROW_H + 2),
            buttons.width()[0], DRAWER_ROW_H,
            Component.literal("Clear Filters"), pressed ->
        {
            EditorState.query().clear();
            search.setValue("");
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        });
        clear.setActiveSupplier(() -> !EditorState.query().isClear());
        addRenderableWidget(clear);

        // The rest of the filters live behind this rather than on the bar:
        // there are sixty of them, which is what the official editors offer and
        // far more than fits beside a search box.
        addRenderableWidget(new ChipButton(chipLeft + buttons.x()[1],
            buttonsY + buttons.line()[1] * (DRAWER_ROW_H + 2), buttons.width()[1], DRAWER_ROW_H,
            Component.literal(filtersOpen ? "Filters -" : "Filters +"),
            () -> filtersOpen, pressed ->
        {
            filtersOpen = !filtersOpen;
            openList = null;
            rebuildControls();
        }));
        addRenderableWidget(new ChipButton(chipLeft + buttons.x()[2],
            buttonsY + buttons.line()[2] * (DRAWER_ROW_H + 2), buttons.width()[2], DRAWER_ROW_H,
            Component.literal("Unowned"), EditorState::showUnowned, pressed ->
        {
            EditorState.setShowUnowned(!EditorState.showUnowned());
            trunkScroll = 0;
            rebuildControls();
        }));

        if(filtersOpen)
        {
            // The strip's own top, not a second sum that happens to land near
            // it. The two used to differ by 2, which is exactly how far
            // maxFilterScroll() over-reached the limit the builder enforced.
            buildFilterDrawer(filterViewTop());
        }

        // Sorting a deck is the same idea as sorting the trunk, so it reuses
        // the trunk's chosen order rather than inventing a second one.
        // Both sit INSIDE their panel's padding rather than against the window
        // edge. Done was placed from the window width, which put its right
        // edge on the panel's border now that the panel reaches the bottom.
        // The row rises with the lines it took, so a wrapped row eats into the
        // grid above it rather than being drawn over the panel's bottom edge.
        int deckRowTop = panelTop + panelH - pad
            - geom.deckControlLines() * BUTTON_ROW_H - (geom.deckControlLines() - 1) * 2;
        // Each sized from its own label rather than from a number picked to
        // suit the longest, so they read as one row instead of three
        // differently-padded boxes. Same measure as Save and Exit uses.
        Packed deckRow = pack(deckRowFloors(), deckRowWidths(),
            Math.max(1, leftW - pad * 2), BUTTON_GAP);
        int rowLeft = leftX + pad;

        addRenderableWidget(new HubWidgets.TextureButton(rowLeft + deckRow.x()[0],
            deckRowTop + deckRow.line()[0] * (BUTTON_ROW_H + 2), deckRow.width()[0],
            BUTTON_ROW_H, SORT_DECK_LABEL, pressed ->
        {
            sortDeck();
            refusal = "";
        }));

        // .ydk, beside the deck they act on. Import makes a NEW deck rather
        // than overwriting the open one: a file arriving is not a reason to
        // lose what is already being built.
        addRenderableWidget(new HubWidgets.TextureButton(rowLeft + deckRow.x()[1],
            deckRowTop + deckRow.line()[1] * (BUTTON_ROW_H + 2), deckRow.width()[1],
            BUTTON_ROW_H, IMPORT_LABEL, pressed -> importDeck()));

        addRenderableWidget(new HubWidgets.TextureButton(rowLeft + deckRow.x()[2],
            deckRowTop + deckRow.line()[2] * (BUTTON_ROW_H + 2), deckRow.width()[2],
            BUTTON_ROW_H, EXPORT_LABEL, pressed ->
        {
            DeckFiles.Result result = DeckFiles.export(EditorState.deck());
            refusal = result.message();
        }));

        // What the deck is printed on, on the row that acts on the deck. A
        // sleeve belongs to a DECK exactly as its cards do -- it is stored on
        // the deck and travels with it -- so it is chosen where the deck is
        // worked on rather than in a settings tab beside the mat colour, which
        // is a client preference and a different kind of thing entirely.
        //
        // No label: the swatch IS the label. It already shows what the deck is
        // wearing, and the word beside it only repeated what the picture said.
        addRenderableWidget(new SleeveButton(rowLeft + deckRow.x()[3],
            deckRowTop + deckRow.line()[3] * (BUTTON_ROW_H + 2), deckRow.width()[3],
            BUTTON_ROW_H, Component.empty()));

        // The case is the deck-level cosmetic immediately beside its sleeves.
        addRenderableWidget(new DeckBoxButton(rowLeft + deckRow.x()[4],
            deckRowTop + deckRow.line()[4] * (BUTTON_ROW_H + 2), deckRow.width()[4],
            BUTTON_ROW_H, Component.empty()));

        // Named for what it does rather than for being finished with: the deck
        // is written to the server on the way out, and a player leaving an
        // editor should not have to guess whether that happened.
        int controlsY = panelTop + panelH - pad - BUTTON_ROW_H;
        Component leave = Component.literal("Save and Exit");
        int leaveW = clamp(Math.min(font.width(leave) + 8, Math.max(1, rightW - pad * 2)),
            Math.max(1, rightW - pad * 2), Math.max(80, font.width(leave) + 16));
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - leaveW,
            controlsY, leaveW, BUTTON_ROW_H, leave, pressed -> saveAndExit()));

        // Card size for the collection, directly above Save and Exit. A slider
        // rather than a cycle of fixed sizes: the useful size depends on how
        // many cards you own and how big the window is, and neither is
        // something a fixed list can answer.
        addRenderableWidget(new TrunkSizeSlider(rightX + rightW - pad - leaveW,
            controlsY - SLIDER_GAP - SLIDER_H, leaveW, SLIDER_H));
    }

    /**
     * The deck bar: which deck is open, and what can be done to it.
     * <p>
     * Rename swaps the title for a text field in place rather than opening a
     * dialogue, so the deck stays visible while it is being named -- and
     * commits on Enter or on losing focus, since a rename that is only applied
     * by a button is easy to lose.
     */
    private void buildDeckBar()
    {
        int y = panelTop + pad - 2;
        int right = leftX + leftW - pad;
        int buttonW = 46;

        // Delete is refused for a granted structure deck, so it reports that by
        // being disabled rather than by failing when pressed.
        HubWidgets.TextureButton delete = new HubWidgets.TextureButton(right - buttonW, y,
            buttonW, 16, Component.literal("Delete"), pressed ->
        {
            String error = EditorState.deleteCurrent();
            refusal = error == null ? "" : error;
            rename = null;
            rebuildControls();
        });
        delete.active = EditorState.deck().origin() != DeckList.Origin.STRUCTURE;
        addRenderableWidget(delete);

        addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 2 - 2, y,
            buttonW, 16, Component.literal("Rename"), pressed -> startRename()));

        addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 3 - 4, y,
            buttonW, 16, Component.literal("New"), pressed ->
        {
            EditorState.newDeck();
            rename = null;
            refusal = "";
            rebuildControls();
        }));

        // Stepping through decks, which doubles as showing how many there are.
        List<DeckList> all = EditorState.decks();
        if(all.size() > 1)
        {
            addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 3 - 4 - 34, y,
                16, 16, Component.literal("<"), pressed ->
            {
                EditorState.select((EditorState.currentIndex() - 1 + all.size()) % all.size());
                rename = null;
                rebuildControls();
            }));
            addRenderableWidget(new HubWidgets.TextureButton(right - buttonW * 3 - 4 - 16, y,
                16, 16, Component.literal(">"), pressed ->
            {
                EditorState.select((EditorState.currentIndex() + 1) % all.size());
                rename = null;
                rebuildControls();
            }));
        }

        if(rename != null)
        {
            rename.setX(leftX + pad);
            rename.setY(y + 2 + (rename.getHeight() - 8) / 2);
            rename.setWidth(Math.max(60, right - buttonW * 3 - 4 - 40 - (leftX + pad)));
            addWidget(rename);
            setFocused(rename);
            rename.setFocused(true);
        }
    }

    private void startRename()
    {
        int y = panelTop + pad;
        rename = new EditBox(font, leftX + pad, y, 120, 14, Component.literal("Deck name"));
        rename.setBordered(false);
        rename.setValue(EditorState.deck().name());
        rename.setMaxLength(40);
        rebuildControls();
    }

    /** Applies the pending rename, if any. Silently keeps the old name if taken. */
    private void commitRename()
    {
        if(rename == null)
        {
            return;
        }
        if(!EditorState.rename(rename.getValue()))
        {
            refusal = "That name is already used";
        }
        rename = null;
        rebuildControls();
    }

    /**
     * Orders every part of the deck by the trunk's current sort.
     * <p>
     * The same comparator the right-hand panel is using, so a player who has
     * sorted the trunk by ATK gets a deck sorted by ATK -- one idea of "in
     * order" rather than two.
     */
    /**
     * How a deck is ordered: monsters, then spells, then traps, and each of
     * those alphabetically.
     * <p>
     * This is how a physical deck is laid out and how every list of one is
     * printed, so it is the order a player is looking for when they press the
     * button. It deliberately does NOT follow the trunk's sort control, which
     * it used to: that control is for finding a card among ten thousand, and
     * the answer to "where is my Mirror Force" is a different question from
     * "what shape is my deck".
     */
    private static final java.util.Comparator<Properties> DECK_ORDER =
        java.util.Comparator.comparingInt((Properties card) -> switch(card.getType())
        {
            case SPELL -> 1;
            case TRAP -> 2;
            default -> 0;
        }).thenComparing(card -> card.getName() == null ? "" : card.getName(),
            String.CASE_INSENSITIVE_ORDER);


    /**
     * The extra filters, laid out in rows of chips over the trunk grid.
     * <p>
     * The categories are the ones every Yu-Gi-Oh editor offers, official and
     * fan alike: attribute, the card's own sub-type, monster abilities, the
     * monster's type, and bands for level, ATK and DEF. All of them come from
     * the card model's own enums rather than a list typed out here, so a card
     * property the database gains is a filter this gains with it.
     */
    /**
     * The extra filters: one selector per category, in two columns, with the
     * three numeric bands beneath.
     * <p>
     * This used to be sixty chips in four wrapping rows -- every attribute,
     * every card type, every ability and all twenty-seven monster types laid
     * out at once. It was complete and unreadable, and it needed a scrollbar to
     * show a thing whose whole job is to be glanced at. A selector says what is
     * chosen in one line and opens its list only when asked, which is how the
     * reference editors do it and takes a quarter of the room.
     * <p>
     * One value per category rather than several. Choosing DARK and LIGHT at
     * once is a rare thing to want and it cost four rows of screen to offer.
     * <p>
     * <b>What is offered follows the kind chips.</b> A control appears exactly
     * when a card it could match can be in the pool -- so the monster group is
     * gone while only Spell is lit, and a Counter Trap cannot be asked for of a
     * pool that holds no traps. With nothing lit every kind is in the pool, so
     * everything is offered: that is the state the drawer is first opened in
     * and the state Clear Filters returns it to, and an empty drawer there
     * would be the worst possible first impression of the feature.
     * {@link CardQuery#toggleKind} is what CLEARS a value whose control has
     * gone; this method only decides what to build.
     */
    private void buildFilterDrawer(int top)
    {
        // MEASURED, then clamped, then placed -- in that order and in one call.
        // It used to place first, notice the offset was past the end, and lay
        // itself out a second time WITHOUT removing what the first pass had
        // added: four dropdowns drawn twice a few units apart, and six edit
        // boxes that nothing referenced any more but that stayed in children(),
        // where overControl() found them and they ate clicks invisibly.
        filterContentHeight = layOutDrawer(top, false);
        filterScroll = clamp(0, maxFilterScroll(), filterScroll);
        filterContentHeight = layOutDrawer(top, true);
    }

    /**
     * The drawer's rows, at the current offset.
     *
     * @param place false to work out only how tall the contents come to, true
     *              to add the widgets as well
     * @return the height of the contents, which does not depend on the offset
     */
    private int layOutDrawer(int top, boolean place)
    {
        filterSections.clear();
        int y = top - filterScroll;
        int contentTop = y;

        int inset = DRAWER_INSET;
        int left = rightX + pad;
        int usable = Math.max(1, rightW - pad * 2 - 8);
        int selectorW = geom.selectorW();
        int rowH = DRAWER_ROW_H;
        int rowGap = DRAWER_ROW_GAP;
        // One column when two readable ones will not fit, and the drawer
        // scrolls -- rather than writing out a width the panel does not have.
        boolean twoColumns = geom.selectorColumns() == 2;
        int rightColumn = twoColumns ? left + inset + selectorW + DRAWER_COLUMN_GAP
            : left + inset;
        int columnStep = twoColumns ? 0 : rowH + rowGap;

        java.util.Set<CardQuery.Kind> pool = EditorState.query().kindsInPool();
        int bottom = y;

        if(pool.contains(CardQuery.Kind.MONSTER))
        {
            int fieldsTop = y;
            int fy = y + FRAME_HEADING + inset;
            if(place)
            {
                selector(left + inset, fy, selectorW, rowH, "Attribute", plain(attributeNames()),
                    EditorState.query().attributes(),
                    chosen -> replaceOnly(EditorState.query().attributes(), chosen,
                        EditorState.query()::toggleAttribute));
                selector(rightColumn, fy + columnStep, selectorW, rowH, "Type",
                    plain(speciesNames()), EditorState.query().species(),
                    chosen -> replaceOnly(EditorState.query().species(), chosen,
                        EditorState.query()::toggleSpecies));
            }
            fy += rowH + rowGap + columnStep;
            if(place)
            {
                selector(left + inset, fy, selectorW, rowH, "Monster Type", monsterTypeOptions(),
                    ownedBy(EditorState.query().subTypes(), "MONSTER"),
                    chosen -> replaceOnly(EditorState.query().subTypes(), chosen,
                        EditorState.query()::toggleSubType, "MONSTER"));
                selector(rightColumn, fy + columnStep, selectorW, rowH, "Ability",
                    plain(abilityNames()), EditorState.query().abilities(),
                    chosen -> replaceOnly(EditorState.query().abilities(), chosen,
                        EditorState.query()::toggleAbility));
            }
            fy += rowH + rowGap + columnStep;
            // Tuner is a CHIP rather than an entry in the Ability list because
            // that list is single-choice: a Tuner entry there would make the
            // twelve Pendulum Tuners and the one Flip Tuner unaskable. As its
            // own axis it ANDs with whatever the Ability selector holds.
            if(place)
            {
                tunerChip(left + inset, fy, selectorW, rowH);
            }
            fy += rowH + inset;
            filterSections.add(new Section("Monster", left, fieldsTop, usable, fy - fieldsTop));

            // The bands. Text rather than steppers because a player filtering
            // for 2500 ATK wants to type 2500, not press a button twenty-five
            // times. Monster-only for the same reason the rest of this group
            // is: CardQuery.withinBand rejects every non-monster once a band is
            // set, so a band offered beside a spell pool could only empty it.
            int bandsTop = fy + FRAME_GAP;
            int bandY = bandsTop + FRAME_HEADING + inset;
            int boxW = geom.bandBoxW();
            // Two bands to a row when both fit; one when the second column's
            // caption would otherwise be printed over the first column's max
            // field.
            boolean bandPairs = geom.bandColumns() == 2;
            int bandRight = bandPairs ? rightColumn : left + inset;
            int bandStep = bandPairs ? 0 : rowH + rowGap;
            if(place)
            {
                levelMin = band(left + inset + BAND_CAPTION, bandY, boxW,
                    EditorState.query().minLevel(), 0);
                levelMax = band(left + inset + BAND_CAPTION + boxW + 8, bandY, boxW,
                    EditorState.query().maxLevel(), 0);
                attackMin = band(bandRight + BAND_CAPTION, bandY + bandStep, boxW,
                    EditorState.query().minAttack(), -1);
                attackMax = band(bandRight + BAND_CAPTION + boxW + 8, bandY + bandStep, boxW,
                    EditorState.query().maxAttack(), -1);
            }
            bandY += rowH + rowGap + bandStep;
            if(place)
            {
                defenceMin = band(left + inset + BAND_CAPTION, bandY, boxW,
                    EditorState.query().minDefence(), -1);
                defenceMax = band(left + inset + BAND_CAPTION + boxW + 8, bandY, boxW,
                    EditorState.query().maxDefence(), -1);
            }
            bottom = bandY + rowH + inset;
            filterSections.add(new Section("Numbers", left, bandsTop, usable, bottom - bandsTop));
        }
        else if(place)
        {
            // Forgotten rather than left pointing at the previous layout's
            // boxes: applyBands() reads these six every time one of them
            // changes, and a field surviving into a spell-only drawer would be
            // read off a widget nothing is drawing.
            levelMin = null;
            levelMax = null;
            attackMin = null;
            attackMax = null;
            defenceMin = null;
            defenceMax = null;
        }

        if(pool.contains(CardQuery.Kind.SPELL))
        {
            bottom = subTypeSection(bottom, contentTop, left, usable, inset, rowH, selectorW,
                "Spell", "Spell Type", spellTypeOptions(), "SPELL", place);
        }
        if(pool.contains(CardQuery.Kind.TRAP))
        {
            bottom = subTypeSection(bottom, contentTop, left, usable, inset, rowH, selectorW,
                "Trap", "Trap Type", trapTypeOptions(), "TRAP", place);
        }

        return bottom - contentTop;
    }

    /**
     * One kind's sub-type selector, in a frame of its own.
     *
     * @param after the y the previous section ended at, or the content top when
     *              this is the first section built
     * @return the y this section ended at
     */
    private int subTypeSection(int after, int contentTop, int left, int usable, int inset,
        int rowH, int selectorW, String heading, String caption, List<Option> options,
        String kind, boolean place)
    {
        int sectionTop = after == contentTop ? after : after + FRAME_GAP;
        if(place)
        {
            // Scoped to its own kind: all three sub-type selectors share one
            // set, so an unscoped read shows another selector's value and an
            // unscoped write erases it.
            selector(left + inset, sectionTop + FRAME_HEADING + inset, selectorW, rowH, caption,
                options, ownedBy(EditorState.query().subTypes(), kind),
                chosen -> replaceOnly(EditorState.query().subTypes(), chosen,
                    EditorState.query()::toggleSubType, kind));
        }
        int bottom = sectionTop + FRAME_HEADING + inset + rowH + inset;
        filterSections.add(new Section(heading, left, sectionTop, usable, bottom - sectionTop));
        return bottom;
    }

    /**
     * The Tuner chip: on or off, and ANDed with everything else.
     * <p>
     * Same shape as the Unowned chip on the bar. It is a filter the player can
     * see the state of without opening anything, which is the other half of why
     * it is not a dropdown entry.
     */
    private void tunerChip(int x, int y, int width, int height)
    {
        ChipButton chip = new ChipButton(x, y, width, height, Component.literal("Tuner"),
            () -> EditorState.query().tunersOnly(), pressed ->
        {
            EditorState.query().setTunersOnly(!EditorState.query().tunersOnly());
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        });
        // As selector() does: a row scrolled out of the strip is hidden rather
        // than clipped, so it cannot be clicked through the bar above it.
        chip.visible = inFilterView(y, height);
        chip.active = chip.visible;
        addRenderableWidget(chip);
    }

    /**
     * Makes {@code chosen} the only value in a filter set, or empties it when
     * "Any" was picked.
     * <p>
     * The query keeps sets because several of its filters genuinely take
     * several values; the selectors simply never put more than one in.
     */
    private void replaceOnly(java.util.Set<String> current, String chosen,
        java.util.function.Consumer<String> toggle)
    {
        replaceOnly(current, chosen, toggle, null);
    }

    /**
     * Replaces this axis's value, touching only the keys belonging to one kind.
     * <p>
     * The three sub-type selectors share ONE set, disambiguated by a
     * {@code KIND/} prefix. Clearing the whole set before adding therefore let
     * the Monster selector wipe a Trap value the player had set, and made
     * "Quick-Play spells or Counter traps" impossible to express. Scoped by
     * prefix, each selector owns its own slice and leaves the others alone.
     *
     * @param kind the prefix to confine this to, or null for an unqualified axis
     */
    private void replaceOnly(java.util.Set<String> current, String chosen,
        java.util.function.Consumer<String> toggle, String kind)
    {
        for(String value : new java.util.ArrayList<>(current))
        {
            if(kind == null || value.startsWith(kind + "/"))
            {
                toggle.accept(value);
            }
        }
        if(chosen != null)
        {
            toggle.accept(chosen);
        }
    }

    /** What this selector is showing, from the slice of the set it owns. */
    private static java.util.Set<String> ownedBy(java.util.Set<String> current, String kind)
    {
        if(kind == null)
        {
            return current;
        }
        java.util.Set<String> mine = new java.util.LinkedHashSet<>();
        for(String value : current)
        {
            if(value.startsWith(kind + "/"))
            {
                mine.add(value);
            }
        }
        return mine;
    }

    /**
     * One row of a selector's list: the value the query stores, and the words
     * the player reads.
     * <p>
     * The two differ on the sub-type axes, whose values are qualified by kind
     * so that a Continuous Trap is not also a Continuous Spell. A record rather
     * than a string split at the draw site, so the qualification is a fact
     * about the list and not a convention every reader has to know.
     */
    private record Option(String key, String label)
    {
    }

    /** An axis whose values are their own labels: attribute, species, ability. */
    private static List<Option> plain(List<String> values)
    {
        return values.stream().map(value -> new Option(value, value)).toList();
    }

    /** A selector: its caption, and the value chosen, on one line. */
    private void selector(int x, int y, int width, int height, String caption,
        List<Option> values, java.util.Set<String> current,
        java.util.function.Consumer<String> choose)
    {
        // The LABEL of what is held, not its key: the sub-type axes store
        // "TRAP/Counter" and the player asked for "Counter". A value with no
        // kind on it comes back unchanged, so the other axes go through the
        // same call rather than needing a second path.
        String chosen = current.isEmpty() ? "Any"
            : CardQuery.subTypeLabel(current.iterator().next());
        DropdownButton button = new DropdownButton(x, y, width, height, caption, chosen,
            values, choose);
        button.visible = inFilterView(y, height);
        button.active = button.visible;
        addRenderableWidget(button);
    }

    private int filterViewTop()
    {
        return trunkGridTop() - 4;
    }

    private int filterViewBottom()
    {
        return trunkContentBottom();
    }

    /**
     * How tall the drawer's strip is.
     * <p>
     * ONE expression, because the builder and {@link #maxFilterScroll()} used
     * to measure it separately -- 67 against 69 -- so the scroll limit was
     * permanently 2 greater than the limit the builder enforced, and at the
     * bottom of the drawer every wheel click re-entered the builder.
     */
    private int filterStripHeight()
    {
        return Math.max(0, filterViewBottom() - filterViewTop());
    }

    private int maxFilterScroll()
    {
        return Math.max(0, filterContentHeight - filterStripHeight());
    }

    /**
     * One labelled row of chips, wrapping onto further rows when the row is
     * wider than the panel -- which it is for the twenty-seven monster types.
     *
     * @return the y the next row starts at
     */
    private int chipRow(int top, int chipH, int rowGap, String heading, List<String> values,
        java.util.function.Predicate<String> lit, java.util.function.Consumer<String> toggle,
        int chipW)
    {
        // The heading sits on the frame's own top edge, so the chips get the
        // full width of the box beneath it rather than sharing the row.
        int inset = 4;
        int left = rightX + pad;
        int right = rightX + rightW - pad - 6;
        int chipsTop = top + FRAME_HEADING + inset;
        int x = left + inset;
        int y = chipsTop;
        for(String value : values)
        {
            if(x + chipW > right - inset)
            {
                x = left + inset;
                y += chipH + rowGap;
            }
            String target = value;
            ChipButton chip = new ChipButton(x, y, chipW, chipH, Component.literal(value),
                () -> lit.test(target), pressed ->
            {
                toggle.accept(target);
                EditorState.invalidate();
                trunkScroll = 0;
                rebuildControls();
            });
            // A widget draws itself wherever it is put, and the drawer's
            // scissor cannot reach it, so one scrolled out of the strip is
            // hidden instead. That also stops it being clicked through the bar
            // above, which a merely-clipped chip still could be.
            chip.visible = inFilterView(y, chipH);
            chip.active = chip.visible;
            addRenderableWidget(chip);
            x += chipW + 2;
        }
        int height = (y + chipH + inset) - top;
        filterSections.add(new Section(heading, left, top, right - left, height));
        return top + height + FRAME_GAP;
    }

    /** Whether a row of the drawer is inside the strip the drawer occupies. */
    private boolean inFilterView(int y, int height)
    {
        return y >= filterViewTop() && y + height <= filterViewBottom();
    }

    /** Room above a frame's contents for its heading, and between frames. */
    private static final int FRAME_HEADING = 11;
    private static final int FRAME_GAP = 5;

    /**
     * One filter category: its frame and its heading. Collected while the chips
     * are built, because only then is it known how many rows a category wrapped
     * onto and therefore how tall its frame is.
     */
    private record Section(String heading, int x, int y, int width, int height)
    {
    }

    private final List<Section> filterSections = new java.util.ArrayList<>();

    /**
     * One end of a numeric band.
     *
     * @param unset the value that means "no bound", shown as an empty field
     */
    private EditBox band(int x, int y, int width, int value, int unset)
    {
        EditBox box = new EditBox(font, x + 2, y + 2, width - 4, 12, Component.literal(""));
        box.setBordered(false);
        box.setY(box.getY() + (box.getHeight() - 8) / 2);
        box.setValue(value == unset ? "" : String.valueOf(value));
        // Digits only, and short: the field is a number, so a letter typed
        // into it is refused rather than parsed and quietly ignored.
        // setFilter is gone; a maximum length is the part of it that
        // survives, and the responder already ignores a bad value.
        box.setMaxLength(5);
        box.setResponder(text -> applyBands());
        addWidget(box);
        return box;
    }

    /** Reads the six band fields back into the query. */
    private void applyBands()
    {
        EditorState.query().setLevelRange(number(levelMin, 0), number(levelMax, 0));
        EditorState.query().setAttackRange(number(attackMin, -1), number(attackMax, -1));
        EditorState.query().setDefenceRange(number(defenceMin, -1), number(defenceMax, -1));
        EditorState.invalidate();
        trunkScroll = 0;
    }

    private int number(EditBox box, int unset)
    {
        if(box == null || box.getValue().isEmpty())
        {
            return unset;
        }
        try
        {
            return Integer.parseInt(box.getValue());
        }
        catch(NumberFormatException halfTyped)
        {
            return unset;
        }
    }

    private static List<String> attributeNames()
    {
        return java.util.Arrays.stream(
                de.cas_ual_ty.dueldimension.card.properties.Attribute.values())
            .map(value -> value.name).toList();
    }

    /**
     * A sub-type value as the query stores it, beside the word for it.
     * <p>
     * There used to be ONE list holding all twelve, de-duplicated by name --
     * and since the facet answered a bare name, its single "Continuous" entry
     * matched 503 spells and 558 traps at once and its "Ritual" matched Ritual
     * Monsters alongside Ritual Spells. Three per-kind lists, each key carrying
     * its kind, is what makes those distinct questions. It also puts every list
     * inside the open list's row count: the merged twelve needed thirteen rows
     * for "Any" and its values, and "Counter" was the one entry that was never
     * drawn without scrolling -- the very Counter Trap filter reported missing.
     */
    private static Option subTypeOption(CardQuery.Kind kind, String label)
    {
        return new Option(CardQuery.subTypeKey(kind, label), label);
    }

    private static List<Option> monsterTypeOptions()
    {
        // Normal and Effect are not MonsterType constants: a monster with no
        // explicit type is one or the other depending on whether it has an
        // effect, which is the distinction the official editors draw and the
        // one the subType facet answers with.
        List<Option> options = new java.util.ArrayList<>();
        options.add(subTypeOption(CardQuery.Kind.MONSTER, "Normal"));
        options.add(subTypeOption(CardQuery.Kind.MONSTER, "Effect"));
        for(de.cas_ual_ty.dueldimension.card.properties.MonsterType type
            : de.cas_ual_ty.dueldimension.card.properties.MonsterType.values())
        {
            options.add(subTypeOption(CardQuery.Kind.MONSTER, type.name));
        }
        return options;
    }

    private static List<Option> spellTypeOptions()
    {
        List<Option> options = new java.util.ArrayList<>();
        for(de.cas_ual_ty.dueldimension.card.properties.SpellType type
            : de.cas_ual_ty.dueldimension.card.properties.SpellType.values())
        {
            options.add(subTypeOption(CardQuery.Kind.SPELL, type.name));
        }
        return options;
    }

    private static List<Option> trapTypeOptions()
    {
        List<Option> options = new java.util.ArrayList<>();
        for(de.cas_ual_ty.dueldimension.card.properties.TrapType type
            : de.cas_ual_ty.dueldimension.card.properties.TrapType.values())
        {
            options.add(subTypeOption(CardQuery.Kind.TRAP, type.name));
        }
        return options;
    }

    private static List<String> abilityNames()
    {
        List<String> names = new java.util.ArrayList<>(
            java.util.Arrays.stream(de.cas_ual_ty.dueldimension.card.properties.Ability.values())
                .map(value -> value.name).toList());
        names.add(EditorState.PENDULUM);
        return names;
    }

    private static List<String> speciesNames()
    {
        // All of them, including the two custom-card species: the enum records
        // no flag to tell them apart -- its constructor takes one and throws it
        // away -- so filtering them out here would mean hardcoding their names.
        return java.util.Arrays.stream(
                de.cas_ual_ty.dueldimension.card.properties.Species.values())
            .map(value -> value.name).toList();
    }

    /**
     * Orders each part, moving each copy's artwork with it.
     * <p>
     * Sorted as (card, artwork) PAIRS rather than as a list of passcodes. The
     * arts are stored one per position, so sorting the codes alone leaves every
     * art on whichever copy happens to land in its old slot -- and since a sort
     * moves nearly everything, a deck of three differently dressed Dark
     * Magicians would come back wearing the artwork of whatever sorted into
     * those three places.
     */
    private void sortDeck()
    {
        for(DeckList.Part part : DeckList.Part.values())
        {
            List<EditorState.Copy> known = new java.util.ArrayList<>();
            List<EditorState.Copy> unknown = new java.util.ArrayList<>();
            for(EditorState.Copy copy : EditorState.copiesIn(part))
            {
                (card(copy.code()) == null ? unknown : known).add(copy);
            }
            known.sort(java.util.Comparator.comparing(
                (EditorState.Copy copy) -> card(copy.code()), DECK_ORDER));
            // A card the database does not know still belongs to the deck, so
            // it is kept rather than dropped by the sort.
            known.addAll(unknown);
            EditorState.reorder(part, known);
        }
    }

    static String label(CardQuery.Kind kind)
    {
        return switch(kind)
        {
            case MONSTER -> "Monster";
            case SPELL -> "Spell";
            case TRAP -> "Trap";
        };
    }

    // ---- layout ----

    /** Bounded both ways in one expression, since every size here is. */
    private static int clamp(int min, int max, int value)
    {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * One row of buttons, and the collection's card-size slider above it.
     * <p>
     * Sixteen rather than twenty. Both panels here are grids of cards and the
     * buttons are the frame around them, so every unit this row does not take
     * is a unit of deck or collection -- and at twenty, with a nine-unit font,
     * over half the button was padding. Save and Exit and the card-size slider
     * share it, which is the point: they are one row of controls at one height,
     * not three that happen to agree.
     */
    private static final int BUTTON_ROW_H = 16;
    private static final int SLIDER_GAP = 6;
    private static final int SLIDER_H = 16;

    /**
     * Where the collection's own content -- grid or drawer -- has to stop.
     * <p>
     * The reserve behind it is measured from the widgets actually stacked
     * there. The authored {@code panel.controls} was 28 -- the button row and
     * its padding -- and stayed 28 after the card-size slider was added ABOVE
     * that row, so the open drawer was drawn straight over the slider at every
     * window size and the slider then repainted on top of it, because
     * {@code super.extractRenderState} runs after the drawer is described.
     */
    private int trunkContentBottom()
    {
        return geom.trunkContentBottom();
    }

    private int mainTop()
    {
        // Below the deck NAME as well as the section heading; these two used to
        // be drawn at the same y and overlapped. Everything that asks where a
        // part is goes through here, so subtracting the scroll once moves the
        // drawing and the hit-testing together rather than letting them drift.
        return deckViewTop() + headerH + sectionGap - deckScroll;
    }

    private int mainRows()
    {
        return mainRows;
    }

    private int extraTop()
    {
        return mainTop() + rowsFor(DeckList.Part.MAIN) * (deckCardH + gap) + headerH + sectionGap;
    }

    private int sideTop()
    {
        return extraTop() + rowsFor(DeckList.Part.EXTRA) * (deckCardH + gap) + headerH + sectionGap;
    }

    /**
     * Every derived number for one window size, worked out in one order.
     * <p>
     * A pure function of the window, the font, the layout file, the player's
     * card-size setting and the open deck's contents -- and of NOTHING it
     * itself produces. That is the invariant that lets {@link #rebuildControls}
     * place a widget and the render pass draw beside it and have the two agree.
     */
    private record Geom(
        int leftX, int rightX, int panelTop, int panelH, int leftW, int rightW,
        int deckColumns, int deckCardW, int deckCardH, int deckRows,
        int deckControlLines, int deckReserve, int deckViewTop, int deckViewHeight,
        int trunkChipLines, int trunkButtonLines, int trunkHeaderBottom, int trunkGridTop,
        int trunkReserve, int trunkContentBottom, int trunkColumns, int trunkCardW,
        int trunkCardH, int selectorColumns, int selectorW, int bandColumns, int bandBoxW,
        int listRows)
    {
    }

    /** Rebuilt by {@link #remeasure()}; never read before that has run. */
    private Geom geom;

    /**
     * The lane a grid leaves clear down its right edge for its scrollbar: the
     * bar is 4 wide and sits 2 inside the padding. Both grids subtract it from
     * the width they count columns into, so the last column can never be the
     * thing the bar is drawn on -- which decided who owned those pixels
     * differently in the drawing and in the hit test.
     */
    private static final int BAR_LANE = 6;

    /** Below this a second selector column is unreadable, so there is one. */
    private static final int SELECTOR_MIN = 64;
    /** Below this a band's two fields are unreadable, so they stack. */
    private static final int BAND_MIN = 24;
    /** Room to the left of a band's fields for its caption. */
    private static final int BAND_CAPTION = 34;
    /** The drawer's inner margin and the gap between its two columns. */
    private static final int DRAWER_INSET = 5;
    private static final int DRAWER_COLUMN_GAP = 6;
    /** One row of the drawer, and the air under it. */
    /**
     * Clear Filters, Filters and Unowned, and every row inside the drawer.
     * <p>
     * Fourteen, matching {@code trunk.chipHeight} above it -- these sit
     * directly under the chips and reading as one block of controls is the
     * point. Two units a row, over however many rows the drawer is showing, is
     * the collection getting them back.
     */
    private static final int DRAWER_ROW_H = 14;
    private static final int DRAWER_ROW_GAP = 4;

    /**
     * Takes the measurements and publishes them.
     * <p>
     * <b>Not</b> an override of {@code Screen.resize(int, int)} and deliberately
     * not named for it: that hook sets the size and calls
     * {@code repositionElements()}, which rebuilds every widget through
     * {@code init()}. A method of that name here would start overriding it and
     * the rebuild would stop happening.
     * <p>
     * The panel frame and the two card sizes are copied out into the fields the
     * rest of the screen reads. They have exactly one writer -- this line -- so
     * they are a view of the measurement rather than a second opinion about it.
     */
    private void remeasure()
    {
        geom = measure();
        leftX = geom.leftX();
        rightX = geom.rightX();
        panelTop = geom.panelTop();
        panelH = geom.panelH();
        leftW = geom.leftW();
        rightW = geom.rightW();
        deckCardW = geom.deckCardW();
        deckCardH = geom.deckCardH();
        trunkCardW = geom.trunkCardW();
        trunkCardH = geom.trunkCardH();
        trunkColumns = geom.trunkColumns();
        mainRows = geom.deckRows();

        // Every scroll re-clamped against what was just measured, not against
        // last frame's numbers. The collection's limit in particular divides by
        // the column count, and clamping before the count was recomputed meant
        // the first clamp after init() or a window change used the layout
        // file's seed of 8 where the real count is twelve to twenty-three.
        deckScroll = clamp(0, maxDeckScroll(), deckScroll);
        trunkScroll = clamp(0, maxTrunkScroll(), trunkScroll);
        filterScroll = clamp(0, maxFilterScroll(), filterScroll);
        if(openList != null)
        {
            openListScroll = clamp(0,
                Math.max(0, openList.values.size() + 1 - listRows()), openListScroll);
        }
    }

    /**
     * Lays the whole screen out, once, top to bottom.
     * <p>
     * The order is the point of the method. The window bounds the panels, the
     * panels bound the control rows, the rows decide where each grid starts and
     * the widgets actually stacked at the bottom decide where it stops -- and
     * only then is a card sized to what is left. Every authored number is a
     * CEILING and every count comes from the room, which is the difference
     * between a layout that survives a small window and one that draws its
     * right-hand controls off the side of it.
     * <p>
     * When the room runs out the regions yield in one stated order, so no two
     * of them invent their own: the pool's cell shrinks, then its rows fall,
     * then the control rows wrap onto more lines (which costs more pool rows),
     * then the drawer drops from two selector columns to one and scrolls, then
     * the deck's columns fall, and last the deck's card reaches
     * {@code card.minWidth} and the deck scrolls. Nothing is ever drawn outside
     * its own panel: a region that still will not fit is clipped by that
     * panel's scissor rather than allowed to paint over its neighbour.
     * <p>
     * The floor it all has to survive is <b>320&times;240 GUI units</b>, which
     * is where {@code Window.calculateScale} stops raising the scale.
     */
    private Geom measure()
    {
        Layout layout = Layout.of(LAYOUT);
        float aspect = layout.f("card.aspect", 480F / 700F);
        int cardMinW = Math.max(1, layout.i("card.minWidth", 10));
        int cardMaxW = Math.max(cardMinW, layout.i("card.maxWidth", 72));

        // ---- the panels -------------------------------------------------
        int panelTop = layout.i("panel.top", 34);
        int panelH = Math.max(1, height - panelTop - layout.i("panel.bottom", 30));
        // From an EXPLICIT gap. Taking the split out of (width - pad*3)
        // silently assumed panel.gap equalled panel.pad, so retuning the gap in
        // the layout file made the two panels stop adding up to the window.
        int panelGap = layout.i("panel.gap", 8);
        int split = Math.max(2, width - pad * 2 - panelGap);
        // Each side needs its padding, its scrollbar's lane and one card. The
        // percentage is clamped against that rather than trusted.
        int sideMin = Math.min(split / 2, pad * 2 + BAR_LANE + cardMinW);
        int leftX = pad;
        // Down the middle. The deck was the smaller half at 44 per cent, which
        // is the wrong way round for the panel you are building IN -- and the
        // width it was short of is what the card size is limited by, so every
        // unit of it came off the cards.
        //
        // The fallback is 50 as well. It read 58 while the layout file said 44,
        // so the number in the code was not the number anything ran with.
        int leftW = clamp(sideMin, split - sideMin,
            split * clamp(20, 80, layout.i("panel.leftPercent", 50)) / 100);
        int rightX = leftX + leftW + panelGap;
        int rightW = Math.max(1, width - rightX - pad);

        // ---- the deck panel's bottom row --------------------------------
        // Measured from its labels and wrapped, rather than placed from a fixed
        // origin with no ceiling. Unmeasured, its right edge sat at the same x
        // at EVERY window size while the panel's inner edge moved with the
        // window: Export and Sleeves were drawn inside the collection panel,
        // and overControl() made them swallow clicks there.
        int deckRoom = Math.max(1, leftW - pad * 2);
        Packed deckRow = pack(deckRowFloors(), deckRowWidths(), deckRoom, BUTTON_GAP);
        int deckControlLines = deckRow.lines();
        int deckReserve = pad + deckControlLines * BUTTON_ROW_H
            + (deckControlLines - 1) * 2 + 2;
        // Against the panel's rim, not a pad below it.
        //
        // The pad was there to clear the name ribbon, and the ribbon is gone --
        // so the Main Deck was starting eight units down for something that is
        // no longer drawn. Two is the nine-slice's own border, which is all the
        // clearance a section heading needs; the six units it releases go to
        // the view, and from there into the card size.
        int deckTopInset = 2;
        int deckViewTop = panelTop + deckTopInset + titleH;
        // Measured to where the button row actually starts, rather than by
        // subtracting a reserve that already contains a pad from a figure that
        // had subtracted two. The pad was counted THREE times for a panel with
        // two of them, so ten units between the Side Deck and the Sort row
        // belonged to the deck and were never given to it.
        int deckViewHeight = Math.max(1,
            (panelH - pad - deckControlLines * BUTTON_ROW_H
                - (deckControlLines - 1) * 2 - 2) - (deckTopInset + titleH));

        // ---- the deck grid: count first, size second ---------------------
        // The ten columns are a CEILING. Dividing by ten and then clamping the
        // result UP to card.minWidth broke the invariant the division existed
        // to keep -- at 320 units the honest fit is nine and the floor forced
        // ten, so the row needed 118 units of the 114 it had and the last
        // column was drawn under the scrollbar.
        // The scrollbar's lane is only owed to a deck that scrolls, so the
        // full width is tried first and kept when everything fits. Six units,
        // which at this panel is the difference between twelve columns and
        // thirteen -- and a thirteenth column is a row off the Main Deck.
        int deckUsableW = Math.max(1, leftW - pad * 2 - BAR_LANE);
        int deckRoomy = Math.max(1, leftW - pad * 2);
        // ---- card size and column count, chosen TOGETHER -----------------
        // The column count used to be fixed at deck.mainColumns and only the
        // card size moved. That is what made the cards small: once the card was
        // limited by the panel's HEIGHT, ten columns of it covered barely half
        // the width and the remaining hundred-odd units were margin -- the
        // panel was full and empty at the same time.
        //
        // Columns and size are one decision. Adding a column spends that margin
        // on FEWER ROWS, and fewer rows is exactly what buys a taller card. So
        // this asks for the largest card the three parts can be drawn at, and
        // takes whatever column count that implies, rather than fixing the
        // columns and accepting whatever card is left over.
        //
        // Downward from the largest, so the first fit IS the largest fit. Sixty
        // iterations of integer arithmetic, once per layout.
        int sectionChrome = (layout.i("deck.headerHeight", 12)
            + layout.i("deck.sectionGap", 6)) * 3;
        // The fallback is the old cram: as many columns as the width holds at
        // the smallest card. Reached only when even that does not fit -- a full
        // Main Deck on a very short window -- and then the panel scrolls, as it
        // always did.
        int deckColumns = Math.max(1, (deckUsableW + gap) / (cardMinW + gap));
        int deckCardW = clamp(cardMinW, cardMaxW,
            (deckUsableW - (deckColumns - 1) * gap) / deckColumns);
        // Over COLUMN COUNTS, taking whichever of the two bounds is binding.
        //
        // Stepping the width down and stopping at the first fit only ever asked
        // the width; the height was a pass/fail at the end, so whatever it had
        // left over stayed left over -- which is the band between the Side Deck
        // and the Sort row. Asking both per column count spends it: at a count
        // where the height is the looser bound the card grows into the width,
        // and where the width is looser it grows into the height.
        // Every column count, and for each the widest card that actually fits.
        //
        // "Actually" is the whole of it. Inverting the height budget into a
        // width -- room / rows, times the aspect -- looks equivalent and is
        // not: the card's height comes back through Math.round, so a width the
        // inverse said would fit can round up a unit and overflow. Rounding the
        // other way instead gives up a unit that was there. Neither is the
        // answer; asking deckContentHeight's own sum is.
        //
        // A couple of thousand integer operations once per layout, for a number
        // that is right rather than nearly right.
        int[] partHeld = {EditorState.deck().partFor(DeckList.Part.MAIN).size(),
            EditorState.deck().partFor(DeckList.Part.EXTRA).size(),
            EditorState.deck().partFor(DeckList.Part.SIDE).size()};
        int[] partCap = {DeckList.Part.MAIN.capacity(), DeckList.Part.EXTRA.capacity(),
            DeckList.Part.SIDE.capacity()};
        // Roomy first. A deck that fits has no scrollbar, so there is no lane
        // to leave for one and the grid may have it; a deck that does not fit
        // is owed the lane, and the narrower fit is the honest one.
        DeckFit fit = deckFit(deckRoomy, deckViewHeight, sectionChrome, aspect, gap,
            cardMinW, cardMaxW, partHeld, partCap);
        if(!fit.fits())
        {
            fit = deckFit(deckUsableW, deckViewHeight, sectionChrome, aspect, gap,
                cardMinW, cardMaxW, partHeld, partCap);
        }
        deckColumns = fit.columns();
        deckCardW = fit.cardW();

        int deckCardH = Math.max(8, Math.round(deckCardW / aspect));

        int deckRows = rowsFor(DeckList.Part.MAIN, deckColumns);

        // ---- the collection panel's control band, line by line -----------
        int searchH = layout.i("trunk.searchHeight", 16);
        int chipH = layout.i("trunk.chipHeight", 14);
        int trunkRoom = Math.max(1, rightW - pad * 2);
        int y = panelTop + pad + searchH + 4;
        // Two runs rather than one, so the authored 14 and 16 heights survive
        // and the chips-are-filters / buttons-are-actions grouping does too.
        Packed chips = pack(chipFloors(chipH), chipWidths(chipH), trunkRoom, 2);
        y += chips.lines() * chipH + (chips.lines() - 1) * 2 + 3;
        Packed buttons = pack(barButtonFloors(), barButtonWidths(), trunkRoom, 2);
        y += buttons.lines() * DRAWER_ROW_H + (buttons.lines() - 1) * 2;
        int trunkHeaderBottom = y;
        int trunkGridTop = trunkHeaderBottom + 6;

        // ---- what the collection keeps clear at the bottom ---------------
        int trunkReserve = pad + BUTTON_ROW_H + SLIDER_GAP + SLIDER_H + 2;
        int trunkContentBottom = panelTop + panelH - trunkReserve;

        // ---- the pool's cell, fitted to the pool's own region -------------
        // It used to be seeded from the DECK's card width and capped by the
        // DECK's height budget; the collection's own region only ever picked a
        // column count out of it.
        int poolW = Math.max(1, rightW - pad * 2 - BAR_LANE);
        int poolH = Math.max(1, trunkContentBottom - 12 - trunkGridTop);
        // The COUNT comes from the smallest cell worth drawing and the SIZE
        // from the fit, which is what makes a bigger window show more rather
        // than the same few columns with the rest of it as margin. That
        // counting cell is trunk.cellMin and not card.minWidth: the latter is
        // the absolute floor at which a card is still a card, and counting from
        // it would fill the panel with 10-unit tiles nobody can read.
        int cellMin = Math.max(cardMinW, layout.i("trunk.cellMin", 18));
        int maxColumns = Math.max(1, (poolW + gap) / (cellMin + gap));
        int cols = maxColumns;
        int widthFit = (poolW + gap) / cols - gap;
        int minCellH = Math.max(8, Math.round(cellMin / aspect));
        int poolRows = Math.max(1, (poolH + gap) / (minCellH + gap));
        int heightFit = Math.round(((poolH + gap) / (float)poolRows - gap) * aspect);
        int cell = clamp(cardMinW, cardMaxW, Math.min(widthFit, heightFit));
        // The player's own scale LAST, so it multiplies the size the collection
        // would otherwise have chosen, and bounded by the region so the largest
        // setting still leaves one whole column.
        cell = clamp(cardMinW, Math.max(cardMinW, poolW), Math.round(cell * trunkScale));
        int trunkCardH = Math.max(8, Math.round(cell / aspect));
        // Re-fit against the cell the player actually got, with no ceiling.
        //
        // maxColumns is how many fit at trunk.cellMin, which is the right
        // ceiling only while the size is being chosen automatically -- and the
        // Card Size slider goes BELOW cellMin. Capping there meant shrinking
        // the cards kept ten columns and simply made them smaller, so the grid
        // tucked itself into the left of its panel and left the rest bare
        // instead of wrapping more cards onto each row.
        //
        // Dropping the cap only affects that direction: a cell that GREW
        // divides into fewer columns anyway, so the ceiling was never what
        // stopped it overrunning -- the division is.
        int trunkColumns = Math.max(1, (poolW + gap) / (cell + gap));

        // ---- the filter drawer -------------------------------------------
        // The authored 80 was a FLOOR on a value that is already the fair share
        // of the box, so at 320 units two columns needed 176 inside a 142-wide
        // frame and the right-hand one was drawn past the edge of the screen.
        // It is the column COUNT that yields now, and the drawer scrolls.
        int usable = Math.max(1, rightW - pad * 2 - 8);
        int share = (usable - DRAWER_INSET * 2 - DRAWER_COLUMN_GAP) / 2;
        int selectorColumns = share >= SELECTOR_MIN ? 2 : 1;
        int selectorW = Math.max(1,
            selectorColumns == 2 ? share : usable - DRAWER_INSET * 2);
        int bandColumns = selectorColumns;
        int bandBoxW = (selectorW - BAND_CAPTION - 10) / 2;
        if(bandBoxW < BAND_MIN)
        {
            // One band per row rather than a width the panel does not have.
            bandColumns = 1;
            bandBoxW = (usable - DRAWER_INSET * 2 - BAND_CAPTION - 10) / 2;
        }
        bandBoxW = Math.max(1, bandBoxW);

        int strip = Math.max(0, trunkContentBottom - (trunkGridTop - 4));
        int listRows = clamp(1, LIST_ROWS_MAX, (strip - 4) / LIST_ROW_H);

        return new Geom(leftX, rightX, panelTop, panelH, leftW, rightW,
            deckColumns, deckCardW, deckCardH, deckRows,
            deckControlLines, deckReserve, deckViewTop, deckViewHeight,
            chips.lines(), buttons.lines(), trunkHeaderBottom, trunkGridTop,
            trunkReserve, trunkContentBottom, trunkColumns, cell, trunkCardH,
            selectorColumns, selectorW, bandColumns, bandBoxW, listRows);
    }

    // ---- rows of controls, measured and wrapped ----

    /**
     * A row of controls packed into the width it has.
     * <p>
     * Positions are relative to the row's left edge, so the same packing can be
     * measured in {@link #measure()} and placed in {@link #rebuildControls()}
     * without either knowing where the other put it.
     */
    private record Packed(int[] x, int[] line, int[] width, int lines)
    {
    }

    /**
     * Sizes a row from its labels and wraps it, greedily.
     * <p>
     * Each control is at most its authored width, at least what its own label
     * needs, and never wider than the whole line: {@code HubWidgets.drawLabel}
     * CENTRES its text with no ellipsis, so a control narrower than its label
     * bleeds out of BOTH ends of itself rather than being clipped.
     */
    private static Packed pack(int[] floor, int[] want, int room, int gap)
    {
        int count = floor.length;
        int[] width = new int[count];
        int[] x = new int[count];
        int[] line = new int[count];
        int at = 0;
        int on = 0;
        for(int i = 0; i < count; i++)
        {
            // Clamped to a SHARE of the line, not to the authored width. Taking
            // the full authored width whenever it fits means only the line
            // COUNT ever yields, so the bottom row wrapped at the reference
            // size and the collection fell to a single visible row at 320x240.
            // A share lets every item give a little instead of one item taking
            // everything and pushing its neighbour onto a new line.
            int share = Math.max(1, (room - gap * (count - 1)) / Math.max(1, count));
            width[i] = clamp(Math.min(floor[i], room), room,
                Math.min(Math.min(want[i], room), Math.max(share, Math.min(floor[i], room))));
            // Never a wrap before the first item: a line has to hold something,
            // even when what it holds is wider than it is.
            if(i > 0 && at + width[i] > room)
            {
                on++;
                at = 0;
            }
            x[i] = at;
            line[i] = on;
            at += width[i] + gap;
        }
        return new Packed(x, line, width, on + 1);
    }

    /** The four labels on the deck panel's bottom row, in the order drawn. */
    // "Sort", not "Sort Deck". It sits on the deck panel's own row, under the
    // deck, beside Import and Export -- which do not say what they import or
    // export either, because the panel they are on has already said it. The
    // row's widths are measured from these labels, so the button narrows to
    // suit rather than keeping the old one's room.
    private static final Component SORT_DECK_LABEL = Component.literal("Sort");
    private static final Component IMPORT_LABEL = Component.literal("Import");
    private static final Component EXPORT_LABEL = Component.literal("Export");

    private int[] deckRowWidths()
    {
        return new int[] {buttonWidth(SORT_DECK_LABEL), buttonWidth(IMPORT_LABEL),
            buttonWidth(EXPORT_LABEL), swatchWidth(), swatchWidth()};
    }

    private int[] deckRowFloors()
    {
        // The same as the wants, for the reason barButtonFloors gives: a button
        // that is its text plus two has nothing left to give.
        return deckRowWidths();
    }

    /** Monster, Spell, Trap, then the star -- which is square-ish, not a label. */
    private int[] chipWidths(int chipH)
    {
        // Each chip its own label plus two, rather than one authored width for
        // all of them: Spell and Trap are half of Monster and were padded out
        // to match it. Same value as chipFloors below, deliberately -- a floor
        // WIDER than its want silently breaks pack, and one expression for both
        // is how they are kept from drifting into that.
        CardQuery.Kind[] kinds = CardQuery.Kind.values();
        int[] widths = new int[kinds.length + 1];
        for(int i = 0; i < kinds.length; i++)
        {
            widths[i] = font.width(label(kinds[i])) + TEXT_PAD * 2;
        }
        widths[kinds.length] = chipH + 6;
        return widths;
    }

    private int[] chipFloors(int chipH)
    {
        CardQuery.Kind[] kinds = CardQuery.Kind.values();
        int[] floors = new int[kinds.length + 1];
        for(int i = 0; i < kinds.length; i++)
        {
            floors[i] = font.width(label(kinds[i])) + TEXT_PAD * 2;
        }
        floors[kinds.length] = chipH + 6;
        return floors;
    }

    /** Clear Filters, the drawer's own toggle, and Unowned. */
    private Component filtersLabel()
    {
        // Measured against BOTH labels, so pressing it cannot re-flow the row.
        return Component.literal(font.width("Filters -") > font.width("Filters +")
            ? "Filters -" : "Filters +");
    }

    /**
     * Measured from the labels rather than written down.
     * <p>
     * These were {@code {92, 72, 78}} against words needing 64, 43 and 42 --
     * so Filters and Unowned carried about thirty units of nothing each, and
     * the row asked for more than the panel had and wrapped Unowned onto a
     * line of its own. Ten of padding, the same as the deck panel's row uses,
     * so the two rows of this screen are padded alike.
     */
    private int[] barButtonWidths()
    {
        return new int[] {buttonWidth(Component.literal("Clear Filters")),
            buttonWidth(filtersLabel()), buttonWidth(Component.literal("Unowned"))};
    }

    private int[] barButtonFloors()
    {
        // The same as the wants: there is nothing left to squeeze once a button
        // is its text plus two, so the floor IS the width.
        return barButtonWidths();
    }

    /** The sort button, wide enough for whichever order is showing. */
    private int sortWidth()
    {
        int widest = 0;
        for(CardQuery.Sort sort : CardQuery.Sort.values())
        {
            widest = Math.max(widest, font.width(sort.label()));
        }
        // The widest order it can ever show, plus the same two each side every
        // other button here gets -- so pressing it cannot re-flow the row, and
        // it is no wider than the longest word it will hold.
        return clamp(widest + TEXT_PAD * 2, Math.max(1, rightW - pad * 2),
            Math.min(widest + TEXT_PAD * 2, Layout.of(LAYOUT).i("trunk.sortWidth", 44)));
    }

    /**
     * The direction arrow, square with the row it is in.
     * <p>
     * No longer measured from a word: it does not carry one. The layout's
     * {@code trunk.dirWidth} is still honoured as a ceiling for anyone who has
     * tuned it, but 16 is what a 16-high row wants.
     */
    private int dirWidth()
    {
        return clamp(12, Math.max(1, rightW - pad * 2),
            Math.min(16, Layout.of(LAYOUT).i("trunk.dirWidth", 30)));
    }

    /** Whatever the sort pair leaves of the first line, down to a floor of 40. */
    private int searchWidth()
    {
        return Math.max(40, rightW - pad * 2 - sortWidth() - dirWidth() - 12);
    }

    /**
     * Whether a point is over one of the widgets rather than over a grid.
     * <p>
     * Asked before the grids are, so a control drawn on top of a card grid is
     * also clicked before it. Uses the widgets' own bounds rather than a
     * rectangle repeated here, so moving a button cannot leave this behind.
     */
    private boolean overControl(double mouseX, double mouseY)
    {
        for(net.minecraft.client.gui.components.events.GuiEventListener child : children())
        {
            if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                && widget.visible && widget.isMouseOver(mouseX, mouseY))
            {
                return true;
            }
        }
        return false;
    }

    /** The strip of the left panel the deck's sections are drawn in. */
    private int deckViewTop()
    {
        return geom.deckViewTop();
    }

    private int deckViewHeight()
    {
        // The reserve below is the button row as it was actually measured and
        // wrapped, not the authored 28 -- which described one line and knew
        // nothing about a row that had to take two.
        return geom.deckViewHeight();
    }

    /** How tall the three sections are altogether, headings included. */
    private int deckContentHeight()
    {
        int rows = rowsFor(DeckList.Part.MAIN)
            + rowsFor(DeckList.Part.EXTRA) + rowsFor(DeckList.Part.SIDE);
        return (headerH + sectionGap) * 3 + rows * (deckCardH + gap);
    }

    /** How far the trunk can scroll, in rows. */
    private int maxTrunkScroll()
    {
        int rows = (EditorState.visible().size() + trunkColumns - 1) / trunkColumns;
        return Math.max(0, rows - trunkVisibleRows());
    }

    private int maxDeckScroll()
    {
        return Math.max(0, deckContentHeight() - deckViewHeight());
    }

    private int rowsFor(DeckList.Part part)
    {
        return rowsFor(part, partColumns(part));
    }

    /**
     * The overload {@link #measure()} uses.
     * <p>
     * It is handed the column count rather than reaching back through
     * {@link #partColumns} for it, because partColumns reads what measure() is
     * in the middle of producing.
     */
    /**
     * How many rows a part takes, as a function of its numbers alone.
     * <p>
     * Static so {@link #deckFit} can be tested without a deck, a screen or a
     * font. The instance overload below is this one with the open deck's
     * figures filled in.
     */
    static int rowsFor(int held, int capacity, int columns)
    {
        // Exactly the rows the cards need, and no spare one.
        //
        // A spare row used to be added whenever the last row came out FULL, as
        // somewhere to drop the next card. What that draws is an entirely empty
        // row inside the part's container -- 48 cards across 12 columns is four
        // full rows and then a band of nothing -- and it is only ever there for
        // the column counts that happen to divide the deck, so it appears and
        // vanishes as cards are added. Dropping still works without it: place()
        // appends to whichever part the cursor is over and does not need a slot
        // under it, and the grid re-flows to hold what it was given.
        return Math.max(1, Math.min((int)Math.ceil(capacity / (double)columns),
            (int)Math.ceil(held / (double)columns)));
    }

    /**
     * What a deck panel of this size settles on.
     *
     * @param fits false when even the smallest card overflowed, in which case
     *             this is the best cram available and the panel scrolls
     */
    record DeckFit(int columns, int cardW, int cardH, int rows, int content, boolean fits)
    {
    }

    /**
     * The deck grid's size and column count, as a pure function of the room.
     * <p>
     * Pulled out of {@code measure} so it can be checked against numbers rather
     * than against a screenshot. Everything it needs is arithmetic -- the
     * panel, the card aspect, and how many cards each part holds -- so a test
     * can ask it exactly what a given window produces, which is what this
     * screen's layout kept being wrong about.
     *
     * @param held     cards in main, extra and side
     * @param capacity the three limits, which cap a part's rows
     */
    static DeckFit deckFit(int usableW, int viewH, int chrome, float aspect, int gap,
        int cardMin, int cardMax, int[] held, int[] capacity)
    {
        int bestW = 0;
        int bestFill = -1;
        int bestColumns = 1;
        int bestRows = 1;
        for(int columns = 1; columns <= Math.max(1, usableW); columns++)
        {
            int byWidth = (usableW - (columns - 1) * gap) / columns;
            if(byWidth < cardMin)
            {
                // Narrower from here on, so there is nothing further to find.
                break;
            }
            int rows = 0;
            for(int part = 0; part < held.length; part++)
            {
                rows += rowsFor(held[part], capacity[part], columns);
            }
            for(int candidate = Math.min(cardMax, byWidth); candidate >= cardMin; candidate--)
            {
                int height = Math.max(8, Math.round(candidate / aspect));
                int content = chrome + rows * (height + gap);
                if(content > viewH)
                {
                    continue;
                }
                // Bigger wins; between equals, the one that leaves least of the
                // panel empty -- which is the band above the Sort row -- and
                // between those, the one with the most columns.
                //
                // That last is not a nicety. Several column counts often reach
                // the same card at the same height, and the fewest of them
                // wins by arriving first: ten columns of a thirteen-wide card
                // covers 148 units of a 185-unit panel and reads as a sparse
                // grid with a margin down one side, where twelve covers 178.
                if(candidate > bestW
                    || (candidate == bestW && content > bestFill)
                    || (candidate == bestW && content == bestFill && columns > bestColumns))
                {
                    bestW = candidate;
                    bestFill = content;
                    bestColumns = columns;
                    bestRows = rows;
                }
                break;
            }
        }
        if(bestW < cardMin)
        {
            // Nothing fits: as many columns as the width holds at the smallest
            // card, and the panel scrolls, as it always did.
            bestColumns = Math.max(1, (usableW + gap) / (cardMin + gap));
            bestW = Math.max(cardMin,
                Math.min(cardMax, (usableW - (bestColumns - 1) * gap) / bestColumns));
            bestRows = 0;
            for(int part = 0; part < held.length; part++)
            {
                bestRows += rowsFor(held[part], capacity[part], bestColumns);
            }
        }
        int cardH = Math.max(8, Math.round(bestW / aspect));
        int content = chrome + bestRows * (cardH + gap);
        return new DeckFit(bestColumns, bestW, cardH, bestRows, content, content <= viewH);
    }

    /**
     * Where a deck section's grid starts, centred in the container it sits in.
     * <p>
     * The columns are whole cards, so they almost never add up to the panel's
     * width exactly -- and the leftover all fell on the RIGHT, because every
     * card was placed from {@code leftX + pad} while the container was drawn
     * across the whole panel. A dozen units down one side is enough to read as
     * a grid that has slipped out of its box.
     * <p>
     * Read by the renderer AND by {@code slotIndexAt}: they have to agree about
     * where column zero is, or the card under the cursor is not the card that
     * gets picked up.
     */
    private int deckGridLeft()
    {
        int columns = geom.deckColumns();
        int containerLeft = leftX + pad - 2;
        int containerWidth = leftW - pad * 2 + 4;
        int gridWidth = columns * deckCardW + (columns - 1) * gap;
        return containerLeft + Math.max(0, (containerWidth - gridWidth) / 2);
    }

    private int rowsFor(DeckList.Part part, int columns)
    {
        // Delegated, not a second copy. These two were the same three lines
        // written twice, which is one edit away from the grid being measured
        // by one rule and drawn by the other.
        return rowsFor(EditorState.deck().partFor(part).size(), part.capacity(), columns);
    }

    private int partTop(DeckList.Part part)
    {
        return switch(part)
        {
            case MAIN -> mainTop();
            case EXTRA -> extraTop();
            case SIDE -> sideTop();
        };
    }

    /**
     * How many columns a part is drawn in.
     * <p>
     * The measured count, not the authored ten: {@code rowsFor},
     * {@code slotIndexAt}, {@code partTop} and the render loop all read it, so
     * the grid re-flows for drawing and for hit-testing off this one line.
     */
    private int partColumns(DeckList.Part part)
    {
        return geom.deckColumns();
    }

    /** Which slot of which part a point falls in, or null. */
    private DeckList.Part partAt(double mouseX, double mouseY)
    {
        // A row scrolled out of sight is not clickable. Without this a card
        // hidden above the strip could still be picked up by clicking the deck
        // name it was hiding behind.
        if(mouseY < deckViewTop() || mouseY >= deckViewTop() + deckViewHeight())
        {
            return null;
        }
        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            int rows = rowsFor(part);
            if(mouseX >= leftX + pad && mouseX < leftX + leftW - pad
                && mouseY >= top && mouseY < top + rows * (deckCardH + gap))
            {
                return part;
            }
        }
        return null;
    }

    private int slotIndexAt(DeckList.Part part, double mouseX, double mouseY)
    {
        int columns = partColumns(part);
        int cellW = deckCardW + gap;
        int column = (int)((mouseX - deckGridLeft()) / cellW);
        int row = (int)((mouseY - partTop(part)) / (deckCardH + gap));
        if(column < 0 || column >= columns || row < 0)
        {
            return -1;
        }
        return row * columns + column;
    }

    /**
     * How many rows of the trunk are on screen.
     * <p>
     * Stops short of the controls, which since the panels reach the bottom of
     * the screen are drawn OVER the grid rather than below it. A row hidden
     * behind the Done button is a row a player cannot click.
     */
    /**
     * Where the collection's card count goes: against the bottom edge of the
     * grid it counts.
     * <p>
     * It used to be placed from the panel's bottom, so it floated in whatever
     * space happened to be left under the last row -- and moved further away
     * as the card size changed, since the row count changes with it.
     */
    /**
     * How far under the grid the card count sits.
     * <p>
     * Five, not three. At three the digits sat against the bottom row of cards
     * with no air between them.
     */
    private static final int COUNT_DROP = 5;

    /**
     * And what the grid therefore has to leave for it.
     * <p>
     * Derived from the drop and the font rather than written as 12 beside a
     * drop of 3. Those two agreed by coincidence, so moving the count two units
     * down pushed it two units past the space the grid had set aside -- which
     * is the sort of thing that shows up later as a caption clipped on one
     * window size and not another.
     */
    private int countReserve()
    {
        return COUNT_DROP + font.lineHeight;
    }

    private int trunkCountY()
    {
        // The grid's real bottom edge: n rows carry n-1 gaps, so counting a
        // trailing one put this three units lower than the cards it labels and
        // could push it past trunkContentBottom on a tight panel.
        int rows = trunkVisibleRows();
        return trunkGridTop() + rows * trunkCardH + (rows - 1) * gap + COUNT_DROP;
    }

    private int trunkVisibleRows()
    {
        // The card count's own strip, then the reserve the slider and the
        // button row occupy.
        int bottom = trunkContentBottom() - countReserve();
        // The gap is added BACK before dividing, because n rows carry n-1 gaps
        // and not n: n rows fit when n*h + (n-1)*g <= room, which rearranges to
        // n <= (room + g) / (h + g).
        //
        // Without it this asked how many rows fit if each carried a trailing
        // gap, and answered one fewer whenever the remainder was at least a
        // card tall. That is the empty band under the collection -- a whole row
        // of cards, measured as fitting and then not drawn. The measurement
        // beside it always had the +gap (see poolRows), so the two disagreed by
        // exactly one row and only the drawn one was visible.
        return Math.max(1, (bottom - trunkGridTop() + gap) / (trunkCardH + gap));
    }

    /**
     * Below the search row, the chip rows and the Clear row -- as those rows
     * actually came out.
     * <p>
     * It used to restate that stack as a literal sum while
     * {@link #rebuildControls} computed the same chain independently. The two
     * agreed by coincidence, and every consumer of the pool's top went through
     * the copy -- so a control row that wrapped onto a second line would have
     * left the grid drawn under it and hit-testing a row out of register.
     */
    private int trunkGridTop()
    {
        return geom.trunkGridTop();
    }

    private int trunkIndexAt(double mouseX, double mouseY)
    {
        if(filtersOpen)
        {
            // The grid is not drawn while the drawer is open, so nothing in it
            // is under the cursor either.
            return -1;
        }
        int gridTop = trunkGridTop();
        int visibleRows = trunkVisibleRows();
        int cellW = trunkCardW + gap;
        // Bounded at the BOTTOM as well as the top. It was not, so the strip
        // below the last row still resolved to a row number, and hovering over
        // the card count previewed a card that was not on screen at all.
        if(mouseX < rightX + pad || mouseX >= rightX + pad + trunkColumns * cellW
            || mouseY < gridTop || mouseY >= gridTop + visibleRows * (trunkCardH + gap))
        {
            return -1;
        }
        int column = (int)((mouseX - (rightX + pad)) / cellW);
        int row = (int)((mouseY - gridTop) / (trunkCardH + gap));
        if(column < 0 || column >= trunkColumns || row < 0 || row >= visibleRows)
        {
            return -1;
        }
        return (row + trunkScroll) * trunkColumns + column;
    }

    // ---- interaction ----

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        boolean doubleClick = false;
        // The artwork picker before ANYTHING, the scrollbar included. That bar
        // is tested first below and is still under the scrim, so a click on
        // where it used to be would scrub the collection behind the picker.
        if(altCard != null)
        {
            // Its Back button is the only widget there is, and it is drawn
            // after the overlay, so it gets the click first.
            if(overControl(event.x(), event.y()) && super.mouseClicked(vanillaX, vanillaY, vanillaButton))
            {
                return true;
            }
            clickAltArts(event.x(), event.y(), event.button());
            return true;
        }
        // The scrollbar before anything else: it sits over the collection grid,
        // whose card hit test would otherwise swallow the click. Whichever of
        // the two is actually on screen -- they share an x, and only one of
        // them is ever drawn.
        if(event.button() == 0 && (filtersOpen
            ? grabFilterBar(event.x(), event.y()) : grabTrunkBar(event.x(), event.y())))
        {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // An open menu takes the click before anything else, so choosing from
        // it cannot also pick up the card underneath.
        if(menuCard != null)
        {
            if(handleMenuClick(mouseX, mouseY))
            {
                return true;
            }
            closeMenu();
        }
        if(filtersOpen && clickOpenList(mouseX, mouseY))
        {
            return true;
        }
        if(button == 1 && search != null && search.isMouseOver(mouseX, mouseY))
        {
            // Right-click empties a search box. Selecting the text and deleting
            // it works, but clearing a filter is common enough to deserve one
            // gesture rather than three.
            search.setValue("");
            search.setFocused(true);
            return true;
        }
        if(button == 1)
        {
            return openMenu(mouseX, mouseY);
        }
        // A button gets the click before the grid underneath it. The panels
        // now reach the bottom of the screen, so the controls sit OVER the
        // card grids rather than below them -- and the grid was being asked
        // first, which is why pressing Done picked up whatever card happened
        // to be behind it.
        if(carried == null && overControl(mouseX, mouseY)
            && super.mouseClicked(vanillaX, vanillaY, vanillaButton))
        {
            return true;
        }
        refusal = "";
        boolean shift = event.hasShiftDown();

        DeckList.Part part = partAt(mouseX, mouseY);
        if(part != null)
        {
            int index = slotIndexAt(part, mouseX, mouseY);
            List<Integer> cards = EditorState.deck().partFor(part);
            if(carried != null)
            {
                place(part);
                return true;
            }
            if(index >= 0 && index < cards.size())
            {
                if(shift)
                {
                    // Shift-click in the deck sends a card back to the trunk,
                    // which is simply removing it: the trunk never lost it.
                    // Through EditorState so the artwork list closes up behind
                    // it; a bare cards.remove leaves every copy after this one
                    // wearing its neighbour's art.
                    EditorState.removeCard(part, index);
                    return true;
                }
                int code = cards.get(index);
                // Read before the removal, and carried on the cursor: this copy
                // may be wearing an artwork, and it keeps it wherever it lands.
                carriedArt = EditorState.removeCard(part, index);
                carried = card(code);
                carriedFrom = part;
                carriedIndex = index;
                pressedOnCard = true;
                pressX = mouseX;
                pressY = mouseY;
                return true;
            }
            return true;
        }

        int trunkIndex = trunkIndexAt(mouseX, mouseY);
        if(trunkIndex >= 0)
        {
            List<Properties> shown = EditorState.visible();
            if(trunkIndex < shown.size())
            {
                Properties picked = shown.get(trunkIndex);
                if(shift)
                {
                    // The obvious destination: extra-deck monsters go to the
                    // Extra grid, everything else to Main.
                    add(picked, picked.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN);
                }
                else
                {
                    carried = picked;
                    carriedFrom = null;
                    carriedIndex = -1;
                    // A card taken out of the collection is a new copy, so it
                    // wears the printing the player owns -- decided here, at the
                    // moment it comes into existence, rather than where it lands,
                    // because the cursor has to be drawn in it on the way there.
                    carriedArt = EditorState.defaultArtFor((int)picked.getId());
                    pressedOnCard = true;
                    pressX = mouseX;
                    pressY = mouseY;
                }
                return true;
            }
        }

        if(carried != null)
        {
            // Dropped on nothing: put it back where it came from rather than
            // losing it.
            returnCarried();
            return true;
        }
        return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
    }

    private void place(DeckList.Part part)
    {
        Properties held = carried;
        if(held == null)
        {
            return;
        }
        carried = null;
        if(carriedFrom == part)
        {
            // Moved within the same grid: it was already removed, so this is
            // just putting it back -- in the artwork it was picked up in.
            EditorState.addCard(part, (int)held.getId(), carriedArt);
            carriedFrom = null;
            carriedArt = 0;
            return;
        }
        // The carried artwork travels ACROSS grids too. This branch is what a
        // drag from Main to Side takes, and appending on art 0 here is how a
        // dressed copy would silently undress itself by being moved.
        if(!add(held, part, carriedArt))
        {
            returnCarriedTo(held);
        }
        carriedFrom = null;
        carriedArt = 0;
    }

    private void returnCarried()
    {
        Properties held = carried;
        carried = null;
        returnCarriedTo(held);
    }

    private void returnCarriedTo(Properties held)
    {
        if(held != null && carriedFrom != null)
        {
            // Card and artwork put back at the same index in one call, so
            // nothing can insert one and leave the other a place out.
            // insertCard does the clamping this used to do here.
            EditorState.insertCard(carriedFrom, carriedIndex, (int)held.getId(), carriedArt);
        }
        carriedFrom = null;
        carriedIndex = -1;
        carriedArt = 0;
    }

    /** Menu row height and width; small, since it holds two choices. */
    private static final int MENU_ROW = 14;
    /** Breathing room either side of the widest row. */
    private static final int MENU_PAD = 7;

    /**
     * Space above the first row and below the last. Equal at both ends: the box
     * used to be a row and a half taller than its rows with all the slack at
     * the bottom, which read as a menu sagging inside an oversized frame.
     */
    private static final int MENU_EDGE = 5;

    /** Right-click opens a menu on whatever card is under the cursor. */
    private boolean openMenu(double mouseX, double mouseY)
    {
        Properties target = cardAt(mouseX, mouseY);
        if(target == null)
        {
            closeMenu();
            return false;
        }
        menuCard = target;
        menuPart = partAt(mouseX, mouseY);
        menuIndex = menuPart == null ? -1 : slotIndexAt(menuPart, mouseX, mouseY);
        // Placed at the pointer, then pulled back so the whole menu is on
        // screen: opened near the right or bottom edge it used to hang off it,
        // and the rows that fell outside could not be read or clicked.
        menuX = Math.max(0, Math.min((int)mouseX, width - menuWidth()));
        menuY = Math.max(0, Math.min((int)mouseY, height - menuHeight()));
        refusal = "";
        return true;
    }

    private void closeMenu()
    {
        menuCard = null;
        menuPart = null;
        menuIndex = -1;
    }

    /**
     * The labels this menu is showing, in the order they are drawn: Favourite,
     * Card Info, +1, then -1 when the click was on a card already in the deck.
     * <p>
     * One list rather than a row count and a set of draw calls that each know
     * their own index: adding "Card Info" to a menu sized by a fixed constant
     * is what pushed it out of its own box. The row numbers below are read off
     * this list for the same reason.
     */
    private List<String> menuLabels()
    {
        List<String> labels = new ArrayList<>();
        labels.add(menuCard != null && EditorState.isFavourite((int)menuCard.getId())
            ? "Unstar" : "Favourite");
        labels.add("Card Info");
        labels.add("+1");
        if(menuPart != null)
        {
            labels.add("-1");
            // Only for a copy IN the deck, and only for the hundred or so cards
            // that have more than one artwork. Artwork is stored per position,
            // so there is no copy to dress on the collection side -- and an
            // entry that opened a picker with one tile in it would be an entry
            // that reads as broken.
            if(hasAltArt(menuCard))
            {
                labels.add(ALT_ARTS);
            }
        }
        return labels;
    }

    /** The one label the artwork picker is opened from. */
    private static final String ALT_ARTS = "Alt Arts";

    /**
     * Whether a card was printed with more than one artwork.
     * <p>
     * Asked of {@code getImages()} and never of {@code getImageIndicesAmt()}:
     * the array is what the database actually delivered for this card, and it
     * is the same array {@code getImageURL} indexes to fetch one. {@code images}
     * is null on the placeholder card, which is why the null is tested.
     * <p>
     * About 122 of the 13,826 cards answer true. Everything else pays one array
     * length read and gets no marker, no menu entry and no picker.
     */
    static boolean hasAltArt(Properties card)
    {
        String[] images = card == null ? null : card.getImages();
        return images != null && images.length > 1;
    }

    /** Wide enough for the widest row, whatever the rows happen to be. */
    private int menuWidth()
    {
        int widest = 0;
        for(String label : menuLabels())
        {
            widest = Math.max(widest, font.width(label));
        }
        return widest + MENU_PAD * 2;
    }

    /**
     * As many rows as there are labels.
     * <p>
     * It used to answer 3 or 4 by hand, which meant every entry added to the
     * menu had to be counted here as well -- and a row the box was not sized
     * for is drawn outside it, while {@link #handleMenuClick}'s clamp files its
     * clicks under the last row it does know about.
     */
    private int menuRows()
    {
        return menuLabels().size();
    }

    private int menuHeight()
    {
        return menuRows() * MENU_ROW + MENU_EDGE * 2;
    }

    /**
     * Where a labelled row is, or -1 when the menu is not showing it.
     * <p>
     * Read off the list that is drawn rather than numbered here, so the rows
     * cannot be in one order on screen and another in the click handler.
     */
    private int rowOf(String label)
    {
        return menuLabels().indexOf(label);
    }

    /** Always first, and its label says which way it will go. */
    private int favouriteRow()
    {
        return 0;
    }

    private int infoRow()
    {
        return rowOf("Card Info");
    }

    private int addRow()
    {
        return rowOf("+1");
    }

    /** Only present when the card clicked was one already in the deck. */
    private int removeRow()
    {
        return rowOf("-1");
    }

    /** Only present for a deck copy of a card that has alternate artwork. */
    private int altArtsRow()
    {
        return rowOf(ALT_ARTS);
    }

    private boolean handleMenuClick(double mouseX, double mouseY)
    {
        int rows = menuRows();
        if(mouseX < menuX || mouseX > menuX + menuWidth()
            || mouseY < menuY || mouseY > menuY + menuHeight())
        {
            return false;
        }
        // Clamped rather than bounds-checked: a click in the padding at either
        // end belongs to the row nearest it, not to nothing.
        int row = Math.max(0, Math.min(rows - 1,
            (int)((mouseY - menuY - MENU_EDGE) / MENU_ROW)));
        // Every row number read BEFORE the menu is closed, because they are
        // derived from the labels and the labels are derived from what the menu
        // is on -- which closeMenu forgets.
        int favourite = favouriteRow();
        int info = infoRow();
        int add = addRow();
        int remove = removeRow();
        int altArts = altArtsRow();
        Properties target = menuCard;
        DeckList.Part part = menuPart;
        int index = menuIndex;
        closeMenu();

        if(row == info)
        {
            // The editor is the way back, so closing the page returns to the
            // deck rather than to the world.
            EditorState.flush();
            if(minecraft != null)
            {
                minecraft.setScreen(new CardInfoScreen(this, target));
            }
            return true;
        }
        if(row == favourite)
        {
            EditorState.toggleFavourite((int)target.getId());
            return true;
        }
        if(row == add)
        {
            // Add one, into the part it belongs in.
            DeckList.Part destination = part != null ? part
                : target.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
            add(target, destination);
            return true;
        }
        if(row == altArts && part != null && index >= 0)
        {
            openAltArts(target, part, index);
            return true;
        }
        if(row == remove && part != null && index >= 0)
        {
            // The removal takes the artwork out with the card; the bare list
            // remove this used to do left the arts a place out of step.
            EditorState.removeCard(part, index);
        }
        return true;
    }

    /** Draws the menu, and greys "Add 1" when the rules refuse another copy. */
    private void drawMenu(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        DeckList.Part destination = menuPart != null ? menuPart
            : menuCard.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
        DeckLimits.Verdict verdict = DeckLimits.canAddToDraft(EditorState.deck(), destination,
            (int)menuCard.getId(), EditorState.banlist());

        // Sized to its contents, top and bottom included, so a row added to
        // the menu cannot fall outside the box drawn behind it.
        List<String> labels = menuLabels();
        int menuW = menuWidth();
        NineSlice.draw(poseStack, HubTextures.PANEL, menuX, menuY, menuW, menuHeight());

        for(int i = 0; i < labels.size(); i++)
        {
            int rowY = menuY + MENU_EDGE + i * MENU_ROW;
            boolean over = mouseX >= menuX && mouseX <= menuX + menuW
                && mouseY >= rowY && mouseY < rowY + MENU_ROW;
            String label = labels.get(i);
            int colour;
            if(i == addRow() && !verdict.allowed())
            {
                colour = 0xFF6A7080;
            }
            else if("-1".equals(label))
            {
                colour = over ? 0xFFFFB0A8 : 0xFFE6EAF2;
            }
            else
            {
                colour = over ? 0xFFFFE9B0 : 0xFFE6EAF2;
            }
            poseStack.text(font, label, (int)(menuX + MENU_PAD), (int)(rowY + (MENU_ROW - font.lineHeight) / 2F + 1), colour, true);
        }
    }

    // ---- the artwork picker ----

    /** How much screen is left around the picker's panel. */
    private static final int ALT_MARGIN = 6;
    /** Never so wide that the row becomes a line to read along. */
    private static final int ALT_MAX_WIDTH = 520;
    private static final int ALT_PAD = 10;

    /**
     * How wide one artwork is drawn, in GUI units.
     * <p>
     * <b>Measured against the GUI, not the framebuffer.</b> The client runs at
     * 1634&times;920 and {@code guiScale:0} resolves to 3 (scale 4 would leave
     * {@code 920/4 = 230} units, under the 240 the game insists on), so a
     * screen is {@code ceil(1634/3)} &times; {@code ceil(920/3)} =
     * <b>545&times;307 GUI units</b>. Sizing a tile against 1634 would make it
     * three times too large and fit two artworks on the row.
     * <p>
     * 48 is the largest tile at which the worst case fits in ONE row without
     * scrolling: Dark Magician has nine artworks, and nine of them need
     * {@code 9T + 8*GAP + 2*PAD <= 520}, so {@code T <= (520 - 84) / 9 = 48.4}.
     * At the card's own aspect that is {@code round(48 / (480/700))} = 70 tall,
     * and the panel comes out
     * {@code 9*(48+8) - 8 + 20 = 516} by {@code 20 + 16 + 26 + 78 = 140} --
     * just under half the screen's height, so the dimmed editor is still
     * plainly there around it.
     */
    private static final int ALT_TILE_W = 48;

    /**
     * Space between cells. Wider than the editor's 2 because every tile wears a
     * frame on all four sides, and at a smaller gap the neighbouring frames
     * would touch and the row would read as one box rather than as choices.
     */
    private static final int ALT_GAP = 8;

    /** How much of the chip behind a tile shows around its art. */
    private static final int ALT_FRAME = 3;

    /** Room above the row for the card's name. */
    private static final int ALT_HEADER_H = 16;

    /** Room below it: a 20-tall button row and 6 of air above that. */
    private static final int ALT_FOOTER_H = 26;

    // Worked out by altLayout, and read by both the drawing and the hit test so
    // the tiles you can click are the tiles you can see.
    private int altLeft;
    private int altTop;
    private int altPanelW;
    private int altPanelH;
    private int altTileH;
    private int altColumns;
    private int altGridTop;
    private int altMaxScroll;

    /** How many artworks the card being dressed was printed with. */
    private int altCount()
    {
        return altCard == null ? 0 : altCard.getImages().length;
    }

    /**
     * Sizes the picker to the artworks it has to show.
     * <p>
     * Whole columns and no more of them than there are artworks, so a card with
     * two does not open a nine-wide panel with seven empty cells in it. Run
     * before every draw and every click rather than once on opening: this
     * screen re-derives its whole layout each frame, and a picker that
     * remembered a size from an earlier window would be clicked in one place
     * and drawn in another.
     */
    private void altLayout()
    {
        int arts = Math.max(1, altCount());
        int usable = Math.min(ALT_MAX_WIDTH, width - ALT_MARGIN * 2);
        altTileH = Math.max(8, Math.round(ALT_TILE_W / DuelTextures.CARD_ASPECT));
        int cell = ALT_TILE_W + ALT_GAP;
        altColumns = Math.clamp((usable - ALT_PAD * 2 + ALT_GAP) / cell, 1, arts);
        altPanelW = altColumns * cell - ALT_GAP + ALT_PAD * 2;
        String name = altCard == null || altCard.getName() == null ? "" : altCard.getName();
        // 12 of air between the name and the count so they never touch.
        int headerW = font.width(name) + 12 + font.width(altCount() + " artworks") + ALT_PAD * 2;
        altPanelW = Math.min(usable, Math.max(altPanelW, headerW));
        altPanelH = ALT_PAD * 2 + ALT_HEADER_H + ALT_FOOTER_H + altTileH + ALT_GAP;
        altLeft = (width - altPanelW) / 2;
        altTop = (height - altPanelH) / 2;
        altGridTop = altTop + ALT_PAD + ALT_HEADER_H;
        // Horizontal, because the grid is one row. Only reachable on a window
        // small enough to lose columns -- at 545 units all nine fit.
        altMaxScroll = Math.max(0, arts - altColumns);
        altScroll = Math.clamp(altScroll, 0, altMaxScroll);
    }

    /**
     * Whether the copy the picker was opened on is still that copy.
     * <p>
     * A profile sync replaces the whole deck list with what the server holds,
     * and a deck can come back a card shorter -- at which point (part, index)
     * names somebody else. The card is therefore checked as well as the
     * position, and a picker that has lost its copy closes rather than dressing
     * whatever moved into the slot.
     */
    private boolean altValid()
    {
        if(altCard == null || altPart == null || altIndex < 0)
        {
            return false;
        }
        List<Integer> cards = EditorState.deck().partFor(altPart);
        return altIndex < cards.size() && cards.get(altIndex) == (int)altCard.getId();
    }

    /** Opens the picker on one copy, and locks the editor behind it. */
    private void openAltArts(Properties card, DeckList.Part part, int index)
    {
        altCard = card;
        altPart = part;
        altIndex = index;
        altScroll = 0;
        refusal = "";
        // Rebuilt so the editor's own controls go away and the picker's Back
        // button is the only thing a click can reach.
        rebuildControls();
    }

    private void closeAltArts()
    {
        altCard = null;
        altPart = null;
        altIndex = -1;
        altScroll = 0;
        rebuildControls();
    }

    /** The picker's one widget. */
    private void buildAltArts()
    {
        altLayout();
        addRenderableWidget(new HubWidgets.TextureButton(altLeft + ALT_PAD,
            altTop + altPanelH - ALT_PAD - 20, 60, 20, Component.literal("Back"),
            pressed -> closeAltArts()));
    }

    /**
     * The artwork row, over a dimmed editor.
     * <p>
     * Described BEFORE {@code super.extractRenderState}, so the Back button --
     * a widget, and therefore described by super -- lands on top of the panel
     * rather than under it. Retained mode draws in the order it was told, so
     * the order of these calls is what layering means here.
     */
    private void renderAltArts(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        altLayout();

        // The editor's own dim, drawn a second time over the finished editor:
        // it stays legible underneath and reads as out of reach, which is what
        // it is. NOT extractBackground -- that blurs in 26.2, once per frame,
        // and a second screen asking for the same blur is what crashed the
        // client. Same decision, and the same substitute, as everywhere else.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        NineSlice.draw(poseStack, HubTextures.PANEL, altLeft, altTop, altPanelW, altPanelH);

        String count = altCount() + " artworks";
        String title = font.plainSubstrByWidth(
            altCard.getName() == null ? "" : altCard.getName(),
            altPanelW - ALT_PAD * 2 - font.width(count) - 12);
        poseStack.text(font, title, altLeft + ALT_PAD, altTop + ALT_PAD, 0xFFF4D089, true);

        poseStack.text(font, count, altLeft + altPanelW - ALT_PAD - font.width(count),
            altTop + ALT_PAD, 0xFFC2C9D6, true);

        // One recess behind the whole row rather than a frame per cell, as the
        // deck grids and the sleeve picker both do.
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, altLeft + ALT_PAD - 2, altGridTop - 2,
            altPanelW - ALT_PAD * 2 + 4, altTileH + ALT_GAP + 4);

        int worn = EditorState.artAt(altPart, altIndex);
        for(int column = 0; column < altColumns; column++)
        {
            int index = column + altScroll;
            if(index >= altCount())
            {
                break;
            }
            int x = altLeft + ALT_PAD + column * (ALT_TILE_W + ALT_GAP);
            int y = altGridTop;
            boolean over = mouseX >= x - ALT_FRAME && mouseX < x + ALT_TILE_W + ALT_FRAME
                && mouseY >= y - ALT_FRAME && mouseY < y + altTileH + ALT_FRAME;

            // The chip's three rows are idle, hovered and lit, which is exactly
            // the three things a tile here has to say -- and the lit one marks
            // what this copy is already wearing.
            int row = index == worn ? NineSlice.SELECTED
                : over ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, x - ALT_FRAME, y - ALT_FRAME,
                ALT_TILE_W + ALT_FRAME * 2, altTileH + ALT_FRAME * 2, row, 3);

            // PREVIEW size, not the icon size the grids use. A 48-unit tile at
            // guiScale 3 is 144 real pixels and the card fills only
            // U1 - U0 = 60.2% of the square file, so a texel per pixel wants
            // 144 / 0.602 = 240 across; the 128 icon is visibly soft at that
            // size and the hover preview already caches the 512.
            DdBlitUtil.blit(poseStack,
                DuelTextures.card(altCard, (byte)index, DuelTextures.PREVIEW_CARD_SIZE),
                x, y, ALT_TILE_W, altTileH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
        }

        if(altMaxScroll > 0)
        {
            // Under the row rather than over the last tile, so no artwork is
            // half covered by furniture. Horizontal, because the grid is.
            int trackW = altPanelW - ALT_PAD * 2;
            int barY = altGridTop + altTileH + ALT_GAP - 2;
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, altLeft + ALT_PAD, barY,
                trackW, 4, 0, 2);
            int thumbW = Math.max(12, trackW * altColumns / Math.max(1, altCount()));
            int thumbX = altLeft + ALT_PAD + (trackW - thumbW) * altScroll / altMaxScroll;
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, thumbX, barY, thumbW, 4, 1, 2);
        }

    }

    /**
     * A click while the picker is up. Everything reaches here, because the
     * picker owns the screen while it is open.
     */
    private void clickAltArts(double mouseX, double mouseY, int button)
    {
        altLayout();
        if(button == 0)
        {
            for(int column = 0; column < altColumns; column++)
            {
                int index = column + altScroll;
                if(index >= altCount())
                {
                    break;
                }
                int x = altLeft + ALT_PAD + column * (ALT_TILE_W + ALT_GAP);
                int y = altGridTop;
                if(mouseX >= x - ALT_FRAME && mouseX < x + ALT_TILE_W + ALT_FRAME
                    && mouseY >= y - ALT_FRAME && mouseY < y + altTileH + ALT_FRAME)
                {
                    if(!altValid())
                    {
                        closeAltArts();
                        return;
                    }
                    // The copy is addressed by (part, index), which is what the
                    // right-click recorded -- so three Dark Magicians in one
                    // deck are three different answers to this click.
                    EditorState.setArt(altPart, altIndex, index);
                    // Written out now rather than on the next tick, so a player
                    // who dresses a copy and closes the game immediately still
                    // has it. The autosave would have caught it anyway.
                    EditorState.flush();
                    closeAltArts();
                    return;
                }
            }
        }
        if(mouseX < altLeft || mouseX >= altLeft + altPanelW
            || mouseY < altTop || mouseY >= altTop + altPanelH)
        {
            // Outside the panel is the same answer as Back, which is what a
            // click off any of this screen's other pop-ups already means.
            closeAltArts();
        }
    }

    /**
     * The part a card goes in when nobody said: extra deck monsters to the
     * extra deck, everything else to the main.
     */
    static DeckList.Part homeFor(Properties card)
    {
        return card.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
    }

    /**
     * Whether another copy would be allowed, and why not if it would not.
     * <p>
     * Shared with {@link CardInfoScreen}, which offers the same one-click add
     * and must refuse for exactly the same reasons the editor does — the pool
     * and the banlist are read here rather than passed in so there is one
     * answer to the question and not two that can drift apart.
     */
    static DeckLimits.Verdict roomFor(Properties card)
    {
        return DeckLimits.canAddToDraft(EditorState.deck(), homeFor(card),
            (int)card.getId(), EditorState.banlist());
    }

    /** Adds one copy if {@link #roomFor} allows it, and reports what happened. */
    static DeckLimits.Verdict addOne(Properties card)
    {
        DeckLimits.Verdict verdict = roomFor(card);
        if(verdict.allowed())
        {
            EditorState.addCard(homeFor(card), (int)card.getId());
        }
        return verdict;
    }

    /** How many copies a card may reach at all, for "2 / 3" style counts. */
    static int ceilingFor(Properties card)
    {
        return DeckLimits.maxCopies((int)card.getId(), EditorState.trunk(),
            EditorState.banlist(), true);
    }

    /**
     * Adds a new copy if every rule allows it, dressed in the printing the
     * player owns.
     * <p>
     * The shift-click route. It went in on artwork 0 unconditionally, which is
     * one of the three places a new copy was born blind to the collection.
     */
    private boolean add(Properties card, DeckList.Part part)
    {
        return add(card, part, EditorState.defaultArtFor((int)card.getId()));
    }

    /** Adds a card if every rule allows it, else records why not. */
    private boolean add(Properties card, DeckList.Part part, int art)
    {
        DeckLimits.Verdict verdict = DeckLimits.canAddToDraft(EditorState.deck(), part,
            (int)card.getId(), EditorState.banlist());
        if(!verdict.allowed())
        {
            refusal = verdict.reason();
            return false;
        }
        EditorState.addCard(part, (int)card.getId(), art);
        return true;
    }

    /**
     * Releasing the button drops the carried card where the cursor is, but only
     * if the mouse actually travelled. Without the distance test a plain click
     * would pick a card up and immediately put it back down, so click-to-carry
     * would be impossible.
     */
    @Override
    public boolean mouseReleased(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        if(altCard != null)
        {
            // The picker owns the screen; a release under it must not finish a
            // scrollbar drag or drop a carried card into the editor behind.
            return super.mouseReleased(vanillaX, vanillaY, vanillaButton);
        }
        trunkBarGrab = -1;
        filterBarGrab = -1;
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if(carried != null && pressedOnCard)
        {
            boolean dragged = Math.abs(mouseX - pressX) > DRAG_SLOP
                || Math.abs(mouseY - pressY) > DRAG_SLOP;
            pressedOnCard = false;
            if(dragged)
            {
                DeckList.Part part = partAt(mouseX, mouseY);
                if(part != null)
                {
                    place(part);
                }
                else if(trunkIndexAt(mouseX, mouseY) >= 0)
                {
                    // Dropped back on the trunk: the deck simply loses it, and
                    // the trunk never lost it in the first place.
                    carried = null;
                    carriedFrom = null;
                    carriedIndex = -1;
                }
                else
                {
                    returnCarried();
                }
                return true;
            }
        }
        pressedOnCard = false;
        return super.mouseReleased(vanillaX, vanillaY, vanillaButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        // Nothing is told about the scroll here any more. A wheel event says
        // the list was touched, not how fast it is moving, so it could not tell
        // a flick from a single click and metered both; drawTrunk measures the
        // rows the grid actually travelled instead. See CardImageManager.
        if(altCard != null)
        {
            // Along the artwork row, and nowhere else: the grids underneath are
            // behind a scrim and must not move while they cannot be reached.
            altLayout();
            altScroll = Math.clamp(altScroll - (int)Math.signum(delta), 0, altMaxScroll);
            return true;
        }
        // Shift reads the hovered card's description; the plain wheel scrolls
        // whatever grid is under the cursor.
        //
        // It used to be the other way round, and that was a mistake: a full
        // grid has a card under the cursor almost everywhere, so the wheel
        // reached the description whatever the player meant by it and the
        // collection could not be scrolled at all. Reading a long effect is the
        // rarer thing to want, so it is the one that takes the modifier.
        if(carried == null && com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            && cardAt(mouseX, mouseY) != null)
        {
            previewScroll = Math.max(0, previewScroll - (int)Math.signum(delta));
            return true;
        }
        if(mouseX < rightX && mouseY >= deckViewTop() && mouseY < deckViewTop() + deckViewHeight())
        {
            deckScroll = Math.max(0, Math.min(maxDeckScroll(),
                deckScroll - (int)Math.signum(delta) * (deckCardH + gap)));
            return true;
        }
        if(openList != null)
        {
            int overflow = Math.max(0, openList.values.size() + 1 - listRows());
            openListScroll = Math.max(0, Math.min(overflow,
                openListScroll - (int)Math.signum(delta)));
            return true;
        }
        if(filtersOpen && mouseX >= rightX && mouseY >= filterViewTop()
            && mouseY < filterViewBottom())
        {
            filterScroll = Math.max(0, Math.min(maxFilterScroll(),
                filterScroll - (int)Math.signum(delta) * 12));
            rebuildControls();
            return true;
        }
        if(mouseX >= rightX)
        {
            trunkScroll = Math.max(0, Math.min(maxTrunkScroll(),
                trunkScroll - (int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);
        int key = event.key();
        int scan = event.scancode();
        int modifiers = event.modifiers();
        if(altCard != null)
        {
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            {
                // Escape answers the picker rather than leaving the editor,
                // which is the safe reading of it while a question is up.
                closeAltArts();
                return true;
            }
            // The search box keeps its focus flag across a rebuild, so without
            // this a keystroke would be typed into a field that is not on
            // screen and would re-filter the collection behind the scrim.
            return true;
        }
        if(rename != null && rename.isFocused())
        {
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            {
                commitRename();
                return true;
            }
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            {
                rename = null;
                rebuildControls();
                return true;
            }
            if(rename.keyPressed(event.key(), event.scancode(), event.modifiers()))
            {
                return true;
            }
        }
        if(search != null && search.isFocused() && search.keyPressed(event.key(), event.scancode(), event.modifiers()))
        {
            return true;
        }
        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
    }

    @Override
    public boolean charTyped(char vanillaCodepoint, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.CharacterEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.CharacterEvent(vanillaCodepoint);
        char typed = (char)event.codepoint();
        int modifiers = 0;
        if(altCard != null)
        {
            // As keyPressed: the fields are gone from the screen but not from
            // their own idea of being focused.
            return true;
        }
        if(rename != null && rename.isFocused() && rename.charTyped((char) event.codepoint(), vanillaModifiers))
        {
            return true;
        }
        if(search != null && search.isFocused() && search.charTyped((char) event.codepoint(), vanillaModifiers))
        {
            return true;
        }
        return super.charTyped(vanillaCodepoint, vanillaModifiers);
    }

    // ---- rendering ----

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
        // The deck decides the card size and the deck changes while this screen
        // is open, so the whole layout is worked out now rather than when it
        // opened. Every number below and in rebuildControls comes out of this
        // one pass, so the widgets and the art they sit among cannot disagree.
        remeasure();
        NineSlice.draw(poseStack, HubTextures.PANEL, leftX, panelTop, leftW, panelH);
        NineSlice.draw(poseStack, HubTextures.PANEL, rightX, panelTop, rightW, panelH);

        renderDeckSide(poseStack, mouseX, mouseY);
        renderTrunkSide(poseStack, mouseX, mouseY);

        // The picker's scrim and panel go down over the finished editor and
        // BEFORE the widgets, so its own Back button -- described by super --
        // is the one thing on top of it.
        if(altCard != null)
        {
            renderAltArts(poseStack, mouseX, mouseY);
            super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
            // Everything below this point draws over whatever super drew, so
            // none of it may run while the picker is up: the search field, the
            // filter drawer, the rename box, the refusal line, the hover
            // preview and the right-click menu would each paint straight
            // through the scrim.
            return;
        }

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
        search.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
        if(filtersOpen && levelMin != null)
        {
            for(EditBox box : List.of(levelMin, levelMax, attackMin, attackMax,
                defenceMin, defenceMax))
            {
                if(inFilterView(box.getY() - 2, 16))
                {
                    box.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
                }
            }
        }
        if(filtersOpen)
        {
            renderOpenList(poseStack, mouseX, mouseY);
        }
        if(rename != null)
        {
            rename.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
        }

        if(!refusal.isEmpty())
        {
            // Wrapped to the window and clamped inside it. The import message
            // is about 300 units wide, so at the smallest window the game
            // allows it spanned the whole screen from a negative x -- and it
            // was anchored at a constant height - 52, which is exactly the
            // card-size slider's band.
            int room = Math.max(16, width - 8);
            List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(refusal), room - 16);
            int textWidth = 0;
            for(net.minecraft.util.FormattedCharSequence line : lines)
            {
                textWidth = Math.max(textWidth, font.width(line));
            }
            int bannerW = Math.min(room, textWidth + 16);
            int bannerH = lines.size() * font.lineHeight + 9;
            int x = clamp(4, Math.max(4, width - bannerW - 4), width / 2 - bannerW / 2);
            // Above the measured control rows rather than on top of them.
            int y = Math.max(4, trunkContentBottom() - bannerH - 2);
            NineSlice.draw(poseStack, HubTextures.PANEL, x, y, bannerW, bannerH);
            for(int i = 0; i < lines.size(); i++)
            {
                poseStack.text(font, lines.get(i), x + 8, y + 5 + i * font.lineHeight,
                    0xFFFF8A80, true);
            }
        }

        // A hovered card is previewed large, with its name and effect text.
        // Skipped while carrying, since the cursor already has a card on it.
        if(carried == null && menuCard == null)
        {
            Properties hovered = cardAt(mouseX, mouseY);
            if(hovered != null)
            {
                // Raised above everything already drawn. Font rendering batches
                // into a buffer that flushes at the end of the frame, so text
                // drawn EARLIER can otherwise appear on top of a panel drawn
                // later -- which is why the deck headings were showing through
                // the preview. Vanilla tooltips solve it the same way.
                poseStack.pose().pushMatrix();
        // The Z translate that lifted this above a later panel is gone:
        // retained mode draws in the order described, so ordering the
        // calls is what layering means now.
                drawPreview(poseStack, hovered, mouseX, mouseY, artAt(mouseX, mouseY));
                poseStack.pose().popMatrix();
            }
        }
        if(menuCard != null)
        {
            poseStack.pose().pushMatrix();
        // The Z translate that lifted this above a later panel is gone:
        // retained mode draws in the order described, so ordering the
        // calls is what layering means now.
            drawMenu(poseStack, mouseX, mouseY);
            poseStack.pose().popMatrix();
        }

        // The carried card rides the cursor, as an inventory stack does, in
        // whatever artwork it was picked up wearing.
        if(carried != null)
        {
            drawCard(poseStack, carried, mouseX - deckCardW / 2, mouseY - deckCardH / 2,
                deckCardW, deckCardH, 1F, false, carriedArt, false);
        }
    }

    /**
     * The collection's card size, as a slider.
     * <p>
     * Rebuilds the screen on every change rather than only on release, because
     * the grid reflowing under the cursor IS the feedback -- a size you cannot
     * see until you let go is one you have to guess at.
     */
    private class TrunkSizeSlider extends net.minecraft.client.gui.components.AbstractSliderButton
    {
        TrunkSizeSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(),
                (trunkScale - TRUNK_SCALE_MIN) / (TRUNK_SCALE_MAX - TRUNK_SCALE_MIN));
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal("Card Size  "
                + Math.round((TRUNK_SCALE_MIN
                    + (float)value * (TRUNK_SCALE_MAX - TRUNK_SCALE_MIN)) * 100F) + "%"));
        }

        @Override
        protected void applyValue()
        {
            trunkScale = TRUNK_SCALE_MIN + (float)value * (TRUNK_SCALE_MAX - TRUNK_SCALE_MIN);
            // The row the scroll sits on has changed size, so where it points
            // has to be re-clamped; remeasure() does that for both grids, and
            // against the column count this drag has just changed rather than
            // against the one the size the player has just left produced.
            remeasure();
        }

        /** Silent: a slider that clicks on every step of a drag is noise. */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }

        /**
         * The handle is a card back rather than the vanilla grey block.
         * <p>
         * Drawn OVER the stock widget rather than instead of it: the track, the
         * hover state and the label all still come from vanilla, so this is a
         * change of face and not a reimplementation of a slider.
         * <p>
         * A card back is also the honest icon for the thing being sized -- the
         * handle grows no larger, but what it stands for is unmistakable.
         */
        @Override
        public void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
        {
            // 26.2 describes a widget into a render state; 1.21.1 draws it now. The
            // body below is unchanged -- it is handed the compatibility surface over
            // the real GuiGraphics.
            de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor graphics = new de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor(vanillaGraphics);
            super.renderWidget(graphics.vanilla(), mouseX, mouseY, partialTick);

            // Where vanilla puts its handle, to the pixel: getX() + (int)(value
            // * (width - HANDLE_WIDTH)), read off the bytecode. Truncated, not
            // rounded -- rounding would sit this half a pixel right of the grey
            // handle underneath and let it peek out at half the positions.
            int x = getX() + (int)(value * (getWidth() - 8)); // HANDLE_WIDTH
            // Card-shaped and as tall as the widget, so it reads as a card
            // standing in the track rather than a square sitting on it.
            int cardH = getHeight();
            int cardW = Math.max(8, Math.round(cardH * DuelTextures.CARD_ASPECT));
            int cardX = x + (8 - cardW) / 2;
            DdBlitUtil.blit(graphics,
                de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT
                    .getMainRL(de.cas_ual_ty.dueldimension.clientutil.ClientProxy
                        .activeCardMainImageSize),
                cardX, getY(), cardW, cardH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

            // Vanilla draws its label AFTER the handle, so the card has just
            // buried it. Put it back, exactly as the superclass drew it -- the
            // same glyphs at the same place, which costs a second opaque pass
            // and nothing else.
            renderScrollingString(graphics.vanilla(),
                net.minecraft.client.Minecraft.getInstance().font, 2,
                (active ? 0xFFFFFF : 0xA0A0A0)
                    | net.minecraft.util.Mth.ceil(alpha * 255.0F) << 24);
        }
    }

    /** Space between the buttons on a control row. */
    /**
     * Between the buttons of the deck panel's own row.
     * <p>
     * Four, not six. These five belong together -- they are all things done to
     * the open deck -- and the gap between them was wide enough to read as
     * three separate groups.
     */
    /**
     * How much air a button's label gets on each side.
     * <p>
     * Two, which is as tight as a button can be and still be a button. One
     * number for every button on this screen, so a row cannot end up padded
     * differently from the row above it.
     */
    private static final int TEXT_PAD = 2;

    private static final int BUTTON_GAP = 3;

    /**
     * How wide a button has to be for its label.
     * <p>
     * A floor as well as a measure, so a one-word button is still big enough
     * to aim at rather than shrinking to the width of its text. Forty by the
     * row's twenty is still a comfortable target.
     * <p>
     * Two pixels each side of the text and nothing else -- no floor, because a
     * floor is what made Sort, Import and Export come out identical at 56 when
     * none of them needed it, and that identical over-padding is what read as a
     * row of empty boxes rather than a row of buttons.
     */
    private int buttonWidth(Component label)
    {
        return font.width(label) + TEXT_PAD * 2;
    }

    /**
     * The sleeve and deck-case swatches, which are icons rather than labels.
     * <p>
     * One expression for both the want and the floor below, because they are
     * the same button at any size -- and a floor WIDER than its want silently
     * breaks {@code pack}, which is what happens if these two drift apart.
     */
    private int swatchWidth()
    {
        return SWATCH_ROOM + TEXT_PAD * 2;
    }

    /**
     * Reads a .ydk into a new deck, and says what happened either way.
     * <p>
     * The chooser returning null is two different things -- the player
     * cancelled, or the native dialog is unavailable -- and they are told
     * apart by whether the folder exists to fall back to, so a missing
     * library reads as "put the file here" rather than as nothing happening.
     */
    private void importDeck()
    {
        java.nio.file.Path chosen = DeckFiles.choose();
        if(chosen == null)
        {
            refusal = "No file chooser here; put .ydk files in "
                + DeckFiles.FOLDER.getName() + " and reopen";
            return;
        }
        DeckFiles.Result result = DeckFiles.importInto(chosen);
        refusal = result.message();
        // The new deck is the open one, and it changed size, so both grids and
        // both scroll positions have to be worked out again. rebuildControls
        // re-measures too; this is the one that makes the refusal line below
        // land against the row that is about to be rebuilt.
        remeasure();
        rebuildControls();
    }

    /** Whatever card is under the cursor, in either panel. */
    private Properties cardAt(double mouseX, double mouseY)
    {
        DeckList.Part part = partAt(mouseX, mouseY);
        if(part != null)
        {
            List<Integer> cards = EditorState.deck().partFor(part);
            int index = slotIndexAt(part, mouseX, mouseY);
            return index >= 0 && index < cards.size() ? card(cards.get(index)) : null;
        }
        int trunkIndex = trunkIndexAt(mouseX, mouseY);
        List<Properties> shown = EditorState.visible();
        return trunkIndex >= 0 && trunkIndex < shown.size() ? shown.get(trunkIndex) : null;
    }

    /**
     * The artwork the card under the cursor is wearing, or 0 over the
     * collection -- a collection entry is a card and not a copy, so it has no
     * artwork of its own to show.
     */
    private int artAt(double mouseX, double mouseY)
    {
        DeckList.Part part = partAt(mouseX, mouseY);
        if(part == null)
        {
            return 0;
        }
        DeckList deck = EditorState.deck();
        List<Integer> cards = deck.partFor(part);
        int index = slotIndexAt(part, mouseX, mouseY);
        return index >= 0 && index < cards.size() ? deck.artAt(cards, index) : 0;
    }

    /**
     * The preview panel, built the way the duel screen's sidebar is.
     * <p>
     * Three things matter here and all three were wrong before. The art comes
     * from {@link DuelTextures#card} at {@code PREVIEW_CARD_SIZE} with bilinear
     * filtering, because the mod's MAIN image is sized for item icons and
     * upscaling it is what made the preview blurry. It is sampled out of its
     * letterboxed square, or it stretches. And the card's own header lines --
     * type, race, level, attribute, ATK/DEF -- are printed under the name,
     * because a preview without them cannot answer the question a player is
     * actually asking.
     * <p>
     * The panel is anchored to whichever side of the cursor has room and
     * clamped to the screen, so it can never be the thing that overflows.
     */
    /**
     * Whether the big hover preview may be drawn at all.
     * <p>
     * Behind Shift by default, because the panel covers a third of the
     * collection and follows the cursor, so browsing a grid means it is nearly
     * always over the cards being browsed. It is also the most expensive thing
     * the image pipeline does -- a 512px decode per card hovered -- so not
     * asking for one per mouse-move is a saving as well as a preference.
     * <p>
     * The gate itself is optional; a player who liked it always-on turns
     * {@code hoverPreviewNeedsShift} off in the mod's settings.
     */
    /**
     * A text scale the GUI can render on whole pixels.
     * <p>
     * The font is a bitmap: at a scale that does not land its glyphs on pixel
     * boundaries the sampler blends neighbouring texels and the text goes soft.
     * A HALF-size label is therefore only crisp when the GUI scale itself is
     * even -- at scale 3, 0.5 asks for 1.5 device pixels per GUI pixel and
     * every stroke smears.
     * <p>
     * Snapped to the nearest scale whose product with the GUI scale is a whole
     * number, and never below one device pixel per GUI pixel, so small text
     * gets a little larger rather than blurrier.
     */
    private float crispScale(float wanted)
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        if(gui <= 0D)
        {
            return wanted;
        }
        // The device pixels one GUI pixel of text would occupy, rounded to a
        // whole number and floored at 1 -- 0 would make the text vanish.
        long steps = Math.max(1L, Math.round(wanted * gui));
        return (float)(steps / gui);
    }

    private boolean previewAllowed()
    {
        if(!de.cas_ual_ty.dueldimension.clientutil.HoverPreviewSettings.needsShift())
        {
            return true;
        }
        // isKeyDown takes the Window OBJECT, not its handle -- the same trap
        // PackOpeningScreen's shift-peek hit.
        long window =
            net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private void drawPreview(GuiGraphicsExtractor poseStack, Properties card, int mouseX,
        int mouseY, int art)
    {
        Layout layout = Layout.of(LAYOUT);
        int inner = 5;
        // Bounded by the window before anything is measured against it, so a
        // small window narrows the panel rather than hanging it off the side.
        int panelW = Math.min(Math.max(32, width - 8),
            Math.max(layout.i("preview.width", 78), layout.i("preview.textWidth", 132))
                + inner * 2);
        // The ART is what Shift reveals; the name, facts and effect text are
        // always shown. Hidden art contributes no height, so the panel closes
        // up around the text rather than leaving a hole where a card was.
        boolean showArt = previewAllowed();
        int artW = Math.min(layout.i("preview.width", 78), panelW - inner * 2);
        int artH = showArt
            ? Math.round(artW / layout.f("card.aspect", DuelTextures.CARD_ASPECT))
            : 0;
        int textW = panelW - inner * 2;

        // Text is drawn at half size, so it is wrapped to twice the width and
        // its line height is halved to match.
        float scale = crispScale(layout.f("preview.textScale", 0.5F));
        int wrapW = Math.round(textW / scale);
        int lineH = Math.max(1, Math.round(9 * scale));

        if(previewCard != card.getId())
        {
            // A different card starts at the top of its own description.
            previewCard = card.getId();
            previewScroll = 0;
        }

        // The card's facts, not its tooltip header: the header starts with the
        // name (drawn separately below) and leaves a monster's species out.
        java.util.List<Component> header = new java.util.ArrayList<>();
        CardPresentation.addFacts(card, header);
        java.util.List<net.minecraft.util.FormattedCharSequence> headerLines =
            new java.util.ArrayList<>();
        for(Component component : header)
        {
            headerLines.addAll(font.split(component, wrapW));
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> nameLines =
            font.split(Component.literal(card.getName() == null ? "" : card.getName()), wrapW);
        java.util.List<net.minecraft.util.FormattedCharSequence> textLines =
            font.split(Component.literal(card.getText() == null ? "" : card.getText()), wrapW);

        // Everything the panel must show whatever happens, so the description
        // is given what is LEFT rather than a fixed count of lines. Only the
        // position used to be clamped, so once the content passed height - 8 the
        // panel simply ran off the bottom of the screen.
        int fixed = inner * 2 + artH + (showArt ? 4 : 0) + nameLines.size() * lineH + 2
            + headerLines.size() * lineH + 3;
        int maxLines = clamp(1, layout.i("preview.maxLines", 10),
            (height - 8 - fixed - lineH - 2) / Math.max(1, lineH));
        int maxScroll = Math.max(0, textLines.size() - maxLines);
        previewScroll = Math.min(previewScroll, maxScroll);
        int shownLines = Math.max(0, Math.min(textLines.size() - previewScroll, maxLines));

        int panelH = fixed + shownLines * lineH + (maxScroll > 0 ? lineH + 2 : 0);

        int x = mouseX + 14;
        if(x + panelW > width - 4)
        {
            x = mouseX - 14 - panelW;
        }
        x = Math.max(4, Math.min(x, width - panelW - 4));
        int y = Math.max(4, Math.min(mouseY - panelH / 2, height - panelH - 4));

        // Half transparent, so the board behind stays readable while pointing.
        // The alpha argument was dropped in the port, so this panel has been
        // fully opaque; Forge passed layout's preview.opacity here.
        NineSlice.draw(poseStack, HubTextures.PANEL, x, y, panelW, panelH,
            NineSlice.IDLE, 1, layout.f("preview.opacity", 0.65F));

        // The hovered COPY's artwork, so pointing at a dressed card shows what
        // that card looks like rather than what its first printing did.
        if(showArt)
        {
            // Only asked for when it is drawn: a 512px decode per card hovered
            // is the most expensive thing the image pipeline does, and asking
            // for one the panel is not going to show is the worst version of it.
            DdBlitUtil.blit(poseStack,
                DuelTextures.card(card, (byte)Math.clamp(art, 0, Byte.MAX_VALUE),
                    DuelTextures.PREVIEW_CARD_SIZE),
                x + (panelW - artW) / 2, y + inner, artW, artH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
        }

        // Everything below is half size. Coordinates are divided by the scale
        // so the text still lands where the panel arithmetic put it.
        poseStack.pose().pushMatrix();
        poseStack.pose().scale(scale, scale);
        float sx = (x + inner) / scale;
        float sy = (y + inner + artH + (showArt ? 4 : 0)) / scale;
        float step = 9F;

        for(net.minecraft.util.FormattedCharSequence line : nameLines)
        {
            poseStack.text(font, line, (int)(sx), (int)(sy), 0xFFF4D089, true);
            sy += step;
        }
        sy += 2F / scale;
        for(net.minecraft.util.FormattedCharSequence line : headerLines)
        {
            poseStack.text(font, line, (int)(sx), (int)(sy), 0xFF9FD4FF, false);
            sy += step;
        }
        sy += 3F / scale;
        for(int i = 0; i < shownLines; i++)
        {
            poseStack.text(font, textLines.get(previewScroll + i), (int)(sx), (int)(sy), 0xFFC2C9D6, false);
            sy += step;
        }
        if(maxScroll > 0)
        {
            // Names the key, because the plain wheel scrolls the grid and a
            // binding nobody is told about is a binding nobody uses.
            poseStack.text(font, "shift+scroll  " + (previewScroll + shownLines)
                + "/" + textLines.size(), (int)(sx), (int)(sy + 2F / scale), 0xFF7A8090, true);
        }
        poseStack.pose().popMatrix();
    }

    private void renderDeckSide(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        DeckList deck = EditorState.deck();

        // The deck's name is not drawn here.
        //
        // It sat in a ribbon across the top of the panel, and the ribbon was
        // taken out: it read as a strip laid on the panel rather than the head
        // of it, and its titleHeight was fourteen units the three sections
        // could have had. Where the name goes instead is still open -- see
        // deck.titleHeight, which is 0 and is what reserves the space for it.
        //
        // NOTE: startRename still places its EditBox at panelTop + pad, which
        // is now inside the Main Deck's own strip. Renaming from this screen
        // needs somewhere to live along with the name.

        // The sections scroll, so they are clipped to their own strip. Without
        // this the top row would be drawn over the deck's name and the bottom
        // one over the buttons.
        clipToDeckView(poseStack, true);
        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            List<Integer> cards = deck.partFor(part);
            String heading = switch(part)
            {
                case MAIN -> "Main Deck";
                case EXTRA -> "Extra Deck";
                case SIDE -> "Side Deck";
            };
            // The count doubles as the legality hint: a main deck under 40 is
            // not playable, so the number is the thing to watch.
            boolean ok = part != DeckList.Part.MAIN
                || (cards.size() >= 40 && cards.size() <= 60);
            poseStack.text(font, heading + "  " + cards.size() + " / " + part.capacity(),
                leftX + pad, top - headerH + 3, ok ? 0xFFC2C9D6 : 0xFFFF8A80, true);

            int columns = partColumns(part);
            int cellW = deckCardW + gap;
            // Each part is drawn with the rows it actually needs. Extra and
            // Side used to be given one row whatever they held, so a filled
            // extra deck drew its second row outside its own container and
            // over the section below it.
            int rows = rowsFor(part);
            // One rectangle for the whole area rather than a frame per card:
            // a grid of empty slots is a lot of visual noise for something the
            // cards themselves already make obvious.
            // Two units of margin above the first row and two below the last.
            // Counting a trailing gap put four under it and two over it, which
            // is the same slip as the columns but vertical.
            NineSlice.draw(poseStack, HubTextures.PANEL_INSET, leftX + pad - 2, top - 2,
                leftW - pad * 2 + 4, rows * deckCardH + (rows - 1) * gap + 4);
            for(int row = 0; row < rows; row++)
            {
                for(int column = 0; column < columns; column++)
                {
                    int index = row * columns + column;
                    if(index >= cards.size())
                    {
                        continue;
                    }
                    Properties card = card(cards.get(index));
                    if(card != null)
                    {
                        boolean missing = !EditorState.freeMode()
                            && ordinalInDeck(deck, part, index, cards.get(index))
                                > EditorState.trunk().countOf(cards.get(index));
                        // The artwork is read per POSITION, so two copies of
                        // the same card in the same grid can and do differ.
                        drawCard(poseStack, card, deckGridLeft() + column * cellW,
                            top + row * (deckCardH + gap), deckCardW, deckCardH, 1F, missing,
                            deck.artAt(cards, index), true);
                    }
                }
            }
        }
        clipToDeckView(poseStack, false);

        // A scroll bar only when there is something to scroll, so a deck that
        // fits shows no furniture it does not need.
        // Measured in pixels rather than rows, because the deck's three
        // sections are different heights and a row is not a fixed unit here.
        // Inside the panel's own padding, in the lane the grid's column count
        // leaves for it. It used to be drawn one unit OUTSIDE that padding, on
        // the border art, at every window size.
        pixelScrollbar(poseStack, leftX + leftW - pad - 2, deckViewTop(), deckViewHeight(),
            deckContentHeight(), deckScroll);
    }

    /** One-based occurrence of a card across main, extra, then side deck. */
    private static int ordinalInDeck(DeckList deck, DeckList.Part targetPart,
        int targetIndex, int passcode)
    {
        int ordinal = 0;
        for(DeckList.Part part : DeckList.Part.values())
        {
            List<Integer> cards = deck.partFor(part);
            int end = part == targetPart ? Math.min(targetIndex + 1, cards.size()) : cards.size();
            for(int index = 0; index < end; index++)
            {
                if(cards.get(index) == passcode)
                {
                    ordinal++;
                }
            }
            if(part == targetPart)
            {
                return ordinal;
            }
        }
        return ordinal;
    }

    /**
     * Limits drawing to the deck's scrolling strip, or lifts that limit.
     * <p>
     * In GUI coordinates, corner to corner. Forge's RenderSystem.enableScissor
     * wanted real framebuffer pixels measured from the BOTTOM of the window,
     * and this method used to do that conversion -- but the extractor's
     * enableScissor builds a ScreenRectangle, which lives in the same
     * top-origin GUI space as everything else here, and takes the two corners
     * rather than an origin and a size. The old converted rectangle landed in
     * the bottom-left of the panel, which clipped the whole deck grid down to
     * the few rows that happened to fall inside it.
     */
    private void clipToDeckView(GuiGraphicsExtractor poseStack, boolean on)
    {
        if(!on)
        {
            poseStack.disableScissor();
            return;
        }
        poseStack.enableScissor(leftX, deckViewTop(),
            leftX + leftW, deckViewTop() + deckViewHeight());
    }

    /**
     * The drawer's backing and its labels. The chips are widgets and draw
     * themselves; what is left is the panel behind them, the row headings and
     * the captions on the three bands.
     */
    private void renderFilterDrawer(GuiGraphicsExtractor poseStack)
    {
        int top = filterViewTop();
        int bottom = filterViewBottom();
        // Its own container: a raised panel over the trunk's, so the drawer
        // reads as a thing laid on top of the collection rather than a recess
        // cut into it. The recessed backing behind the frames keeps the sunken
        // look where the chips actually sit.
        NineSlice.draw(poseStack, HubTextures.PANEL, rightX + pad - 4, top - 2,
            rightW - pad * 2 + 8, Math.max(24, bottom - top + 4));
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, rightX + pad - 2, top,
            rightW - pad * 2 + 4, Math.max(20, bottom - top));

        // The chips are widgets and have already drawn themselves anywhere on
        // screen; the frames are clipped so a category scrolled past the top
        // does not paint over the chip row above the drawer.
        clipToFilterView(poseStack, true);
        for(Section section : filterSections)
        {
            NineSlice.draw(poseStack, HubTextures.PANEL, section.x(), section.y(),
                section.width(), section.height());
            poseStack.text(font, section.heading(), (int)(section.x() + 5), (int)(section.y() + 2), 0xFFF4D089, true);
        }

        // The bands read as "Level  [min] [max]", so the captions sit against
        // the fields rather than in the heading column with the chip rows.
        if(levelMin != null)
        {
            band(poseStack, "Level", levelMin, levelMax);
            band(poseStack, "ATK", attackMin, attackMax);
            band(poseStack, "DEF", defenceMin, defenceMax);
        }
        clipToFilterView(poseStack, false);

        // Its own bar, recorded from the draw so it can be caught. The
        // collection's bar is drawn at exactly this x, and while the drawer is
        // up it is not drawn at all -- so dragging what looks like the drawer's
        // scrollbar used to scrub the hidden collection behind it.
        filterBarX = rightX + rightW - pad - 2;
        filterBarY = top + 2;
        filterBarH = Math.max(0, bottom - top - 4);
        filterBarThumbH = pixelScrollbar(poseStack, filterBarX, filterBarY, filterBarH,
            filterContentHeight, filterScroll);
        if(filterBarThumbH <= 0)
        {
            filterBarH = 0;
        }
    }

    /**
     * Takes hold of the filter drawer's scrollbar.
     *
     * @return whether the bar took this click
     */
    private boolean grabFilterBar(double mouseX, double mouseY)
    {
        if(filterBarH <= 0 || maxFilterScroll() <= 0
            || mouseX < filterBarX - BAR_GRAB || mouseX >= filterBarX + 4 + BAR_GRAB
            || mouseY < filterBarY || mouseY >= filterBarY + filterBarH)
        {
            return false;
        }
        int travel = Math.max(1, filterBarH - filterBarThumbH);
        int thumbY = filterBarY + travel * Math.min(filterScroll, maxFilterScroll())
            / Math.max(1, maxFilterScroll());
        filterBarGrab = mouseY >= thumbY && mouseY < thumbY + filterBarThumbH
            ? (int)(mouseY - thumbY) : filterBarThumbH / 2;
        dragFilterBar(mouseY);
        return true;
    }

    /** Scrubs the drawer to wherever its thumb has been dragged. */
    private void dragFilterBar(double mouseY)
    {
        int travel = filterBarH - filterBarThumbH;
        int max = maxFilterScroll();
        if(travel <= 0 || max <= 0)
        {
            filterScroll = 0;
        }
        else
        {
            double top = mouseY - filterBarGrab - filterBarY;
            filterScroll = (int)Math.clamp(Math.round(top / travel * max), 0, max);
        }
        // The drawer's rows are widgets placed at build time, so moving the
        // offset means building them again -- the same route the wheel takes.
        rebuildControls();
    }

    /**
     * Asks for the art of the one row above and the one row below.
     * <p>
     * <b>This replaced a warmer that asked for three pages of icons plus a whole
     * page of 512px previews, and that warmer is why a collection took seconds
     * to fill on every launch.</b> EDOPro's entire prefetch is one row either
     * side ({@code drawing.cpp}:1385-1387, "loads the thumb of one card before
     * and one after to make the scroll smoother") against a database of 13,864
     * cards, and it never has more than about eleven thumbs in flight.
     * <p>
     * EDOPro expresses it as a draw: its search list loops from row -1 to 9
     * against a clip rect, so the off-screen row is requested through the
     * ordinary draw path and it is <em>impossible</em> to warm something the
     * screen would not draw. Our trunk grid has no scissor around it, so an
     * extra row would paint outside the panel. Asking for the same two rows
     * through the same accessor at the same size is the closest thing that does
     * not change what is on screen: same cards, same call, no draw.
     * <p>
     * It goes through {@link CardImageManager} and not {@link ImageHandler}, and
     * that is the difference from the old warmer. The manager records nothing as
     * resident until a texture actually exists, so a warmed card that is never
     * drawn costs a decode and no bookkeeping. The old one recorded residency at
     * request time, which held the first-sighting gate open for exactly the
     * cards the wheel was about to reach.
     * <p>
     * Gated by the same flag the grid uses: a list flying past is not worth a
     * decode for the rows on screen, so it is certainly not worth one for the
     * rows that are not.
     */
    private void prefetchRow(List<Properties> shown, int visibleRows, boolean loadImages)
    {
        if(!loadImages)
        {
            return;
        }
        int first = Math.max(0, (trunkScroll - 1) * trunkColumns);
        int last = Math.min(shown.size(), (trunkScroll + visibleRows + 1) * trunkColumns);

        for(int i = first; i < last; i++)
        {
            Properties card = shown.get(i);
            if(card != null)
            {
                // The artwork the grid will actually draw, not artwork 0. The
                // old warmer asked for 0 regardless, so for every card whose
                // player owns an alternate printing it warmed a texture the
                // grid would never blit -- the exact failure EDOPro's
                // prefetch-by-clipped-draw cannot have.
                DuelTextures.card(card,
                    (byte)Math.clamp(EditorState.bestArtOwned(card), 0, Byte.MAX_VALUE),
                    DuelTextures.ICON_CARD_SIZE);
            }
        }
    }

    /**
     * Milliseconds since the previous frame, which is what the scroll-velocity
     * gate is stated against.
     * <p>
     * {@code DeltaTracker.getRealtimeDeltaTicks} is wall clock divided by the
     * timer's {@code msPerTick}, and {@code Minecraft} builds its timer with
     * 20 ticks a second, so the tick is 50 ms; multiplying back recovers
     * EDOPro's {@code delta_time} ({@code game.cpp}:2044) in its own units.
     * Vanilla clamps the result to half a tick once a frame passes 350 ms, so
     * after a real stall this reads low and the gate errs towards showing a
     * placeholder — which is the safe direction.
     */
    private static float deltaMillis()
    {
        return net.minecraft.client.Minecraft.getInstance().getTimer()
            .getRealtimeDeltaTicks() * 50F;
    }

    /**
     * A scrollbar, drawn only when there is something to scroll.
     * <p>
     * The thumb's length reports how much of the list is on screen and its
     * position reports where in the list that is, so the bar answers "how much
     * more is there" as well as "where am I".
     */
    private void scrollbar(GuiGraphicsExtractor poseStack, int x, int y, int height,
        int total, int visible, int offset)
    {
        int overflow = Math.max(0, total - visible);
        if(overflow <= 0 || height <= 0)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        // The 12 is a minimum, so it has to be capped by the track: a track
        // shorter than the minimum thumb sends thumbY negative and the thumb is
        // drawn above the bar it belongs to. DuelHubScreen already carries this
        // cap; these two copies did not get it.
        int thumbH = Math.min(height, Math.max(12, height * visible / Math.max(1, total)));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);

        // Remember what was drawn, so the click test and the picture are the
        // same numbers rather than two independent calculations.
        trunkBarX = x;
        trunkBarY = y;
        trunkBarH = height;
        trunkBarThumbH = thumbH;
        trunkBarRows = total;
        trunkBarVisibleRows = visible;
    }

    /**
     * Takes hold of the collection's scrollbar.
     *
     * @return whether the bar took this click
     */
    private boolean grabTrunkBar(double mouseX, double mouseY)
    {
        if(trunkBarH <= 0 || maxTrunkScroll() <= 0
            || mouseX < trunkBarX - BAR_GRAB || mouseX >= trunkBarX + 4 + BAR_GRAB
            || mouseY < trunkBarY || mouseY >= trunkBarY + trunkBarH)
        {
            return false;
        }
        int overflow = Math.max(1, trunkBarRows - trunkBarVisibleRows);
        int thumbY = trunkBarY
            + (trunkBarH - trunkBarThumbH) * Math.min(trunkScroll, overflow) / overflow;
        // Grab the thumb where it was taken hold of; clicking bare track puts
        // the thumb's middle under the cursor, as every other bar does.
        trunkBarGrab = mouseY >= thumbY && mouseY < thumbY + trunkBarThumbH
            ? (int)(mouseY - thumbY) : trunkBarThumbH / 2;
        dragTrunkBar(mouseY);
        return true;
    }

    /** Scrubs the collection to wherever the thumb has been dragged. */
    private void dragTrunkBar(double mouseY)
    {
        int travel = trunkBarH - trunkBarThumbH;
        int max = maxTrunkScroll();
        if(travel <= 0 || max <= 0)
        {
            trunkScroll = 0;
            return;
        }
        double top = mouseY - trunkBarGrab - trunkBarY;
        trunkScroll = (int)Math.clamp(Math.round(top / travel * max), 0, max);
    }

    @Override
    public boolean mouseDragged(double vanillaX, double vanillaY, int vanillaButton, double dragX, double dragY)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        if(altCard != null)
        {
            // A drag begun before the picker opened must not keep scrubbing the
            // collection through it.
            return true;
        }
        if(trunkBarGrab >= 0)
        {
            dragTrunkBar(event.y());
            return true;
        }
        if(filterBarGrab >= 0)
        {
            dragFilterBar(event.y());
            return true;
        }
        return super.mouseDragged(vanillaX, vanillaY, vanillaButton, dragX, dragY);
    }

    /**
     * A scrollbar over a run of pixels rather than a count of rows.
     *
     * @return how tall the thumb came out, or 0 when there was nothing to draw,
     *         so a caller that also has to CATCH this bar records what was
     *         drawn instead of working it out a second time
     */
    private int pixelScrollbar(GuiGraphicsExtractor poseStack, int x, int y, int height,
        int content, int offset)
    {
        int overflow = Math.max(0, content - height);
        if(overflow <= 0 || height <= 0)
        {
            return 0;
        }
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        // Capped by the track, as in scrollbar() above: renderOpenList passes a
        // height of one row for a one-entry list, which is shorter than the
        // minimum thumb.
        int thumbH = Math.min(height, Math.max(12, height * height / Math.max(1, content)));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
        return thumbH;
    }

    /** As {@link #clipToDeckView}, for the filter drawer's strip. */
    private void clipToFilterView(GuiGraphicsExtractor poseStack, boolean on)
    {
        if(!on)
        {
            poseStack.disableScissor();
            return;
        }
        // GUI-space corners; see clipToDeckView for why the framebuffer
        // conversion this used to do put the rectangle in the wrong place.
        poseStack.enableScissor(rightX, filterViewTop(),
            rightX + rightW, filterViewBottom());
    }

    private void band(GuiGraphicsExtractor poseStack, String caption, EditBox min, EditBox max)
    {
        int y = min.getY() - 2;
        if(!inFilterView(y, 16))
        {
            return;
        }
        int boxW = min.getWidth() + 4;
        poseStack.text(font, caption, (int)(min.getX() - 2 - font.width(caption) - 4), (int)(y + 4), 0xFFC2C9D6, true);
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, min.getX() - 2, y, boxW, 16);
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, max.getX() - 2, y, boxW, 16);
        poseStack.text(font, "-", (int)(min.getX() - 2 + boxW + 1), (int)(y + 4), 0xFF7A8090, true);
    }

    private void renderTrunkSide(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        Layout layout = Layout.of(LAYOUT);
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, rightX + pad, panelTop + pad,
            search.getWidth() + 6, layout.i("trunk.searchHeight", 16) + 2);

        if(filtersOpen)
        {
            // The collection's bar is not drawn, so it must not be catchable
            // either: its remembered track otherwise still answers clicks that
            // land on the drawer's bar, which shares its x.
            trunkBarH = 0;
            renderFilterDrawer(poseStack);
            poseStack.text(font, EditorState.visible().size() + " cards", (int)(rightX + pad), (int)trunkCountY(), 0xFF7A8090, true);
            return;
        }

        List<Properties> shown = EditorState.visible();
        int gridTop = trunkGridTop();
        int cellW = trunkCardW + gap;
        int visibleRows = trunkVisibleRows();
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, rightX + pad - 2, gridTop - 2,
            rightW - pad * 2 + 4, visibleRows * (trunkCardH + gap) + 4);

        // drawing.cpp:1375-1378, in rows per frame. Ten rows per 16.6 ms, stated
        // against elapsed time so it means the same at 60 fps and at 240 -- and
        // a whole page is nine rows, so this only fires when more than the
        // entire visible grid turns over inside one frame. Art flying past
        // faster than the eye reads it is not worth a decode.
        //
        // This is what replaced a timestamp of the last wheel event. A
        // timestamp cannot tell a flick from a single click, so it metered the
        // click too -- which is what made a collection take seconds to fill.
        int prevRow = lastTrunkScroll;
        lastTrunkScroll = trunkScroll;
        boolean loadImages = CardImageManager.drawThumb(prevRow, trunkScroll, deltaMillis());

        for(int row = 0; row < visibleRows; row++)
        {
            for(int column = 0; column < trunkColumns; column++)
            {
                int index = (row + trunkScroll) * trunkColumns + column;
                int x = rightX + pad + column * cellW;
                int y = gridTop + row * (trunkCardH + gap);
                if(index >= shown.size())
                {
                    continue;
                }
                Properties card = shown.get(index);
                int inDeck = EditorState.deck().copiesOf((int)card.getId());
                int max = DeckLimits.maxCopies((int)card.getId(), EditorState.trunk(),
                    EditorState.banlist(), true);
                // A card already at its limit is dimmed, so the trunk shows
                // what is still available at a glance rather than on refusal.
                boolean unowned = !EditorState.owns((int)card.getId());
                drawCard(poseStack, card, x, y,
                    !unowned && inDeck >= max ? 0.35F : 1F, loadImages);
                // How many the player OWNS, not how many are in the deck.
                // The deck count was the smaller question: a collection screen
                // is asked "how many of these do I have", and the deck's own
                // copies are visible in the deck list beside it.
                int owned = EditorState.trunk().countOf((int)card.getId());
                if(owned > 0)
                {
                    String count = Integer.toString(owned);
                    // Half size: at full size this reads as a label on the card
                    // rather than a mark on it, and covers the art it is
                    // annotating. Scaled around the bottom-right corner so it
                    // stays tucked there whatever the card size is.
                    float scale = crispScale(layout.f("trunk.countScale", 0.5F));
                    poseStack.pose().pushMatrix();
                    poseStack.pose().translate(x + trunkCardW - font.width(count) * scale - 1,
                        y + trunkCardH - font.lineHeight * scale - 1);
                    poseStack.pose().scale(scale, scale);
                    // Reddened once every copy owned is already in the deck,
                    // which is the moment the number stops meaning "spare".
                    outlined(poseStack, count,
                        inDeck >= Math.min(owned, max) ? 0xFFFF8A80 : 0xFFF4D089);
                    poseStack.pose().popMatrix();
                }
            }
        }

        prefetchRow(shown, visibleRows, loadImages);

        int rows = (shown.size() + trunkColumns - 1) / trunkColumns;
        scrollbar(poseStack, rightX + rightW - pad - 2, gridTop,
            visibleRows * (trunkCardH + gap), rows, visibleRows, trunkScroll);

        poseStack.text(font, shown.size() + " cards", (int)(rightX + pad), (int)trunkCountY(), 0xFF7A8090, true);
    }

    /**
     * Draws text ringed in black.
     * <p>
     * A drop shadow is only below and to the right, which is not enough over
     * card art: the count sits on whatever colour the artwork happens to be
     * there, and against a light one the unshadowed edges disappear. A full
     * ring reads on anything.
     */
    private void outlined(GuiGraphicsExtractor poseStack, String text, int colour)
    {
        for(int dx = -1; dx <= 1; dx++)
        {
            for(int dy = -1; dy <= 1; dy++)
            {
                if(dx != 0 || dy != 0)
                {
                    poseStack.text(font, text, (int)(dx), (int)(dy), 0xFF000000, false);
                }
            }
        }
        poseStack.text(font, text, (int)(0), (int)(0), colour, false);
    }

    private static Properties card(int code)
    {
        return de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
    }

    /**
     * A tile in the collection panel.
     * <p>
     * Drawn in the best artwork the player owns rather than always in the
     * printed one. A tile stands for the CARD and not for any one copy, so it
     * shows the finest printing in the collection and goes on showing it while
     * a deck is built -- which is why it asks for copy 0 rather than for the
     * art the next copy added would wear.
     */
    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y,
        float alpha, boolean loadImage)
    {
        drawCard(poseStack, card, x, y, trunkCardW, trunkCardH, alpha,
            EditorState.showUnowned() && !EditorState.owns((int)card.getId()),
            EditorState.bestArtOwned(card), false, loadImage);
    }

    /**
     * Draws a card at its true proportions.
     * <p>
     * The mod stores card art letterboxed inside a SQUARE image, with the card
     * occupying u 0.199..0.801 and v 0.0625..0.9375 (see DuelTextures). Blitting
     * the whole square into a card-shaped rect therefore squeezes the art
     * horizontally, which is the stretch that was visible on every card in both
     * panels. Sampling the letterbox window instead keeps it true.
     */
    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y,
        int w, int h, float alpha)
    {
        drawCard(poseStack, card, x, y, w, h, alpha, false, 0, false, true);
    }

    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y,
        int w, int h, float alpha, boolean halfSaturation)
    {
        drawCard(poseStack, card, x, y, w, h, alpha, halfSaturation, 0, false, true);
    }

    /**
     * The deck grids and the drag ghost, which are never scrolling fast enough
     * to be worth gating: sixty cards at most, and they do not move under the
     * wheel the way the collection does.
     */
    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y,
        int w, int h, float alpha, boolean halfSaturation, int art, boolean altMarker)
    {
        drawCard(poseStack, card, x, y, w, h, alpha, halfSaturation, art, altMarker, true);
    }

    /**
     * Draws one card, in one artwork.
     *
     * @param art       which artwork this copy wears; 0 is the printed one, and
     *                  an index the card does not have folds back to 0 in
     *                  {@code Properties.adjustImageIndex} rather than throwing
     * @param altMarker whether to mark a card that has more than one artwork.
     *                  Only the deck grids do: the mark means "this copy can be
     *                  re-dressed", and a collection entry is a card rather
     *                  than a copy, so there is nothing there to dress
     * @param loadImage whether this card's art may be ASKED for. False while the
     *                  collection is flicking past faster than the eye reads it,
     *                  and it is the request that is suppressed, not the draw:
     *                  see {@link CardImageManager#drawThumb}. Only the grids
     *                  pass anything but true -- every other card here is
     *                  stationary
     */
    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y,
        int w, int h, float alpha, boolean halfSaturation, int art, boolean altMarker,
        boolean loadImage)
    {
        // Fetched at twice the size it is drawn at, and filtered on the way
        // down, so the art is legible rather than a 64-pixel image stretched
        // across a 40-pixel icon.
        // The texture is an argument now rather than a separate bind, and the
        // window is given as its two corners rather than an offset and a size.
        byte imageIndex = (byte)Math.clamp(art, 0, Byte.MAX_VALUE);
        // drawing.cpp:1191. When the gate is shut the placeholder is drawn
        // WITHOUT going through DuelTextures.card at all -- no map entry, no
        // queue entry, no decode. Substituting after the call would be no gate
        // at all: the request is the cost.
        ResourceLocation face = loadImage
            ? DuelTextures.card(card, imageIndex, DuelTextures.ICON_CARD_SIZE)
            : DuelTextures.UNKNOWN;
        // One image whether the card is owned or not; halfSaturation picks the
        // pipeline that greys it rather than a second, desaturated copy of the
        // file. See UnownedPipelines.
        DdBlitUtil.blit(poseStack, face, x, y, w, h,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1,
            DdBlitUtil.tint(1F, 1F, 1F, alpha), halfSaturation);

        // A fraction of the card rather than a fixed size, so both marks stay
        // in proportion however small the icons get.
        int mark = Math.max(6, w / 3);
        boolean alternates = altMarker && hasAltArt(card);
        if(alternates)
        {
            DdBlitUtil.blit(poseStack, HubTextures.ALT_ART, x + 1, y + 1, mark, mark,
                0F, 0F, 1F, 1F, DdBlitUtil.NO_TINT);
        }
        if(EditorState.isFavourite((int)card.getId()))
        {
            // Top RIGHT, always. The top left belongs to the [A]. The bottom
            // right is the collection's copy count, which is text rather than a
            // badge and sits clear of this.
            DdBlitUtil.blit(poseStack, HubTextures.STAR, x + w - mark - 1, y + 1, mark, mark,
                0F, 0F, 1F, 1F, DdBlitUtil.NO_TINT);
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        // An edit reaches the server within a tick of being made, so closing
        // the game rather than the screen still keeps the deck.
        EditorState.flush();
        // Checked here rather than mid-draw, because closing rebuilds the
        // widgets and a frame is no place to do that.
        if(altCard != null && !altValid())
        {
            closeAltArts();
        }
    }

    /**
     * Writes the deck out and leaves.
     * <p>
     * The save is unconditional rather than left to the tick that normally
     * notices edits: that tick only sends when the contents differ from what
     * the server last acknowledged, which is right for an autosave and wrong
     * for a button whose label promises a save. An edit made and undone in the
     * same visit still ends with the server holding this deck.
     */
    private void saveAndExit()
    {
        EditorState.save();
        if(minecraft != null)
        {
            minecraft.setScreen(parent);
        }
    }

    /**
     * Escape saves too. The editor has autosaved all along, so losing the work
     * on the way out would be a new behaviour and a worse one -- the button
     * makes the saving visible, it does not make it optional.
     */
    @Override
    public void onClose()
    {
        EditorState.flush();
        if(minecraft != null)
        {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /**
     * A filter selector: a caption, the chosen value, and a list that opens
     * only when it is asked for.
     * <p>
     * The list is drawn and clicked by the screen rather than by the widget, so
     * it can sit above everything else and be dismissed by a click anywhere --
     * a widget can only paint inside its own rectangle, and a list that
     * appeared underneath the chips below it would be unusable.
     */
    private class DropdownButton extends HubWidgets.TextureButton
    {
        private final String caption;
        private final String chosen;
        private final List<Option> values;
        private final java.util.function.Consumer<String> choose;

        DropdownButton(int x, int y, int width, int height, String caption, String chosen,
            List<Option> values, java.util.function.Consumer<String> choose)
        {
            super(x, y, width, height, Component.literal(caption), pressed ->
            {
            });
            this.caption = caption;
            this.chosen = chosen;
            this.values = values;
            this.choose = choose;
        }

        @Override
        public void onPress()
        {
            openList = openList == this ? null : this;
            openListScroll = 0;
        }

        int listWidth()
        {
            return width;
        }

        int listAnchor()
        {
            return getY() + getHeight();
        }

        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

            int row = openList == this ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, getX(), getY(), getWidth(), getHeight(), row, 3);
            poseStack.text(font, caption, (int)(getX() + 4), (int)(getY() + (getHeight() - 8) / 2), 0xFF9AA2B2, true);
            // The value is right-aligned so the eye can run down the chosen
            // values in a column without the captions in the way.
            boolean any = "Any".equals(chosen);
            String shown = font.width(chosen) > getWidth() - font.width(caption) - 14
                ? font.plainSubstrByWidth(chosen, getWidth() - font.width(caption) - 18) + "."
                : chosen;
            poseStack.text(font, shown, (int)(getX() + getWidth() - 4 - font.width(shown)), (int)(getY() + (getHeight() - 8) / 2), any ? 0xFF6A7080 : 0xFFFFE9B0, true);
        }
    }

    /** Draws the open selector's list, above everything else on the panel. */
    private void renderOpenList(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        if(openList == null)
        {
            return;
        }
        List<Option> values = openList.values;
        int rowH = LIST_ROW_H;
        int shown = Math.min(values.size() + 1, listRows());
        int listX = openList.getX();
        int listW = openList.listWidth();
        int listY = openList.listAnchor();
        // Opens upward when there is no room below, so the last selector in the
        // drawer is not a list drawn off the bottom of the screen.
        if(listY + shown * rowH > filterViewBottom())
        {
            listY = Math.max(filterViewTop(), openList.getY() - shown * rowH);
        }

        poseStack.pose().pushMatrix();
        // The Z translate that lifted this above a later panel is gone:
        // retained mode draws in the order described, so ordering the
        // calls is what layering means now.
        NineSlice.draw(poseStack, HubTextures.PANEL, listX, listY, listW, shown * rowH + 4);
        for(int i = 0; i < shown; i++)
        {
            int index = i + openListScroll;
            // The label, never the key: the row says "Counter" while choosing
            // it stores "TRAP/Counter".
            String label = index == 0 ? "Any" : values.get(index - 1).label();
            int rowY = listY + 2 + i * rowH;
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                && mouseY >= rowY && mouseY < rowY + rowH;
            poseStack.text(font, label, (int)(listX + 5), (int)(rowY + 2),
                hovered ? 0xFFFFE9B0 : 0xFFC2C9D6, true);
        }
        pixelScrollbar(poseStack, listX + listW - 5, listY + 2, shown * rowH,
            (values.size() + 1) * rowH, openListScroll * rowH);
        poseStack.pose().popMatrix();
    }

    /** The click on an open list, or false if it fell outside one. */
    private boolean clickOpenList(double mouseX, double mouseY)
    {
        if(openList == null)
        {
            return false;
        }
        List<Option> values = openList.values;
        int rowH = LIST_ROW_H;
        int shown = Math.min(values.size() + 1, listRows());
        int listX = openList.getX();
        int listW = openList.listWidth();
        int listY = openList.listAnchor();
        if(listY + shown * rowH > filterViewBottom())
        {
            listY = Math.max(filterViewTop(), openList.getY() - shown * rowH);
        }
        if(mouseX < listX || mouseX >= listX + listW
            || mouseY < listY || mouseY >= listY + shown * rowH + 4)
        {
            // A click anywhere else closes the list without choosing, and is
            // then allowed through to whatever was actually clicked.
            openList = null;
            return false;
        }
        int row = (int)((mouseY - listY - 2) / rowH) + openListScroll;
        if(row >= 0 && row <= values.size())
        {
            DropdownButton target = openList;
            openList = null;
            // The key, never the label: what the query stores is qualified.
            target.choose.accept(row == 0 ? null : values.get(row - 1).key());
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        }
        return true;
    }

    /** How tall one row of a selector's open list is. */
    private static final int LIST_ROW_H = 11;

    /**
     * The most rows a selector's list ever shows -- a ceiling, not a count.
     * <p>
     * Twelve rows plus the panel's own 4 is 136 tall, and the drawer's strip is
     * 127 at 1634&times;920 on GUI scale 4. Drawn at the constant the list ran
     * over the card-size slider and into the Save and Exit row, and stayed
     * clickable there because the click test repeated the same arithmetic.
     */
    private static final int LIST_ROWS_MAX = 12;

    /**
     * How many rows an open list shows here and now.
     * <p>
     * One method, read by the drawing, the click test and the wheel handler, so
     * the three cannot drift.
     */
    private int listRows()
    {
        return geom.listRows();
    }

    /**
     * The favourites chip: the star, lit when only starred cards are showing.
     * <p>
     * Its own class rather than a ChipButton with a label, because the mark on
     * a favourited card is a picture and the chip that filters by it should be
     * the same picture.
     */
    private static class StarChip extends HubWidgets.TextureButton
    {
        private final java.util.function.BooleanSupplier lit;

        StarChip(int x, int y, int width, int height,
            java.util.function.BooleanSupplier lit, OnPress onPress)
        {
            super(x, y, width, height, Component.literal(""), onPress);
            this.lit = lit;
        }

        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

            boolean on = lit.getAsBoolean();
            int row = on ? NineSlice.SELECTED : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, getX(), getY(), getWidth(), getHeight(), row, 3);
            int mark = Math.min(getWidth(), getHeight()) - 4;
            // Dimmed when off, so the chip reads as a switch rather than as a
            // decoration that happens to be there. The texture and the dimming
            // are both arguments now: one was a separate bind, the other a
            // shader colour, and neither survives outside immediate mode.
            DdBlitUtil.blit(poseStack, HubTextures.STAR,
                getX() + (getWidth() - mark) / 2, getY() + (getHeight() - mark) / 2,
                mark, mark, 0F, 0F, 1F, 1F,
                on ? DdBlitUtil.NO_TINT : DdBlitUtil.alpha(0.45F));
        }
    }

    /**
     * How much wider the Sleeves button is than its label needs, so the swatch
     * it wears has somewhere to sit.
     * <p>
     * A card-shaped swatch as tall as the button's inside is
     * {@code round(14 * 480/700)} = 10 wide, plus the 4 either side of it.
     */
    private static final int SWATCH_ROOM = 14;

    /**
     * Opens the sleeve picker, wearing what the open deck is printed on.
     * <p>
     * The swatch is the point of it being its own widget rather than a plain
     * button: the row then answers "what is this deck dressed in" without
     * anything having to be opened, and it is read fresh every frame so a
     * choice made in the picker -- or undone by a server refusal -- shows here
     * without anything having to tell it.
     */
    private class SleeveButton extends HubWidgets.TextureButton
    {
        SleeveButton(int x, int y, int width, int height, Component label)
        {
            super(x, y, width, height, label, pressed ->
            {
            });
        }

        @Override
        public void onPress()
        {
            // Written out on the way, as the route to the card page is: the
            // tick that normally saves a deck does not run while another
            // screen is up.
            EditorState.flush();
            if(minecraft != null)
            {
                minecraft.setScreen(new SleevePickerScreen(DeckEditorScreen.this));
            }
        }

        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

            int row = !active ? NineSlice.DISABLED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.BUTTON, getX(), getY(), getWidth(),
                getHeight(), row, 3);

            int swatchH = getHeight() - 6;
            int swatchW = Math.max(4, Math.round(swatchH * DuelTextures.CARD_ASPECT));
            // Centred, now that it is the whole content of the button rather
            // than something sitting to the left of a word. A deck with its
            // sleeves off draws the plain card back here, which is the honest
            // picture of what its cards will look like.
            SleevePickerScreen.drawSleeve(poseStack, EditorState.deck().sleeve(),
                getX() + (getWidth() - swatchW) / 2, getY() + 3, swatchW, swatchH,
                DdBlitUtil.NO_TINT);
        }
    }

    /** The deck-box swatch immediately to the right of the sleeve swatch. */
    private class DeckBoxButton extends HubWidgets.TextureButton
    {
        DeckBoxButton(int x, int y, int width, int height, Component label)
        {
            super(x, y, width, height, label, pressed ->
            {
            });
            setTooltipLines(java.util.List.of("Choose deck box"));
        }

        @Override
        public void onPress()
        {
            EditorState.flush();
            if(minecraft != null)
            {
                minecraft.setScreen(new DeckBoxPickerScreen(DeckEditorScreen.this));
            }
        }

        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);

            int row = !active ? NineSlice.DISABLED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.BUTTON, getX(), getY(), getWidth(),
                getHeight(), row, 3);
            int boxH = getHeight() - 4;
            int boxW = Math.max(5, Math.round(boxH * 0.75F));
            DeckBoxPickerScreen.drawBox(graphics, EditorState.deck().deckBox(),
                getX() + (getWidth() - boxW) / 2, getY() + 2, boxW, boxH);
        }
    }

    /** A filter chip: lit when its filter is on. */
    /** Package-private so the card info page's related filter is the same chip. */
    static class ChipButton extends HubWidgets.TextureButton
    {
        private final java.util.function.BooleanSupplier lit;

        ChipButton(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier lit, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.lit = lit;
        }

        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

            int row = lit.getAsBoolean() ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, getX(), getY(), getWidth(), getHeight(), row, 3);
            drawLabel(poseStack, lit.getAsBoolean() ? 0xFFFFE9B0 : 0xFFC2C9D6);
        }
    }
}
