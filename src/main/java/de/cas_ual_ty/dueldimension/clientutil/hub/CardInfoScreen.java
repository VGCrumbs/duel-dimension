package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.duel.profile.DeckLimits;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything known about one card, on one screen.
 * <p>
 * Reached from the card's own right-click menu. Three questions a player asks
 * about a card that the editor could not answer: what does it do, where do I
 * get it, and what goes with it.
 * <p>
 * The two searches behind those last answers walk the whole database, so they
 * are done once when the screen opens and remembered — not per frame, and not
 * per card drawn.
 */
public class CardInfoScreen extends Screen
{
    /** The most related cards worth showing; past this it is a list, not a hint. */
    private static final int RELATED_LIMIT = 24;

    /**
     * Answers already worked out, by card. The database does not change while
     * the game runs, so a card looked at twice costs the search once.
     */
    private static final Map<Long, List<CardSet>> SOURCES = new LinkedHashMap<>();
    private static final Map<Long, List<Properties>> RELATED = new LinkedHashMap<>();

    private final Screen parent;
    private final Properties card;
    private List<CardSet> sources = List.of();
    private List<Properties> related = List.of();

    /**
     * The one-click add, present only when this page was opened from the deck
     * editor. Reached from anywhere else — the shop, a duel — there is no deck
     * being edited for it to add to.
     */
    private HubWidgets.TextureButton addButton;

    /**
     * Which kinds of card the related grid shows. Empty means all of them, the
     * same convention the trunk's filter uses: a filter nobody has touched
     * narrows nothing.
     */
    private final java.util.EnumSet<de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind>
        relatedKinds = java.util.EnumSet.noneOf(
            de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind.class);

    private List<FormattedCharSequence> textLines = List.of();
    private int textScroll;
    private int sourceScroll;
    private int relatedScroll;

    public CardInfoScreen(Screen parent, Properties card)
    {
        super(Component.literal(card == null ? "Card" : card.getName()));
        this.parent = parent;
        this.card = card;
    }

    @Override
    protected void init()
    {
        if(card == null)
        {
            return;
        }
        sources = SOURCES.computeIfAbsent(card.getId(), id -> findSources(card));
        related = RELATED.computeIfAbsent(card.getId(), id -> findRelated(card));

        addRenderableWidget(new HubWidgets.TextureButton(pad(), pad(), 60, 18,
            Component.literal("Back"), pressed -> onClose()));
        // Only once there is a chain to escape. At the first page Back already
        // leaves, so a second button doing the same thing would be furniture.
        if(parent instanceof CardInfoScreen)
        {
            addRenderableWidget(new HubWidgets.TextureButton(pad() + 66, pad(), 60, 18,
                Component.literal("Exit"), pressed ->
            {
                if(minecraft != null)
                {
                    minecraft.setScreenAndShow(root());
                }
            }));
        }

        // Where to buy it, next to Back on the top row. Goes through
        // ConfirmLinkScreen rather than opening the browser outright: vanilla
        // shows the address and asks first for every link it offers, and a
        // card database is no reason to be the exception.
        Component shop = Component.literal("TCGplayer");
        int shopW = Math.max(70, font.width(shop) + 14);
        int shopX = parent instanceof CardInfoScreen ? pad() + 132 : pad() + 66;
        addRenderableWidget(new HubWidgets.TextureButton(shopX, pad(), shopW, 18, shop,
            pressed -> net.minecraft.client.gui.screens.ConfirmLinkScreen
                .confirmLinkNow(this, tcgPlayerSearch(card))));

        // The star is the same mark the grids use, so toggling it here and
        // toggling it from the menu are visibly the same act.
        addRenderableWidget(new StarButton(width - pad() - 22, pad(), 22, 18,
            () -> EditorState.isFavourite((int)card.getId()),
            pressed -> EditorState.toggleFavourite((int)card.getId())));

        // Monster / Spell / Trap, the same three chips the trunk uses, because
        // narrowing a hundred archetype members to the traps is the same act.
        // The row is measured against the space it has before it is placed.
        // Marching right from a fixed start with no bound put the last chip off
        // the screen edge at small widths, where it could be neither seen nor
        // clicked -- the same authored-size-as-absolute mistake fixed elsewhere.
        int chipCount = de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind.values().length;
        int chipH = 12;
        int columnLeft = width / 2 + pad();
        int room = Math.max(0, width - pad() - columnLeft);
        // Narrow before it is nudged: shrinking the chips buys more than
        // sliding them does, and the labels are short.
        int chipW = clamp(24, 40, (room - 2 * (chipCount - 1)) / Math.max(1, chipCount));
        int rowW = chipCount * chipW + 2 * (chipCount - 1);
        // Right-aligned to the panel when the heading would push it past the
        // edge, and never left of the column it belongs to.
        int chipX = Math.max(columnLeft, Math.min(
            columnLeft + font.width("Related  (000)") + 8, width - pad() - rowW));
        for(de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind kind
            : de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind.values())
        {
            de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind target = kind;
            addRenderableWidget(new DeckEditorScreen.ChipButton(chipX, sourcesTop() - 15,
                chipW, chipH, Component.literal(DeckEditorScreen.label(kind)),
                () -> relatedKinds.contains(target), pressed ->
            {
                if(!relatedKinds.remove(target))
                {
                    relatedKinds.add(target);
                }
                // Back to the top: the card under the cursor is not the card
                // that was there before the list got shorter.
                relatedScroll = 0;
            }));
            chipX += chipW + 2;
        }

        // Following related cards is how a player finds the card they wanted;
        // making them walk back to the editor and search for it by name to put
        // it in the deck is the one step that page was meant to remove.
        if(root() instanceof DeckEditorScreen)
        {
            addButton = new HubWidgets.TextureButton(width - pad() - 26 - ADD_WIDTH, pad(),
                ADD_WIDTH, 18, Component.literal("Add to Deck"), pressed -> addToDeck());
            addRenderableWidget(addButton);
            refreshAddButton();
        }
    }

    /**
     * The related cards this page is currently showing.
     * <p>
     * An archetype runs to a hundred members and a player looking for its trap
     * should not have to scroll past sixty monsters to find it. Everything that
     * draws, scrolls or hit-tests the grid asks this rather than the full list,
     * so the filter cannot move the cards out from under the click.
     */
    private List<Properties> shownRelated()
    {
        if(relatedKinds.isEmpty())
        {
            return related;
        }
        List<Properties> kept = new ArrayList<>();
        for(Properties card : related)
        {
            if(relatedKinds.contains(kindOf(card)))
            {
                kept.add(card);
            }
        }
        return kept;
    }

    private static de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind kindOf(Properties card)
    {
        de.cas_ual_ty.dueldimension.card.properties.Type type = card.getType();
        if(type == de.cas_ual_ty.dueldimension.card.properties.Type.SPELL)
        {
            return de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind.SPELL;
        }
        return type == de.cas_ual_ty.dueldimension.card.properties.Type.TRAP
            ? de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind.TRAP
            : de.cas_ual_ty.dueldimension.duel.profile.CardQuery.Kind.MONSTER;
    }

    /** Width of the add button; the count is right-aligned to its left edge. */
    private static final int ADD_WIDTH = 74;

    private void addToDeck()
    {
        DeckEditorScreen.addOne(card);
        refreshAddButton();
    }

    /**
     * Greys the button and explains itself when another copy is not allowed —
     * three already, a banlist limit, a full deck, or a card not owned outside
     * free mode. The reason comes from the same check the editor uses, so the
     * two cannot disagree about why.
     */
    private void refreshAddButton()
    {
        if(addButton == null)
        {
            return;
        }
        DeckLimits.Verdict verdict = DeckEditorScreen.roomFor(card);
        addButton.setLabelColour(verdict.allowed() ? 0xFFE6EAF2 : 0xFF6A7080);
        addButton.setTooltipLines(verdict.allowed()
            ? List.of("Adds one copy to the deck")
            : List.of(verdict.reason()));
    }

    /** How many copies of this card the deck being edited already holds. */
    private int copiesInDeck()
    {
        return EditorState.deck().copiesOf((int)card.getId());
    }

    private int pad()
    {
        return 10;
    }

    /**
     * The screen this chain started from — the editor, however many cards deep
     * the player has followed.
     */
    private Screen root()
    {
        Screen at = parent;
        while(at instanceof CardInfoScreen page)
        {
            at = page.parent;
        }
        return at;
    }

    /**
     * Every set this card is printed in.
     * <p>
     * Read off the sets themselves rather than a reverse index, because the
     * database has no reverse index and building one for a screen that opens
     * occasionally would cost more than the walk does.
     */
    private static List<CardSet> findSources(Properties card)
    {
        List<CardSet> found = new ArrayList<>();
        for(CardSet set : DdDatabase.SETS_LIST)
        {
            if(set == null || set == CardSet.DUMMY || !set.isIndependentAndItem() || set.cards == null)
            {
                continue;
            }
            for(CardHolder holder : set.cards)
            {
                if(holder != null && holder.getCard() != null
                    && holder.getCard().getId() == card.getId())
                {
                    found.add(set);
                    break;
                }
            }
        }
        return List.copyOf(found);
    }

    /**
     * Archetype names, discovered from the card text rather than listed here.
     * <p>
     * Yu-Gi-Oh text quotes the things it refers to — {@code (This card is
     * always treated as an "Archfiend" card.)} — so the quoted strings across
     * the whole database are a vocabulary of archetypes that the data supplies
     * itself. A hand-written list would look smarter and go stale with the next
     * set; this cannot, because it is derived from the same files the cards
     * come from.
     * <p>
     * Two filters separate an archetype from a one-off reference to a specific
     * card: the term has to be quoted by several cards, AND appear inside more
     * than one card's name. "Archfiend" passes both; the name of a single
     * searcher target passes neither.
     */
    private static volatile Set<String> archetypeTerms;

    /** Quoted runs of 3 to 30 characters: shorter is noise, longer is a sentence. */
    private static final java.util.regex.Pattern QUOTED =
        java.util.regex.Pattern.compile("\"([^\"]{3,30})\"");

    private static Set<String> vocabulary()
    {
        Set<String> known = archetypeTerms;
        if(known != null)
        {
            return known;
        }
        Map<String, Integer> quoted = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for(Properties card : DdDatabase.PROPERTIES_LIST)
        {
            if(card == null || card.getId() <= 0)
            {
                continue;
            }
            if(card.getName() != null)
            {
                names.add(card.getName().toLowerCase(Locale.ROOT));
            }
            String text = card.getText();
            if(text == null)
            {
                continue;
            }
            java.util.regex.Matcher matcher = QUOTED.matcher(text);
            while(matcher.find())
            {
                quoted.merge(matcher.group(1), 1, Integer::sum);
            }
        }

        Set<String> vocab = new LinkedHashSet<>();
        quoted.forEach((term, mentions) ->
        {
            if(mentions < 3)
            {
                return;
            }
            String lower = term.toLowerCase(Locale.ROOT);
            int inNames = 0;
            for(String name : names)
            {
                if(name.contains(lower) && ++inNames >= 2)
                {
                    vocab.add(term);
                    return;
                }
            }
        });
        archetypeTerms = vocab;
        return vocab;
    }

    /** The archetypes this card belongs to: named in it, or quoted by it. */
    private static Set<String> archetypesOf(Properties card)
    {
        String name = card.getName() == null ? "" : card.getName().toLowerCase(Locale.ROOT);
        String text = card.getText() == null ? "" : card.getText().toLowerCase(Locale.ROOT);
        Set<String> mine = new LinkedHashSet<>();
        for(String term : vocabulary())
        {
            String lower = term.toLowerCase(Locale.ROOT);
            if(name.contains(lower) || text.contains('"' + lower + '"'))
            {
                mine.add(term);
            }
        }
        return mine;
    }

    /**
     * Cards that go with this one: anything sharing an archetype with it, then
     * anything that names it outright.
     * <p>
     * Matching on the whole name alone was not enough. It worked for a card
     * whose name IS an archetype — Dark Magician finds Dark Magician Girl — and
     * found nothing at all for a card with a long specific name, so following a
     * related card led to a page with no relations of its own.
     */
    private static List<Properties> findRelated(Properties card)
    {
        Set<String> mine = archetypesOf(card);
        String needle = card.getName() == null ? "" : card.getName().toLowerCase(Locale.ROOT);

        List<Properties> shared = new ArrayList<>();
        List<Properties> mentions = new ArrayList<>();
        for(Properties other : DdDatabase.PROPERTIES_LIST)
        {
            if(other == null || other.getId() == card.getId() || other.getId() <= 0
                || other.getIllegal())
            {
                continue;
            }
            if(!mine.isEmpty() && !java.util.Collections.disjoint(archetypesOf(other), mine))
            {
                shared.add(other);
                continue;
            }
            String otherName = other.getName();
            String otherText = other.getText();
            if(!needle.isEmpty()
                && ((otherName != null && otherName.toLowerCase(Locale.ROOT).contains(needle))
                    || (otherText != null && otherText.toLowerCase(Locale.ROOT).contains(needle))))
            {
                mentions.add(other);
            }
        }

        List<Properties> found = new ArrayList<>(shared);
        found.addAll(mentions);
        return List.copyOf(found);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        int step = (int)Math.signum(delta);
        if(mouseY >= sourcesTop() && mouseX < width / 2)
        {
            sourceScroll = Math.max(0, Math.min(Math.max(0, sources.size() - sourceRows()),
                sourceScroll - step));
            return true;
        }
        if(mouseY >= sourcesTop())
        {
            // The related grid scrolls too. An archetype can have a hundred
            // members and showing the first five was not "related cards", it
            // was a sample.
            relatedScroll = Math.max(0, Math.min(maxRelatedScroll(), relatedScroll - step));
            return true;
        }
        textScroll = Math.max(0, Math.min(Math.max(0, textLines.size() - textRows()),
            textScroll - step));
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if(super.mouseClicked(event, false))
        {
            return true;
        }
        // A related card opens its own page, with this one as the way back, so
        // following a chain of cards can be walked back one at a time.
        Properties clicked = relatedAt(mouseX, mouseY);
        if(clicked != null && minecraft != null)
        {
            minecraft.setScreenAndShow(new CardInfoScreen(this, clicked));
            return true;
        }
        return false;
    }

    /**
     * Where the upper half starts: under the Back and star row.
     */
    private int topSection()
    {
        return pad() + 24;
    }

    /**
     * How much height the two lists get.
     * <p>
     * Taken first, and as a share of the window rather than whatever the card
     * art happens to leave over. Sizing the art to a constant and giving the
     * lists the remainder is what reduced them to a single row on a wide, short
     * window: the art was 219 tall whatever the window was.
     */
    private int listsHeight()
    {
        return Math.max(76, Math.round((height - topSection() - pad()) * 0.50F));
    }

    /**
     * The art fills the height left above the lists, and is capped by width so
     * it cannot crowd out the text beside it on a narrow window.
     */
    private int artH()
    {
        int room = height - topSection() - listsHeight() - pad() - 14;
        int byWidth = Math.round(width / 4F / DuelTextures.CARD_ASPECT);
        return Math.max(60, Math.min(room, byWidth));
    }

    private int artW()
    {
        return Math.round(artH() * DuelTextures.CARD_ASPECT);
    }

    /**
     * Where the two lists begin: below the art AND below their heading, with
     * room for the heading itself. The heading used to be drawn twelve pixels
     * above this line, which put it back over the bottom of the card.
     */
    private int sourcesTop()
    {
        return height - pad() - listsHeight() + 14;
    }

    private int sourceRows()
    {
        return Math.max(1, (height - sourcesTop() - pad()) / 11);
    }

    /**
     * Rows of effect text on screen, remembered from the last frame that drew
     * them. Scroll clamping and drawing have to agree on this, and the drawing
     * side is the one that knows where the header ended.
     */
    private int textRowsShown = 1;

    private int textRows()
    {
        return textRowsShown;
    }

    /**
     * How many rows of related cards to aim for. The card size is derived from
     * this rather than the other way round: a fixed 44px card fits three rows in
     * a tall window and one in a short one, and one row is not a grid.
     */
    private static final int RELATED_TARGET_ROWS = 3;

    private int relatedCardH()
    {
        int room = height - sourcesTop() - pad();
        // Bounded so the cards stay recognisable on a short window and stop
        // growing on a tall one, where the extra height becomes another row.
        return Math.max(34, Math.min(64, (room + 4) / RELATED_TARGET_ROWS - 4));
    }

    private int relatedCardW()
    {
        return Math.max(24, Math.round(relatedCardH() * DuelTextures.CARD_ASPECT));
    }

    private int relatedColumns()
    {
        int room = width - width / 2 - pad() * 2;
        return Math.max(1, room / (relatedCardW() + 4));
    }

    private int relatedRows()
    {
        return Math.max(1, (height - sourcesTop() - pad() + 4) / (relatedCardH() + 4));
    }

    private int maxRelatedScroll()
    {
        int columns = relatedColumns();
        int rows = (shownRelated().size() + columns - 1) / columns;
        return Math.max(0, rows - relatedRows());
    }

    private Properties relatedAt(double mouseX, double mouseY)
    {
        int cardW = relatedCardW();
        int cardH = relatedCardH();
        int left = width / 2 + pad();
        int top = sourcesTop();
        int columns = relatedColumns();
        if(mouseX < left || mouseY < top)
        {
            return null;
        }
        int column = (int)((mouseX - left) / (cardW + 4));
        int row = (int)((mouseY - top) / (cardH + 4));
        if(column < 0 || column >= columns || row < 0 || row >= relatedRows())
        {
            return null;
        }
        List<Properties> shown = shownRelated();
        int index = (row + relatedScroll) * columns + column;
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
    {
        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, the blur is once-per-frame, and the frame a screen
        // opens over another that already asked for it took the client down.
        // Same decision as EngineDuelScreen, for the same crash.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        if(card == null)
        {
            super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
            return;
        }
        NineSlice.draw(poseStack, HubTextures.PANEL, pad() - 4, pad() - 4,
            width - (pad() - 4) * 2, height - (pad() - 4) * 2);

        int artX = pad() + 4;
        int artY = pad() + 24;
        DdBlitUtil.blit(poseStack,
            DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
            artX, artY, artW(), artH(),
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

        if(addButton != null)
        {
            // Answers "have I already got some?" without leaving the page. Kept
            // beside the button it qualifies, since it is the reason the button
            // is greyed whenever the count has reached the ceiling.
            int copies = copiesInDeck();
            int ceiling = DeckEditorScreen.ceilingFor(card);
            String count = "In deck  " + copies + " / " + ceiling;
            int countX = addButton.getX() - 8 - font.width(count);
            // Dropped rather than drawn over Back and Exit on a narrow window;
            // the button's own tooltip still carries the reason it is greyed.
            if(countX > pad() + (parent instanceof CardInfoScreen ? 132 : 66))
            {
                poseStack.text(font, count, countX, pad() + 5,
                    copies > 0 ? 0xFFF4D089 : 0xFF8A93A3, true);
            }
            // Rechecked every frame: the deck can also change under this page,
            // by the player walking back to the editor and returning.
            refreshAddButton();
        }

        int detailX = artX + artW() + 12;
        int detailW = width - detailX - pad() - 4;
        renderOverview(poseStack, detailX, artY, detailW);

        renderSources(poseStack);
        renderRelated(poseStack, mouseX, mouseY);

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
    }

    /**
     * When this card was first printed, worked out rather than stored.
     * <p>
     * No card file carries a date; the SETS do, so a card's first release is
     * the earliest date among the sets that contain it. A set with no date is
     * skipped rather than treated as ancient -- promos and unreleased products
     * legitimately have none, and letting one of those win would date every
     * card in it to nothing.
     * <p>
     * <b>Cached, and that is not optional.</b> This walks all 691 sets and
     * their card lists, and renderOverview runs every frame; recomputing it per
     * frame would be a scan of tens of thousands of entries sixty times a
     * second. The empty string is cached too, so a card that is in no dated set
     * is worked out once and not retried.
     */
    private String firstRelease;

    private String firstRelease()
    {
        if(firstRelease != null)
        {
            return firstRelease;
        }
        java.util.Date earliest = null;
        long id = card.getId();
        for(de.cas_ual_ty.dueldimension.set.CardSet set
            : de.cas_ual_ty.dueldimension.DdDatabase.SETS_LIST.getList())
        {
            if(set == null || set.date == null || set.cards == null)
            {
                continue;
            }
            if(earliest != null && !set.date.before(earliest))
            {
                // Already beaten; no need to look inside this one at all.
                continue;
            }
            for(de.cas_ual_ty.dueldimension.card.CardHolder holder : set.cards)
            {
                if(holder != null && holder.getCard() != null && holder.getCard().getId() == id)
                {
                    earliest = set.date;
                    break;
                }
            }
        }
        firstRelease = earliest == null ? ""
            : new java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ROOT)
                .format(earliest);
        return firstRelease;
    }

    /**
     * Name, type line and stats, taken from the card model's own description
     * rather than composed here, so this page says what every tooltip says.
     */
    private void renderOverview(GuiGraphicsExtractor poseStack, int x, int y, int usableW)
    {
        // Name first, because the loop below styles line 0 as the title, then
        // the facts -- which is where a monster's species comes from.
        List<Component> information = new ArrayList<>();
        information.add(Component.literal(card.getName() == null ? "" : card.getName()));
        card.addFacts(information);
        String released = firstRelease();
        if(!released.isEmpty())
        {
            information.add(Component.literal("First released: " + released));
        }

        int line = y;
        for(int i = 0; i < information.size(); i++)
        {
            String text = information.get(i).getString();
            if(text.isBlank())
            {
                continue;
            }
            poseStack.text(font, text, x, line, i == 0 ? 0xFFF4D089 : 0xFFC2C9D6, true);
            line += i == 0 ? 14 : 11;
        }


        // The effect, in a recessed box so it reads as the card's own words.
        int boxY = line;
        // Down to the lists rather than down to the art. On a narrow window the
        // art is capped by width and stops well short, and measuring the box
        // against it left a band of empty panel between the two halves.
        int boxH = Math.max(30, sourcesTop() - 20 - boxY);
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, x - 3, boxY - 3, usableW + 6, boxH);
        textLines = font.split(Component.literal(card.getText() == null ? "" : card.getText()),
            usableW - 6);
        int rows = Math.max(1, (boxH - 8) / 10);
        textRowsShown = rows;
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, textLines.size() - rows)));
        for(int i = 0; i < rows && i + textScroll < textLines.size(); i++)
        {
            poseStack.text(font, textLines.get(i + textScroll), x, boxY + i * 10, 0xFFC2C9D6, false);
        }
        if(textLines.size() > rows)
        {
            String more = "scroll  " + Math.min(textLines.size(), textScroll + rows)
                + " / " + textLines.size();
            poseStack.text(font, more, x + usableW - font.width(more), boxY + boxH - 12,
                0xFF6E7686, true);
        }
    }

    private void renderSources(GuiGraphicsExtractor poseStack)
    {
        int x = pad() + 4;
        int y = sourcesTop();
        int w = width / 2 - x - pad();
        poseStack.text(font, "Obtained from  (" + sources.size() + ")", x, y - 13, 0xFFF4D089, true);
        int rows = sourceRows();
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, x - 3, y - 3, w + 6, rows * 11 + 6);

        if(sources.isEmpty())
        {
            // Said plainly. A card with no set is one the shop cannot sell, and
            // a player hunting for it deserves to know that rather than to keep
            // looking.
            poseStack.text(font, "Not found in any pack", x + 2, y + 1, 0xFF6E7686, true);
            return;
        }
        for(int i = 0; i < rows && i + sourceScroll < sources.size(); i++)
        {
            CardSet set = sources.get(i + sourceScroll);
            String label = set.code + "   " + set.name;
            poseStack.text(font, font.plainSubstrByWidth(label, w - 4), x + 2,
                y + 1 + i * 11, 0xFFC2C9D6, true);
        }
        if(sources.size() > rows)
        {
            String more = (sourceScroll + rows) + " / " + sources.size();
            poseStack.text(font, more, x + w - font.width(more), y - 13, 0xFF6E7686, true);
        }
    }

    private void renderRelated(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        int x = width / 2 + pad();
        int y = sourcesTop();
        List<Properties> shown = shownRelated();
        poseStack.text(font, "Related  (" + shown.size() + ")", x, y - 13, 0xFFF4D089, true);
        if(shown.isEmpty())
        {
            poseStack.text(font, related.isEmpty()
                    ? "Nothing names this card"
                    : "None of those, under this filter",
                x, y + 1, 0xFF6E7686, true);
            return;
        }

        int cardW = relatedCardW();
        int cardH = relatedCardH();
        int columns = relatedColumns();
        int top = y;
        int rows = relatedRows();
        relatedScroll = Math.max(0, Math.min(relatedScroll, maxRelatedScroll()));

        int first = relatedScroll * columns;
        for(int slot = 0; slot < columns * rows && first + slot < shown.size(); slot++)
        {
            int i = first + slot;
            Properties other = shown.get(i);
            int cx = x + (slot % columns) * (cardW + 4);
            int cy = top + (slot / columns) * (cardH + 4);
            boolean hovered = mouseX >= cx && mouseX < cx + cardW
                && mouseY >= cy && mouseY < cy + cardH;
            if(hovered)
            {
                NineSlice.draw(poseStack, HubTextures.PANEL, cx - 3, cy - 3, cardW + 6, cardH + 6,
                    NineSlice.HOVER, 3, 0.9F);
            }
            DdBlitUtil.blit(poseStack,
                DuelTextures.card(other, (byte)0, DuelTextures.ICON_CARD_SIZE),
                cx, cy, cardW, cardH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
        }

        if(maxRelatedScroll() > 0)
        {
            String more = Math.min(shown.size(), (relatedScroll + rows) * columns)
                + " / " + shown.size();
            poseStack.text(font, more, width - pad() - 4 - font.width(more), y - 13,
                0xFF6E7686, true);
        }

        Properties hovered = relatedAt(mouseX, mouseY);
        if(hovered != null)
        {
            poseStack.setTooltipForNextFrame(font, Component.literal(hovered.getName()), mouseX, mouseY);
        }
    }

    /** The star, which is a button here rather than a mark. */
    /**
     * Where to buy this card, as a TCGplayer search.
     * <p>
     * A search rather than a product page, because the only identifier this
     * mod holds is the passcode and TCGplayer indexes by printing, not by
     * passcode — a search on the name lands on the card and its printings,
     * which is what somebody pricing it wants anyway.
     * <p>
     * The name is URL-encoded: real card names carry spaces, apostrophes,
     * ampersands and hyphens ("Harpie's Feather Duster", "D/D/D"), and any of
     * them unescaped makes a broken link rather than a wrong one.
     */
    private static java.net.URI tcgPlayerSearch(Properties card)
    {
        String name = card.getName() == null ? "" : card.getName();
        String query = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
        return java.net.URI.create(
            "https://www.tcgplayer.com/search/yugioh/product?productLineName=yugioh&q=" + query);
    }

    /** Bounded, the way every other measured layout here bounds its numbers. */
    private static int clamp(int min, int max, int value)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static class StarButton extends HubWidgets.TextureButton
    {
        private final java.util.function.BooleanSupplier lit;

        StarButton(int x, int y, int width, int height,
            java.util.function.BooleanSupplier lit, OnPress onPress)
        {
            super(x, y, width, height, Component.literal(""), onPress);
            this.lit = lit;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
            boolean on = lit.getAsBoolean();
            NineSlice.draw(poseStack, HubTextures.BUTTON, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE, 3);
            int mark = Math.min(getWidth(), getHeight()) - 5;
            // An unlit star was drawn faint by setting the shader colour before
            // the blit and putting it back after. The faintness is the blit's
            // own argument now, so there is nothing to put back.
            DdBlitUtil.fullBlit(poseStack, HubTextures.STAR,
                getX() + (getWidth() - mark) / 2, getY() + (getHeight() - mark) / 2,
                mark, mark, DdBlitUtil.alpha(on ? 1F : 0.35F));
        }
    }

    @Override
    public void onClose()
    {
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
}
