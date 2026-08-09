package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
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

    /** Why the last attempted add was refused; cleared on the next action. */
    private String refusal = "";
    /** Slack either side of a scrollbar, so catching it does not need pixel aim. */
    private static final int BAR_GRAB = 3;

    private int trunkScroll;
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
        Layout layout = Layout.of(LAYOUT);
        pad = layout.i("panel.pad", 8);
        gap = layout.i("card.gap", 2);
        mainColumns = Math.max(1, layout.i("deck.mainColumns", 10));
        extraColumns = Math.max(1, layout.i("deck.extraColumns", 15));
        trunkColumns = Math.max(1, layout.i("trunk.columns", 8));
        titleH = layout.i("deck.titleHeight", 14);
        headerH = layout.i("deck.headerHeight", 12);
        sectionGap = layout.i("deck.sectionGap", 6);

        panelTop = layout.i("panel.top", 34);
        panelH = height - panelTop - layout.i("panel.bottom", 30);
        leftX = pad;
        leftW = (width - pad * 3) * Math.max(20, Math.min(80, layout.i("panel.leftPercent", 58))) / 100;
        rightX = leftX + leftW + layout.i("panel.gap", 8);
        rightW = width - rightX - pad;

        resize();

        search = new EditBox(font, rightX + pad + 2, panelTop + pad + 2,
            Math.max(40, rightW - pad * 2 - layout.i("trunk.sortWidth", 56)
                - layout.i("trunk.dirWidth", 30) - 12), 14, Component.literal("Search"));
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
        addWidget(search);

        Layout layout = Layout.of(LAYOUT);
        int rowH = layout.i("trunk.searchHeight", 16) + 4;
        int chipY = panelTop + pad + rowH;
        int chipX = rightX + pad;
        int chipW = layout.i("trunk.chipWidth", 42);
        int chipH = layout.i("trunk.chipHeight", 14);
        // Kind chips. A chip is on or off, never disabled, so its third atlas
        // row is the lit state rather than a greyed one.
        for(CardQuery.Kind kind : CardQuery.Kind.values())
        {
            CardQuery.Kind target = kind;
            addRenderableWidget(new ChipButton(chipX, chipY, chipW, chipH,
                Component.literal(label(kind)),
                () -> EditorState.query().kinds().contains(target), pressed ->
            {
                EditorState.query().toggleKind(target);
                EditorState.invalidate();
                trunkScroll = 0;
            }));
            chipX += chipW + 2;
        }

        // Starred-only, beside the kind chips because it narrows the pool the
        // same way. Drawn as the star itself rather than the word "Favourites":
        // it is the same mark that appears on the cards.
        addRenderableWidget(new StarChip(chipX, chipY, chipH + 6, chipH,
            () -> EditorState.query().favouritesOnly(), pressed ->
        {
            EditorState.query().setFavouritesOnly(!EditorState.query().favouritesOnly());
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        }));

        // Sort cycles through the offered orders; the arrow flips direction.
        int sortW = layout.i("trunk.sortWidth", 56);
        int dirW = layout.i("trunk.dirWidth", 30);
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - sortW - dirW - 2,
            panelTop + pad, sortW, 16, Component.literal(EditorState.query().sort().label()), pressed ->
        {
            EditorState.query().setSort(EditorState.query().sort().next());
            EditorState.invalidate();
            rebuildControls();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - dirW, panelTop + pad,
            dirW, 16, Component.literal(EditorState.query().descending() ? "DESC" : "ASC"), pressed ->
        {
            EditorState.query().setDescending(!EditorState.query().descending());
            EditorState.invalidate();
            rebuildControls();
        }));

        // Clear Filters: greyed out when there is nothing to clear, so the
        // button itself reports whether anything is narrowing.
        HubWidgets.TextureButton clear = new HubWidgets.TextureButton(
            rightX + pad, chipY + chipH + 3, Math.min(92, rightW - pad * 2), 16,
            Component.literal("Clear Filters"), pressed ->
        {
            EditorState.query().clear();
            search.setValue("");
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        });
        clear.active = !EditorState.query().isClear();
        addRenderableWidget(clear);

        // The rest of the filters live behind this rather than on the bar:
        // there are sixty of them, which is what the official editors offer and
        // far more than fits beside a search box.
        int clearW = Math.min(92, rightW - pad * 2);
        int filtersX = rightX + pad + clearW + 3;
        addRenderableWidget(new ChipButton(filtersX, chipY + chipH + 3,
            72, 16, Component.literal(filtersOpen ? "Filters -" : "Filters +"),
            () -> filtersOpen, pressed ->
        {
            filtersOpen = !filtersOpen;
            openList = null;
            rebuildControls();
        }));
        addRenderableWidget(new ChipButton(filtersX + 75, chipY + chipH + 3,
            78, 16, Component.literal("Unowned"), EditorState::showUnowned, pressed ->
        {
            EditorState.setShowUnowned(!EditorState.showUnowned());
            trunkScroll = 0;
            rebuildControls();
        }));

        if(filtersOpen)
        {
            buildFilterDrawer(chipY + chipH + 3 + 20);
        }

        // Sorting a deck is the same idea as sorting the trunk, so it reuses
        // the trunk's chosen order rather than inventing a second one.
        // Both sit INSIDE their panel's padding rather than against the window
        // edge. Done was placed from the window width, which put its right
        // edge on the panel's border now that the panel reaches the bottom.
        int controlsY = panelTop + panelH - pad - 20;
        // Each sized from its own label rather than from a number picked to
        // suit the longest, so they read as one row instead of three
        // differently-padded boxes. Same measure as Save and Exit uses.
        Component sortLabel = Component.literal("Sort Deck");
        Component importLabel = Component.literal("Import");
        Component exportLabel = Component.literal("Export");
        int rowX = leftX + pad;

        int sortDeckW = buttonWidth(sortLabel);
        addRenderableWidget(new HubWidgets.TextureButton(rowX, controlsY, sortDeckW, 20,
            sortLabel, pressed ->
        {
            sortDeck();
            refusal = "";
        }));
        rowX += sortDeckW + BUTTON_GAP;

        // .ydk, beside the deck they act on. Import makes a NEW deck rather
        // than overwriting the open one: a file arriving is not a reason to
        // lose what is already being built.
        int importW = buttonWidth(importLabel);
        addRenderableWidget(new HubWidgets.TextureButton(rowX, controlsY, importW, 20,
            importLabel, pressed -> importDeck()));
        rowX += importW + BUTTON_GAP;

        addRenderableWidget(new HubWidgets.TextureButton(rowX, controlsY,
            buttonWidth(exportLabel), 20, exportLabel, pressed ->
        {
            DeckFiles.Result result = DeckFiles.export(EditorState.deck());
            refusal = result.message();
        }));
        // Named for what it does rather than for being finished with: the deck
        // is written to the server on the way out, and a player leaving an
        // editor should not have to guess whether that happened.
        Component leave = Component.literal("Save and Exit");
        int leaveW = Math.max(80, font.width(leave) + 16);
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - leaveW,
            controlsY, leaveW, 20, leave, pressed -> saveAndExit()));

        // Card size for the collection, directly above Save and Exit. A slider
        // rather than a cycle of fixed sizes: the useful size depends on how
        // many cards you own and how big the window is, and neither is
        // something a fixed list can answer.
        addRenderableWidget(new TrunkSizeSlider(rightX + rightW - pad - leaveW,
            controlsY - 22, leaveW, 16));
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
            rename.setY(y + 2);
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
     */
    private void buildFilterDrawer(int top)
    {
        Layout layout = Layout.of(LAYOUT);
        filterSections.clear();
        int y = top - filterScroll;
        int contentTop = y;

        int inset = 5;
        int left = rightX + pad;
        int usable = rightW - pad * 2 - 8;
        int columnGap = 6;
        int selectorW = Math.max(80, (usable - inset * 2 - columnGap) / 2);
        int rowH = 16;
        int rowGap = 4;

        int fieldsTop = y;
        int fy = y + FRAME_HEADING + inset;
        selector(left + inset, fy, selectorW, rowH, "Attribute", attributeNames(),
            EditorState.query().attributes(),
            chosen -> replaceOnly(EditorState.query().attributes(), chosen,
                EditorState.query()::toggleAttribute));
        selector(left + inset + selectorW + columnGap, fy, selectorW, rowH, "Type",
            speciesNames(), EditorState.query().species(),
            chosen -> replaceOnly(EditorState.query().species(), chosen,
                EditorState.query()::toggleSpecies));
        fy += rowH + rowGap;
        selector(left + inset, fy, selectorW, rowH, "Card Type", subTypeNames(),
            EditorState.query().subTypes(),
            chosen -> replaceOnly(EditorState.query().subTypes(), chosen,
                EditorState.query()::toggleSubType));
        selector(left + inset + selectorW + columnGap, fy, selectorW, rowH, "Ability",
            abilityNames(), EditorState.query().abilities(),
            chosen -> replaceOnly(EditorState.query().abilities(), chosen,
                EditorState.query()::toggleAbility));
        fy += rowH + inset;
        filterSections.add(new Section("Card", left, fieldsTop, usable, fy - fieldsTop));

        // The bands. Text rather than steppers because a player filtering for
        // 2500 ATK wants to type 2500, not press a button twenty-five times.
        int bandsTop = fy + FRAME_GAP;
        int bandY = bandsTop + FRAME_HEADING + inset;
        int captionW = 34;
        int boxW = Math.max(30, (selectorW - captionW - 10) / 2);
        levelMin = band(left + inset + captionW, bandY, boxW, EditorState.query().minLevel(), 0);
        levelMax = band(left + inset + captionW + boxW + 8, bandY, boxW,
            EditorState.query().maxLevel(), 0);
        attackMin = band(left + inset + selectorW + columnGap + captionW, bandY, boxW,
            EditorState.query().minAttack(), -1);
        attackMax = band(left + inset + selectorW + columnGap + captionW + boxW + 8, bandY, boxW,
            EditorState.query().maxAttack(), -1);
        bandY += rowH + rowGap;
        defenceMin = band(left + inset + captionW, bandY, boxW,
            EditorState.query().minDefence(), -1);
        defenceMax = band(left + inset + captionW + boxW + 8, bandY, boxW,
            EditorState.query().maxDefence(), -1);
        int bandsBottom = bandY + rowH + inset;
        filterSections.add(new Section("Numbers", left, bandsTop, usable, bandsBottom - bandsTop));

        filterContentHeight = bandsBottom - contentTop;
        int room = filterViewBottom() - top;
        int limit = Math.max(0, filterContentHeight - room);
        if(filterScroll > limit)
        {
            // The contents shrank and the old offset is past the end, so it is
            // pulled back and the layout redone from the corrected position.
            filterScroll = limit;
            buildFilterDrawer(top);
        }
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
        for(String value : new java.util.ArrayList<>(current))
        {
            toggle.accept(value);
        }
        if(chosen != null)
        {
            toggle.accept(chosen);
        }
    }

    /** A selector: its caption, and the value chosen, on one line. */
    private void selector(int x, int y, int width, int height, String caption,
        List<String> values, java.util.Set<String> current, java.util.function.Consumer<String> choose)
    {
        String chosen = current.isEmpty() ? "Any" : current.iterator().next();
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
        return panelTop + panelH - pad - Layout.of(LAYOUT).i("panel.controls", 28);
    }

    private int maxFilterScroll()
    {
        return Math.max(0, filterContentHeight - (filterViewBottom() - filterViewTop() - 4));
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
     * Every sub-type a card can carry, monsters first, then spells, then traps
     * -- the order the kind chips above are in.
     */
    private static List<String> subTypeNames()
    {
        List<String> names = new java.util.ArrayList<>(List.of("Normal", "Effect"));
        for(de.cas_ual_ty.dueldimension.card.properties.MonsterType type
            : de.cas_ual_ty.dueldimension.card.properties.MonsterType.values())
        {
            names.add(type.name);
        }
        for(de.cas_ual_ty.dueldimension.card.properties.SpellType type
            : de.cas_ual_ty.dueldimension.card.properties.SpellType.values())
        {
            // Normal and Continuous name a spell type AND a trap type, and the
            // filter matches by name, so listing them twice would be two chips
            // that do the same thing.
            if(!names.contains(type.name))
            {
                names.add(type.name);
            }
        }
        for(de.cas_ual_ty.dueldimension.card.properties.TrapType type
            : de.cas_ual_ty.dueldimension.card.properties.TrapType.values())
        {
            if(!names.contains(type.name))
            {
                names.add(type.name);
            }
        }
        return names;
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

    private void sortDeck()
    {
        DeckList deck = EditorState.deck();
        for(DeckList.Part part : DeckList.Part.values())
        {
            List<Integer> codes = deck.partFor(part);
            List<Properties> cards = new java.util.ArrayList<>();
            List<Integer> unknown = new java.util.ArrayList<>();
            for(int code : codes)
            {
                Properties card = card(code);
                if(card == null)
                {
                    unknown.add(code);
                }
                else
                {
                    cards.add(card);
                }
            }
            List<Properties> sorted = new java.util.ArrayList<>(cards);
            sorted.sort(DECK_ORDER);
            codes.clear();
            for(Properties card : sorted)
            {
                codes.add((int)card.getId());
            }
            // A card the database does not know still belongs to the deck, so
            // it is kept rather than dropped by the sort.
            codes.addAll(unknown);
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
     * How many rows a part is drawn with: enough for what it holds, plus one
     * spare to drop into, never more than it can hold.
     */
    /**
     * Works out how big a card can be drawn, given how many rows the deck
     * currently needs.
     * <p>
     * Run every frame rather than once when the screen opens, because the
     * number of rows follows the deck and the deck changes while the screen is
     * up. Computing it once meant that adding cards eventually pushed the side
     * deck off the bottom of its container -- the sizing was answering a
     * question about a deck that no longer existed.
     */
    private void resize()
    {
        Layout layout = Layout.of(LAYOUT);

        // Deck cards fill the row. Their width comes from the columns -- ten
        // across with exactly `gap` between -- so the pitch is deckCardW + gap
        // rather than the panel divided by ten. Dividing the panel and then
        // drawing a smaller card inside each cell is what left the wide empty
        // channels between columns.
        //
        // Width is authoritative for this side. If the stacked sections become
        // taller than the viewport, the deck's existing scrollbar exposes the
        // rest instead of shrinking ten cards into only part of their row.
        float aspect = layout.f("card.aspect", 480F / 700F);
        int usableW = leftW - pad * 2;
        deckCardW = (usableW - (mainColumns - 1) * gap) / mainColumns;
        deckCardW = Math.max(layout.i("card.minWidth", 10),
            Math.min(layout.i("card.maxWidth", 72), deckCardW));
        deckCardH = Math.max(8, Math.round(deckCardW / aspect));

        // The collection deliberately stays compact. It used to share the
        // height-constrained size below with the deck; retaining that size for
        // the trunk preserves the useful dense catalogue while letting the
        // deck itself occupy its full ten-column workspace.
        trunkCardW = deckCardW;

        // Preserve the collection's former height-aware sizing. Its density is
        // useful when browsing hundreds of cards and is independent from the
        // ten-column deck workspace now.
        int rowsTotal = rowsFor(DeckList.Part.MAIN)
            + rowsFor(DeckList.Part.EXTRA) + rowsFor(DeckList.Part.SIDE);
        int heightBudget = deckViewHeight() - (headerH + sectionGap) * 3 - rowsTotal * gap;
        int fitH = Integer.MAX_VALUE;
        if(rowsTotal > 0 && heightBudget > 0)
        {
            fitH = heightBudget / rowsTotal;
            // FLOOR, not round. Rounding the width up and then deriving the
            // height from it rounds up a second time, so a card could end up a
            // pixel taller than its share of the budget -- and a pixel per row
            // over ten rows is ten pixels, which is the scrollbar back on
            // exactly the full deck this exists to fit.
            int fitW = Math.max(layout.i("card.minWidth", 10), (int)Math.floor(fitH * aspect));
            trunkCardW = Math.min(trunkCardW, fitW);
        }
        // The player's own scale, last, so it multiplies the size the
        // collection would otherwise have chosen. Clamped to the same minimum
        // the automatic sizing uses, and to the panel's width so the largest
        // setting still leaves at least one column.
        trunkCardW = Math.max(layout.i("card.minWidth", 10),
            Math.round(trunkCardW * trunkScale));
        trunkCardW = Math.min(trunkCardW, Math.max(layout.i("card.minWidth", 10),
            rightW - pad * 2));
        trunkCardH = Math.max(8, Math.round(trunkCardW / aspect));
        // The budget is a hard ceiling, so it is enforced on the number that
        // actually decides the row pitch rather than trusted to the arithmetic
        // above. Costs at most one pixel of aspect accuracy. Skipped once the
        // player has scaled up on purpose -- the collection scrolls, so its
        // cards are allowed to be taller than one deck row's share.
        if(trunkScale <= 1F && trunkCardH > fitH)
        {
            trunkCardH = Math.max(8, fitH);
        }

        // Rows grow with what is actually in each part, plus one spare so there
        // is always an empty slot to drop onto, capped by what the part can
        // hold. Reserving every part's full capacity meant ten rows on screen
        // at all times whatever the deck held.
        mainRows = rowsFor(DeckList.Part.MAIN);

        deckScroll = Math.max(0, Math.min(deckScroll, maxDeckScroll()));
        // The trunk too. Its row height moves as the deck grows, so a
        // deck grows -- so a scroll position taken when cards were small can
        // point past the end once they are large, and the grid draws rows that
        // are not there while the ones that are cannot be reached.
        trunkScroll = Math.max(0, Math.min(trunkScroll, maxTrunkScroll()));
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
        return panelTop + pad + titleH;
    }

    private int deckViewHeight()
    {
        return panelH - pad * 2 - titleH - Layout.of(LAYOUT).i("panel.controls", 28);
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
        int columns = partColumns(part);
        int max = (int)Math.ceil(part.capacity() / (double)columns);
        int held = EditorState.deck().partFor(part).size();
        int used = (int)Math.ceil(held / (double)columns);
        // The spare row is a place to drop a card, and it is only needed when
        // the last row is FULL. Reserving one unconditionally meant a part
        // holding a single card drew a container two rows tall with an empty
        // row under it -- and did the same to every part, so the three
        // sections between them wasted three rows of the panel.
        int rows = held % columns == 0 ? used + 1 : used;
        return Math.max(1, Math.min(max, rows));
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

    private int partColumns(DeckList.Part part)
    {
        return mainColumns;
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
        int column = (int)((mouseX - (leftX + pad)) / cellW);
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
    private int trunkCountY()
    {
        return trunkGridTop() + trunkVisibleRows() * (trunkCardH + gap) + 3;
    }

    private int trunkVisibleRows()
    {
        int bottom = panelTop + panelH - pad - 12
            - Layout.of(LAYOUT).i("panel.controls", 28);
        return Math.max(1, (bottom - trunkGridTop()) / (trunkCardH + gap));
    }

    /** Below the search row, the chip row and the Clear row. */
    private int trunkGridTop()
    {
        Layout layout = Layout.of(LAYOUT);
        int rowH = layout.i("trunk.searchHeight", 16) + 4;
        return panelTop + pad + rowH + layout.i("trunk.chipHeight", 14) + 3 + 16 + 6;
    }

    private int trunkIndexAt(double mouseX, double mouseY)
    {
        if(filtersOpen)
        {
            // The grid is not drawn while the drawer is open, so nothing in it
            // is under the cursor either.
            return -1;
        }
        trunkColumns = Math.max(1, (rightW - pad * 2 + gap) / (trunkCardW + gap));
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        // The scrollbar before anything else: it sits over the collection grid,
        // whose card hit test would otherwise swallow the click.
        if(event.button() == 0 && grabTrunkBar(event.x(), event.y()))
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
            && super.mouseClicked(event, false))
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
                    cards.remove(index);
                    return true;
                }
                carried = card(cards.remove(index));
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
        return super.mouseClicked(event, false);
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
            // just putting it back.
            EditorState.deck().partFor(part).add((int)held.getId());
            carriedFrom = null;
            return;
        }
        if(!add(held, part))
        {
            returnCarriedTo(held);
        }
        carriedFrom = null;
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
            List<Integer> cards = EditorState.deck().partFor(carriedFrom);
            int at = Math.min(Math.max(0, carriedIndex), cards.size());
            cards.add(at, (int)held.getId());
        }
        carriedFrom = null;
        carriedIndex = -1;
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
        menuX = (int)mouseX;
        menuY = (int)mouseY;
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
        }
        return labels;
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

    private int menuRows()
    {
        return menuPart == null ? 3 : 4;
    }

    private int menuHeight()
    {
        return menuRows() * MENU_ROW + MENU_EDGE * 2;
    }

    private int favouriteRow()
    {
        return 0;
    }

    private int infoRow()
    {
        return 1;
    }

    private int addRow()
    {
        return 2;
    }

    /** Only present when the card clicked was one already in the deck. */
    private int removeRow()
    {
        return menuPart == null ? -1 : 3;
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
        int favourite = favouriteRow();
        int info = infoRow();
        int remove = removeRow();
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
                minecraft.setScreenAndShow(new CardInfoScreen(this, target));
            }
            return true;
        }
        if(row == favourite)
        {
            EditorState.toggleFavourite((int)target.getId());
            return true;
        }
        if(row == addRow())
        {
            // Add one, into the part it belongs in.
            DeckList.Part destination = part != null ? part
                : target.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
            add(target, destination);
            return true;
        }
        if(row == remove && part != null && index >= 0)
        {
            List<Integer> cards = EditorState.deck().partFor(part);
            if(index < cards.size())
            {
                cards.remove(index);
            }
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
            EditorState.deck().partFor(homeFor(card)).add((int)card.getId());
        }
        return verdict;
    }

    /** How many copies a card may reach at all, for "2 / 3" style counts. */
    static int ceilingFor(Properties card)
    {
        return DeckLimits.maxCopies((int)card.getId(), EditorState.trunk(),
            EditorState.banlist(), true);
    }

    /** Adds a card if every rule allows it, else records why not. */
    private boolean add(Properties card, DeckList.Part part)
    {
        DeckLimits.Verdict verdict = DeckLimits.canAddToDraft(EditorState.deck(), part,
            (int)card.getId(), EditorState.banlist());
        if(!verdict.allowed())
        {
            refusal = verdict.reason();
            return false;
        }
        EditorState.deck().partFor(part).add((int)card.getId());
        return true;
    }

    /**
     * Releasing the button drops the carried card where the cursor is, but only
     * if the mouse actually travelled. Without the distance test a plain click
     * would pick a card up and immediately put it back down, so click-to-carry
     * would be impossible.
     */
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        trunkBarGrab = -1;
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
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        // Shift reads the hovered card's description; the plain wheel scrolls
        // whatever grid is under the cursor.
        //
        // It used to be the other way round, and that was a mistake: a full
        // grid has a card under the cursor almost everywhere, so the wheel
        // reached the description whatever the player meant by it and the
        // collection could not be scrolled at all. Reading a long effect is the
        // rarer thing to want, so it is the one that takes the modifier.
        if(carried == null && com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
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
            int overflow = Math.max(0, openList.values.size() + 1 - LIST_ROWS);
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event)
    {
        int key = event.key();
        int scan = event.scancode();
        int modifiers = event.modifiers();
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
            if(rename.keyPressed(event))
            {
                return true;
            }
        }
        if(search != null && search.isFocused() && search.keyPressed(event))
        {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event)
    {
        char typed = (char)event.codepoint();
        int modifiers = 0;
        if(rename != null && rename.isFocused() && rename.charTyped(event))
        {
            return true;
        }
        if(search != null && search.isFocused() && search.charTyped(event))
        {
            return true;
        }
        return super.charTyped(event);
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
    {
        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, the blur is once-per-frame, and the frame a screen
        // opens over another that already asked for it took the client down.
        // Same decision as EngineDuelScreen, for the same crash.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        // The deck decides the card size, and the deck changes while this
        // screen is open, so the size is worked out now rather than when it
        // opened.
        resize();
        NineSlice.draw(poseStack, HubTextures.PANEL, leftX, panelTop, leftW, panelH);
        NineSlice.draw(poseStack, HubTextures.PANEL, rightX, panelTop, rightW, panelH);

        renderDeckSide(poseStack, mouseX, mouseY);
        renderTrunkSide(poseStack, mouseX, mouseY);

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        search.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        if(filtersOpen && levelMin != null)
        {
            for(EditBox box : List.of(levelMin, levelMax, attackMin, attackMax,
                defenceMin, defenceMax))
            {
                if(inFilterView(box.getY() - 2, 16))
                {
                    box.extractRenderState(poseStack, mouseX, mouseY, partialTick);
                }
            }
        }
        if(filtersOpen)
        {
            renderOpenList(poseStack, mouseX, mouseY);
        }
        if(rename != null)
        {
            rename.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        }

        if(!refusal.isEmpty())
        {
            int textWidth = font.width(refusal);
            NineSlice.draw(poseStack, HubTextures.PANEL, width / 2 - textWidth / 2 - 8,
                height - 52, textWidth + 16, 18);
            poseStack.text(font, refusal, (int)(width / 2F - textWidth / 2F), (int)(height - 47), 0xFFFF8A80, true);
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
                drawPreview(poseStack, hovered, mouseX, mouseY);
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

        // The carried card rides the cursor, as an inventory stack does.
        if(carried != null)
        {
            drawCard(poseStack, carried, mouseX - deckCardW / 2, mouseY - deckCardH / 2,
                deckCardW, deckCardH, 1F);
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
            // has to be re-clamped; resize() does that for both grids.
            resize();
        }

        /** Silent: a slider that clicks on every step of a drag is noise. */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** Space between the buttons on a control row. */
    private static final int BUTTON_GAP = 6;

    /**
     * How wide a button has to be for its label.
     * <p>
     * A floor as well as a measure, so a one-word button is still big enough
     * to aim at rather than shrinking to the width of its text.
     */
    private int buttonWidth(Component label)
    {
        return Math.max(56, font.width(label) + 16);
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
        // both scroll positions have to be worked out again.
        resize();
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
    private void drawPreview(GuiGraphicsExtractor poseStack, Properties card, int mouseX, int mouseY)
    {
        Layout layout = Layout.of(LAYOUT);
        int artW = layout.i("preview.width", 78);
        int artH = Math.round(artW / layout.f("card.aspect", DuelTextures.CARD_ASPECT));
        int inner = 5;
        int panelW = Math.max(artW, layout.i("preview.textWidth", 132)) + inner * 2;
        int textW = panelW - inner * 2;

        // Text is drawn at half size, so it is wrapped to twice the width and
        // its line height is halved to match.
        float scale = layout.f("preview.textScale", 0.5F);
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
        card.addFacts(header);
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

        int maxLines = layout.i("preview.maxLines", 10);
        int maxScroll = Math.max(0, textLines.size() - maxLines);
        previewScroll = Math.min(previewScroll, maxScroll);
        int shownLines = Math.min(textLines.size() - previewScroll, maxLines);

        int panelH = inner * 2 + artH + 4
            + nameLines.size() * lineH + 2
            + headerLines.size() * lineH + 3
            + shownLines * lineH + (maxScroll > 0 ? lineH + 2 : 0);

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

        DdBlitUtil.blit(poseStack,
            DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
            x + (panelW - artW) / 2, y + inner, artW, artH,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

        // Everything below is half size. Coordinates are divided by the scale
        // so the text still lands where the panel arithmetic put it.
        poseStack.pose().pushMatrix();
        poseStack.pose().scale(scale, scale);
        float sx = (x + inner) / scale;
        float sy = (y + inner + artH + 4) / scale;
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

        // The band the open deck's name sits in, drawn before the name so the
        // name is on top of it, and sized to the title strip so the first
        // section heading starts immediately beneath.
        NineSlice.draw(poseStack, HubTextures.TITLE_RIBBON, leftX + pad - 2, panelTop + pad - 2,
            leftW - pad * 2 + 4, titleH);

        if(rename == null)
        {
            String title = deck.name();
            List<DeckList> all = EditorState.decks();
            if(all.size() > 1)
            {
                title += "  (" + (EditorState.currentIndex() + 1) + "/" + all.size() + ")";
            }
            if(deck.origin() == DeckList.Origin.STRUCTURE)
            {
                title += "  [structure]";
            }
            // Centred in the ribbon rather than sitting on its top edge.
            poseStack.text(font, title, (int)(leftX + pad + 2), (int)(panelTop + pad + (titleH - font.lineHeight) / 2F), 0xFFF4D089, true);
        }

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
            NineSlice.draw(poseStack, HubTextures.PANEL_INSET, leftX + pad - 2, top - 2,
                leftW - pad * 2 + 4, rows * (deckCardH + gap) + 4);
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
                        drawCard(poseStack, card, leftX + pad + column * cellW,
                            top + row * (deckCardH + gap), deckCardW, deckCardH, 1F, missing);
                    }
                }
            }
        }
        clipToDeckView(poseStack, false);

        // A scroll bar only when there is something to scroll, so a deck that
        // fits shows no furniture it does not need.
        // Measured in pixels rather than rows, because the deck's three
        // sections are different heights and a row is not a fixed unit here.
        pixelScrollbar(poseStack, leftX + leftW - pad + 1, deckViewTop(), deckViewHeight(),
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

        pixelScrollbar(poseStack, rightX + rightW - pad - 2, top + 2, bottom - top - 4,
            filterContentHeight, filterScroll);
    }

    /**
     * Asks for the art of the rows either side of the ones on screen.
     * <p>
     * A card's image is only scaled once something asks to draw it, so scrolling
     * used to meet a fresh page of cold cards every time and show placeholders
     * until the workers caught up. Asking a page early means the wheel arrives
     * at art that is already made.
     * <p>
     * This is deliberately just a request and not a draw: the readiness gate
     * queues the work as a side effect of being asked, the request is cheap
     * once the answer is cached, and duplicate requests are dropped by
     * ImageHandler rather than piling up behind each other.
     */
    private void warmAhead(List<Properties> shown, int visibleRows)
    {
        int first = Math.max(0, (trunkScroll - visibleRows) * trunkColumns);
        int last = Math.min(shown.size(), (trunkScroll + visibleRows * 2) * trunkColumns);
        int onScreenFirst = Math.max(0, trunkScroll * trunkColumns);
        int onScreenLast = Math.min(shown.size(),
            (trunkScroll + visibleRows) * trunkColumns);

        for(int i = first; i < last; i++)
        {
            Properties card = shown.get(i);
            if(card == null)
            {
                continue;
            }
            DuelTextures.card(card, (byte)0, DuelTextures.ICON_CARD_SIZE);
            // The hover preview is a different, much larger texture, so warming
            // the grid icon did nothing for it and the first hover over every
            // card still waited. Only for the rows actually on screen though: a
            // 512 is about sixteen times the pixels of a 128, and a card you
            // MIGHT scroll to is a far weaker bet than one you are looking at.
            if(i >= onScreenFirst && i < onScreenLast)
            {
                DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE);
            }
        }
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
        int thumbH = Math.max(12, height * visible / Math.max(1, total));
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(trunkBarGrab >= 0)
        {
            dragTrunkBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /** A scrollbar over a run of pixels rather than a count of rows. */
    private void pixelScrollbar(GuiGraphicsExtractor poseStack, int x, int y, int height,
        int content, int offset)
    {
        int overflow = Math.max(0, content - height);
        if(overflow <= 0 || height <= 0)
        {
            return;
        }
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        int thumbH = Math.max(12, height * height / Math.max(1, content));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
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
            renderFilterDrawer(poseStack);
            poseStack.text(font, EditorState.visible().size() + " cards", (int)(rightX + pad), (int)trunkCountY(), 0xFF7A8090, true);
            return;
        }

        List<Properties> shown = EditorState.visible();
        trunkColumns = Math.max(1, (rightW - pad * 2 + gap) / (trunkCardW + gap));
        int gridTop = trunkGridTop();
        int cellW = trunkCardW + gap;
        int visibleRows = trunkVisibleRows();
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, rightX + pad - 2, gridTop - 2,
            rightW - pad * 2 + 4, visibleRows * (trunkCardH + gap) + 4);

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
                    !unowned && inDeck >= max ? 0.35F : 1F);
                if(inDeck > 0)
                {
                    String count = inDeck + "/" + max;
                    // Half size: at full size this reads as a label on the card
                    // rather than a mark on it, and covers the art it is
                    // annotating. Scaled around the bottom-right corner so it
                    // stays tucked there whatever the card size is.
                    float scale = layout.f("trunk.countScale", 0.5F);
                    poseStack.pose().pushMatrix();
                    poseStack.pose().translate(x + trunkCardW - font.width(count) * scale - 1,
                        y + trunkCardH - font.lineHeight * scale - 1);
                    poseStack.pose().scale(scale, scale);
                    outlined(poseStack, count, inDeck >= max ? 0xFFFF8A80 : 0xFFF4D089);
                    poseStack.pose().popMatrix();
                }
            }
        }

        warmAhead(shown, visibleRows);

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

    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y, float alpha)
    {
        drawCard(poseStack, card, x, y, trunkCardW, trunkCardH, alpha,
            EditorState.showUnowned() && !EditorState.owns((int)card.getId()));
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
        drawCard(poseStack, card, x, y, w, h, alpha, false);
    }

    private void drawCard(GuiGraphicsExtractor poseStack, Properties card, int x, int y,
        int w, int h, float alpha, boolean halfSaturation)
    {
        // Fetched at twice the size it is drawn at, and filtered on the way
        // down, so the art is legible rather than a 64-pixel image stretched
        // across a 40-pixel icon.
        // The texture is an argument now rather than a separate bind, and the
        // window is given as its two corners rather than an offset and a size.
        DdBlitUtil.blit(poseStack,
            halfSaturation
                ? DuelTextures.cardUnowned(card, (byte)0, DuelTextures.ICON_CARD_SIZE)
                : DuelTextures.card(card, (byte)0, DuelTextures.ICON_CARD_SIZE), x, y, w, h,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1,
            DdBlitUtil.tint(1F, 1F, 1F, alpha));

        if(EditorState.isFavourite((int)card.getId()))
        {
            // A fraction of the card rather than a fixed size, so it stays in
            // proportion however small the icons get.
            int mark = Math.max(6, w / 3);
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
            minecraft.setScreenAndShow(parent);
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
            minecraft.setScreenAndShow(parent);
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
        private final List<String> values;
        private final java.util.function.Consumer<String> choose;

        DropdownButton(int x, int y, int width, int height, String caption, String chosen,
            List<String> values, java.util.function.Consumer<String> choose)
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
        public void onPress(net.minecraft.client.input.InputWithModifiers input)
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
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
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
        List<String> values = openList.values;
        int rowH = 11;
        int shown = Math.min(values.size() + 1, LIST_ROWS);
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
            String label = index == 0 ? "Any" : values.get(index - 1);
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
        List<String> values = openList.values;
        int rowH = 11;
        int shown = Math.min(values.size() + 1, LIST_ROWS);
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
            target.choose.accept(row == 0 ? null : values.get(row - 1));
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        }
        return true;
    }

    /** How many rows of a selector's list are shown before it scrolls. */
    private static final int LIST_ROWS = 12;

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
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
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
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
            int row = lit.getAsBoolean() ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, getX(), getY(), getWidth(), getHeight(), row, 3);
            drawLabel(poseStack, lit.getAsBoolean() ? 0xFFFFE9B0 : 0xFFC2C9D6);
        }
    }
}
