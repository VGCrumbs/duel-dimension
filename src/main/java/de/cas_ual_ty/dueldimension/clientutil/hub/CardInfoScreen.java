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
     * Cards that go with this one.
     * <p>
     * Two relationships, in the order a player cares about them: cards whose
     * NAME contains this one's — which is what an archetype looks like, Dark
     * Magician to Dark Magician Girl — and then cards whose TEXT names this
     * one, which is what support looks like.
     * <p>
     * Both are substring searches over the whole database rather than anything
     * cleverer. The card data carries no archetype field, so the name is the
     * only honest signal available; inventing a list of archetypes here would
     * be a guess that went stale with the next set.
     */
    private static List<Properties> findRelated(Properties card)
    {
        String name = card.getName();
        if(name == null || name.isBlank())
        {
            return List.of();
        }
        String needle = name.toLowerCase(Locale.ROOT);
        Set<Properties> byName = new LinkedHashSet<>();
        Set<Properties> byText = new LinkedHashSet<>();

        for(Properties other : DdDatabase.PROPERTIES_LIST)
        {
            if(other == null || other.getId() == card.getId() || other.getId() <= 0
                || other.getIllegal())
            {
                continue;
            }
            String otherName = other.getName();
            if(otherName != null && otherName.toLowerCase(Locale.ROOT).contains(needle))
            {
                byName.add(other);
                continue;
            }
            String text = other.getText();
            if(text != null && text.toLowerCase(Locale.ROOT).contains(needle))
            {
                byText.add(other);
            }
        }

        List<Properties> found = new ArrayList<>(byName);
        for(Properties support : byText)
        {
            if(found.size() >= RELATED_LIMIT)
            {
                break;
            }
            found.add(support);
        }
        return List.copyOf(found.size() > RELATED_LIMIT ? found.subList(0, RELATED_LIMIT) : found);
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

    private int sourcesTop()
    {
        return pad() + 24 + artH() + 10;
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

    private Properties relatedAt(double mouseX, double mouseY)
    {
        int cardW = relatedCardW();
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
        int left = width / 2 + pad();
        int top = sourcesTop() + 14;
        int column = (int)((mouseX - left) / (cardW + 4));
        int row = (int)((mouseY - top) / (cardH + 4));
        if(mouseX < left || mouseY < top || column < 0 || column >= relatedColumns() || row < 0)
        {
            return null;
        }
        int index = row * relatedColumns() + column;
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

        String stats = statsLine();
        if(!stats.isEmpty())
        {
            font.drawShadow(poseStack, stats, x, line, 0xFF9FD4FF);
            line += 14;
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

    /** ATK, DEF, level and attribute, for the cards that have them. */
    private String statsLine()
    {
        if(!(card instanceof de.cas_ual_ty.dueldimension.card.properties.MonsterProperties monster))
        {
            return "";
        }
        StringBuilder line = new StringBuilder();
        if(monster.getAttribute() != null)
        {
            line.append(monster.getAttribute());
        }
        if(card instanceof de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties levelled)
        {
            line.append(line.length() > 0 ? "   " : "").append("Level ").append(levelled.level);
        }
        line.append(line.length() > 0 ? "   " : "").append("ATK ").append(monster.getAtk());
        if(card instanceof de.cas_ual_ty.dueldimension.card.properties.DefMonsterProperties def)
        {
            line.append(" / DEF ").append(def.def);
        }
        return line.toString();
    }

    private void renderSources(PoseStack poseStack)
    {
        int x = pad() + 4;
        int y = sourcesTop();
        int w = width / 2 - x - pad();
        font.drawShadow(poseStack, "Obtained from  (" + sources.size() + ")", x, y - 12, 0xFFF4D089);
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
        font.drawShadow(poseStack, "Related cards  (" + related.size() + ")", x, y - 12, 0xFFF4D089);
        if(related.isEmpty())
        {
            font.drawShadow(poseStack, "Nothing names this card", x, y + 1, 0xFF6E7686);
            return;
        }

        int cardW = relatedCardW();
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
        int columns = relatedColumns();
        int top = y + 14;
        int rows = Math.max(1, (height - top - pad()) / (cardH + 4));

        for(int i = 0; i < related.size() && i < columns * rows; i++)
        {
            Properties other = related.get(i);
            int cx = x + (i % columns) * (cardW + 4);
            int cy = top + (i / columns) * (cardH + 4);
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
