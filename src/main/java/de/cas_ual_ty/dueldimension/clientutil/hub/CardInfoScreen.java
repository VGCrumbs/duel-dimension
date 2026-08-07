package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
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
                    minecraft.setScreen(root());
                }
            }));
        }

        // The star is the same mark the grids use, so toggling it here and
        // toggling it from the menu are visibly the same act.
        addRenderableWidget(new StarButton(width - pad() - 22, pad(), 22, 18,
            () -> EditorState.isFavourite((int)card.getId()),
            pressed -> EditorState.toggleFavourite((int)card.getId())));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
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
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        // A related card opens its own page, with this one as the way back, so
        // following a chain of cards can be walked back one at a time.
        Properties clicked = relatedAt(mouseX, mouseY);
        if(clicked != null && minecraft != null)
        {
            minecraft.setScreen(new CardInfoScreen(this, clicked));
            return true;
        }
        return false;
    }

    private int artW()
    {
        return Math.min(150, width / 5);
    }

    private int artH()
    {
        return Math.round(artW() / DuelTextures.CARD_ASPECT);
    }

    /**
     * Where the two lists begin: below the art AND below its heading, with room
     * for the heading itself. The heading used to be drawn twelve pixels above
     * this line, which put it back over the bottom of the card.
     */
    private int sourcesTop()
    {
        return pad() + 24 + artH() + 22;
    }

    private int sourceRows()
    {
        return Math.max(1, (height - sourcesTop() - pad() - 14) / 11);
    }

    private int textRows()
    {
        return Math.max(1, (artH() - 62) / 10);
    }

    private int relatedCardW()
    {
        return 44;
    }

    private int relatedColumns()
    {
        int room = width - width / 2 - pad() * 2;
        return Math.max(1, room / (relatedCardW() + 4));
    }

    private int relatedRows()
    {
        int cardH = Math.round(relatedCardW() / DuelTextures.CARD_ASPECT);
        return Math.max(1, (height - sourcesTop() - pad()) / (cardH + 4));
    }

    private int maxRelatedScroll()
    {
        int perPage = relatedColumns() * relatedRows();
        int rows = (related.size() + relatedColumns() - 1) / relatedColumns();
        return Math.max(0, rows - relatedRows());
    }

    private Properties relatedAt(double mouseX, double mouseY)
    {
        int cardW = relatedCardW();
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
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
        int index = (row + relatedScroll) * columns + column;
        return index >= 0 && index < related.size() ? related.get(index) : null;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        if(card == null)
        {
            super.render(poseStack, mouseX, mouseY, partialTick);
            return;
        }
        NineSlice.draw(poseStack, HubTextures.PANEL, pad() - 4, pad() - 4,
            width - (pad() - 4) * 2, height - (pad() - 4) * 2);

        int artX = pad() + 4;
        int artY = pad() + 24;
        ScreenUtil.white();
        DuelTextures.bindSmooth(DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        DdBlitUtil.blit(poseStack, artX, artY, artW(), artH(),
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);

        int detailX = artX + artW() + 12;
        int detailW = width - detailX - pad() - 4;
        renderOverview(poseStack, detailX, artY, detailW);

        renderSources(poseStack);
        renderRelated(poseStack, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    /**
     * Name, type line and stats, taken from the card model's own description
     * rather than composed here, so this page says what every tooltip says.
     */
    private void renderOverview(PoseStack poseStack, int x, int y, int usableW)
    {
        List<Component> information = new ArrayList<>();
        card.addHeader(information);

        int line = y;
        for(int i = 0; i < information.size(); i++)
        {
            String text = information.get(i).getString();
            if(text.isBlank())
            {
                continue;
            }
            font.drawShadow(poseStack, text, x, line, i == 0 ? 0xFFF4D089 : 0xFFC2C9D6);
            line += i == 0 ? 14 : 11;
        }


        // The effect, in a recessed box so it reads as the card's own words.
        int boxY = line;
        int boxH = Math.max(30, artH() + pad() + 24 - boxY + 4);
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, x - 3, boxY - 3, usableW + 6, boxH);
        textLines = font.split(Component.literal(card.getText() == null ? "" : card.getText()),
            usableW - 6);
        int rows = Math.max(1, (boxH - 8) / 10);
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, textLines.size() - rows)));
        for(int i = 0; i < rows && i + textScroll < textLines.size(); i++)
        {
            font.draw(poseStack, textLines.get(i + textScroll), x, boxY + i * 10F, 0xFFC2C9D6);
        }
        if(textLines.size() > rows)
        {
            String more = "scroll  " + Math.min(textLines.size(), textScroll + rows)
                + " / " + textLines.size();
            font.drawShadow(poseStack, more, x + usableW - font.width(more), boxY + boxH - 12,
                0xFF6E7686);
        }
    }

    private void renderSources(PoseStack poseStack)
    {
        int x = pad() + 4;
        int y = sourcesTop();
        int w = width / 2 - x - pad();
        font.drawShadow(poseStack, "Obtained from  (" + sources.size() + ")", x, y - 13, 0xFFF4D089);
        int rows = sourceRows();
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, x - 3, y - 3, w + 6, rows * 11 + 6);

        if(sources.isEmpty())
        {
            // Said plainly. A card with no set is one the shop cannot sell, and
            // a player hunting for it deserves to know that rather than to keep
            // looking.
            font.drawShadow(poseStack, "Not found in any pack", x + 2, y + 1, 0xFF6E7686);
            return;
        }
        for(int i = 0; i < rows && i + sourceScroll < sources.size(); i++)
        {
            CardSet set = sources.get(i + sourceScroll);
            String label = set.code + "   " + set.name;
            font.drawShadow(poseStack, font.plainSubstrByWidth(label, w - 4), x + 2,
                y + 1 + i * 11, 0xFFC2C9D6);
        }
        if(sources.size() > rows)
        {
            String more = (sourceScroll + rows) + " / " + sources.size();
            font.drawShadow(poseStack, more, x + w - font.width(more), y + rows * 11 - 8, 0xFF6E7686);
        }
    }

    private void renderRelated(PoseStack poseStack, int mouseX, int mouseY)
    {
        int x = width / 2 + pad();
        int y = sourcesTop();
        font.drawShadow(poseStack, "Related cards  (" + related.size() + ")", x, y - 13, 0xFFF4D089);
        if(related.isEmpty())
        {
            font.drawShadow(poseStack, "Nothing names this card", x, y + 1, 0xFF6E7686);
            return;
        }

        int cardW = relatedCardW();
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
        int columns = relatedColumns();
        int top = y;
        int rows = relatedRows();
        relatedScroll = Math.max(0, Math.min(relatedScroll, maxRelatedScroll()));

        int first = relatedScroll * columns;
        for(int slot = 0; slot < columns * rows && first + slot < related.size(); slot++)
        {
            int i = first + slot;
            Properties other = related.get(i);
            int cx = x + (slot % columns) * (cardW + 4);
            int cy = top + (slot / columns) * (cardH + 4);
            boolean hovered = mouseX >= cx && mouseX < cx + cardW
                && mouseY >= cy && mouseY < cy + cardH;
            if(hovered)
            {
                NineSlice.draw(poseStack, HubTextures.PANEL, cx - 3, cy - 3, cardW + 6, cardH + 6,
                    NineSlice.HOVER, 3, 0.9F);
            }
            ScreenUtil.white();
            DuelTextures.bindSmooth(DuelTextures.card(other, (byte)0, DuelTextures.ICON_CARD_SIZE));
            DdBlitUtil.blit(poseStack, cx, cy, cardW, cardH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
                DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);
        }

        if(maxRelatedScroll() > 0)
        {
            String more = "scroll  " + Math.min(related.size(), (relatedScroll + rows) * columns)
                + " / " + related.size();
            font.drawShadow(poseStack, more, x, y - 12 + (height - y) - 10, 0xFF6E7686);
        }

        Properties hovered = relatedAt(mouseX, mouseY);
        if(hovered != null)
        {
            renderTooltip(poseStack, Component.literal(hovered.getName()), mouseX, mouseY);
        }
    }

    /** The star, which is a button here rather than a mark. */
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
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            boolean on = lit.getAsBoolean();
            NineSlice.draw(poseStack, HubTextures.BUTTON, x, y, width, height,
                isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE, 3);
            int mark = Math.min(width, height) - 5;
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1F, 1F, 1F, on ? 1F : 0.35F);
            RenderSystem.setShaderTexture(0, HubTextures.STAR);
            DdBlitUtil.blit(poseStack, x + (width - mark) / 2, y + (height - mark) / 2,
                mark, mark, 0, 0, 1, 1, 1, 1);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    @Override
    public void onClose()
    {
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
}
