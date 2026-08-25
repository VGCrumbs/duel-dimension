package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;
import de.cas_ual_ty.dueldimension.duel.DuelManager;
import de.cas_ual_ty.dueldimension.duel.playfield.DuelCard;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneInteraction;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import de.cas_ual_ty.dueldimension.duel.screen.DuelScreenDueling;
import de.cas_ual_ty.dueldimension.duel.screen.IDuelScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class ZoneWidget extends Button
{
    public final Zone zone;
    public final IDuelScreenContext context;
    public boolean isFlipped;
    public DuelCard hoverCard;

    /** Asked, while hovered, what to say. See {@link #extractTooltip}. */
    private final ITooltip onTooltip;

    public ZoneWidget(Zone zone, IDuelScreenContext context, int width, int height, Component title, Consumer<ZoneWidget> onPress, ITooltip onTooltip)
    {
        super(0, 0, width, height, title, (w) -> onPress.accept((ZoneWidget) w), DEFAULT_NARRATION);
        this.zone = zone;
        this.context = context;
        this.onTooltip = onTooltip;
        shift();
        hoverCard = null;
    }

    /**
     * What {@code Button.renderToolTip} did on Forge: hand the callback the
     * moment of drawing.
     * <p>
     * Vanilla's last constructor argument is a narration builder now, not a
     * tooltip, and its replacement ({@code setTooltip}) fixes the text when the
     * widget is built. A zone's text is not fixed -- it counts the cards in the
     * zone and its counters as they stand -- so the callback survives and is
     * asked here instead, from the same place in the frame Forge asked it.
     */
    protected void extractTooltip(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        if(onTooltip != null)
        {
            onTooltip.onTooltip(this, ms, mouseX, mouseY);
        }
    }

    /**
     * The widget's fade, as the colour a draw is tinted with.
     * <p>
     * Forge said it once, {@code RenderSystem.setShaderColor(1F, 1F, 1F, alpha)},
     * and everything drawn afterwards -- here and inside every helper called from
     * here -- came out fainter for it. There is no global shader colour now: a
     * tint is an argument, so the fade has to reach each draw by hand.
     */
    protected int fadeTint()
    {
        return DdBlitUtil.alpha(alpha);
    }

    protected void shift()
    {
        setX(getX() - width / 2);
        setY(getY() - height / 2);
    }
    
    protected void unshift()
    {
        setX(getX() + width / 2);
        setY(getY() + height / 2);
    }
    
    public ZoneWidget flip(int guiWidth, int guiHeight)
    {
        guiWidth /= 2;
        guiHeight /= 2;
        
        unshift();
        
        setX(getX() - guiWidth);
        setY(getY() - guiHeight);
        
        setX(-getX());
        setY(-getY());
        
        setX(getX() + guiWidth);
        setY(getY() + guiHeight);
        
        shift();
        
        isFlipped = !isFlipped;
        
        return this;
    }
    
    public ZoneWidget setPositionRelative(int x, int y, int guiWidth, int guiHeight)
    {
        setX(x + guiWidth / 2);
        setY(y + guiHeight / 2);
        
        shift();
        
        isFlipped = false;
        
        return this;
    }
    
    public ZoneWidget setPositionRelativeFlipped(int x, int y, int guiWidth, int guiHeight)
    {
        setX(guiWidth / 2 - x);
        setY(guiHeight / 2 - y);
        
        shift();
        
        isFlipped = true;
        
        return this;
    }
    
    @Override
    protected void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font Font = minecraft.font;

        // PORT-NOTE: the fade this method opened with cannot reach the rectangles.
        // fadeTint() is the value they want, but none of them takes a tint:
        // ScreenUtil.renderHoverRect/renderDisabledRect fill a hardcoded ARGB, and
        // DuelScreenDueling.render*SelectedRect passes fixed floats to
        // ScreenUtil.drawLineRect. Giving those four a tint parameter is a change
        // to files outside this one. Nothing in the mod calls setAlpha today, so
        // alpha is 1F and the difference does not show yet.

        renderZoneSelectRect(ms, zone, getX(), getY(), width, height);

        hoverCard = renderCards(ms, mouseX, mouseY);

        if(zone.type.getCanHaveCounters() && zone.getCounters() > 0)
        {
            // The counter used to be pushed towards the viewer so the card under
            // it could not swallow it. The GUI matrix is 2D and has no z to push
            // into -- and needs none, because what is described later draws later
            // and the cards are described above.
            ms.centeredText(Font, Component.literal("(" + zone.getCounters() + ")"),
                    getX() + width / 2, getY() + height / 2 - Font.lineHeight / 2,
                    16777215 | Mth.ceil(alpha * 255.0F) << 24);
        }

        if(active)
        {
            if(isHoveredOrFocused())
            {
                if(zone.getCardsAmount() == 0)
                {
                    ScreenUtil.renderHoverRect(ms, getX(), getY(), width, height);
                }

                extractTooltip(ms, mouseX, mouseY);
            }
        }
        else
        {
            ScreenUtil.renderDisabledRect(ms, getX(), getY(), width, height);
        }
    }
    
    public void renderZoneSelectRect(GuiGraphicsExtractor ms, Zone zone, float x, float y, float width, float height)
    {
        if(context.getClickedZone() == zone && context.getClickedCard() == null)
        {
            if(context.getOpponentClickedZone() == zone && context.getOpponentClickedCard() == null)
            {
                DuelScreenDueling.renderBothSelectedRect(ms, x, y, width, height);
            }
            else
            {
                DuelScreenDueling.renderSelectedRect(ms, x, y, width, height);
            }
        }
        else
        {
            if(context.getOpponentClickedZone() == zone && context.getOpponentClickedCard() == null)
            {
                DuelScreenDueling.renderEnemySelectedRect(ms, x, y, width, height);
            }
            else
            {
                //
            }
        }
    }
    
    public void renderCardSelectRect(GuiGraphicsExtractor ms, DuelCard card, float x, float y, float width, float height)
    {
        if(context.getClickedCard() == card)
        {
            if(context.getOpponentClickedCard() == card)
            {
                DuelScreenDueling.renderBothSelectedRect(ms, x, y, width, height);
            }
            else
            {
                DuelScreenDueling.renderSelectedRect(ms, x, y, width, height);
            }
        }
        else
        {
            if(context.getOpponentClickedCard() == card)
            {
                DuelScreenDueling.renderEnemySelectedRect(ms, x, y, width, height);
            }
            else
            {
                //
            }
        }
    }
    
    @Nullable
    public DuelCard renderCards(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        if(zone.getCardsAmount() <= 0)
        {
            return null;
        }
        
        boolean isOwner = zone.getOwner() == context.getZoneOwner();
        DuelCard c = zone.getTopCard();
        
        if(c != null)
        {
            if(drawCard(ms, c, getX(), getY(), width, height, mouseX, mouseY, getX(), getY(), width, height))
            {
                if(c.getCardPosition().isFaceUp || (isOwner && !zone.getType().getIsSecret()))
                {
                    context.renderCardInfo(ms, c);
                }
                
                if(active)
                {
                    ScreenUtil.renderHoverRect(ms, getX(), getY(), width, height);
                    return c;
                }
            }
        }
        
        if(context.getClickedZone() == zone)
        {
            DuelScreenDueling.renderSelectedRect(ms, getX(), getY(), width, height);
        }
        
        return null;
    }
    
    protected boolean drawCard(GuiGraphicsExtractor ms, DuelCard duelCard, int renderX, int renderY, int renderWidth, int renderHeight, int mouseX, int mouseY, int cardsWidth, int cardsHeight)
    {
        int offset = cardsHeight - cardsWidth;
        
        int hoverX = renderX;
        int hoverY = renderY;
        int hoverWidth;
        int hoverHeight;
        
        if(duelCard.getCardPosition().isStraight)
        {
            hoverX += offset;
            hoverWidth = cardsWidth;
            hoverHeight = cardsHeight;
        }
        else
        {
            hoverY += offset;
            hoverWidth = cardsHeight;
            hoverHeight = cardsWidth;
        }
        
        return drawCard(ms, duelCard, renderX, renderY, renderWidth, renderHeight, mouseX, mouseY, hoverX, hoverY, hoverWidth, hoverHeight);
    }
    
    protected boolean drawCard(GuiGraphicsExtractor ms, DuelCard duelCard, float renderX, float renderY, float renderWidth, float renderHeight, int mouseX, int mouseY, float hoverX, float hoverY, float hoverWidth, float hoverHeight)
    {
        boolean isOwner = zone.getOwner() == context.getZoneOwner();
        boolean faceUp = zone.getType().getShowFaceDownCardsToOwner() && isOwner;
        boolean isOpponentView = zone.getOwner() != context.getView();
        
        renderCardSelectRect(ms, duelCard, hoverX, hoverY, hoverWidth, hoverHeight);

        // A blit is measured in whole pixels now, so the rectangle is rounded from
        // its own two edges rather than from a position and a width -- rounding
        // those separately is what puts a gap between two cards in a row. Every
        // caller works in ints anyway; this only decides what happens if one stops.
        int cardX = Math.round(renderX);
        int cardY = Math.round(renderY);
        int cardWidth = Math.round(renderX + renderWidth) - cardX;
        int cardHeight = Math.round(renderY + renderHeight) - cardY;

        // PORT-NOTE: the fade does not reach the card either. CardRenderUtil blits
        // with DdBlitUtil.NO_TINT throughout and its render methods take no tint of
        // their own, so a fading zone draws its cards at full strength. fadeTint()
        // is what they want passing, once they can take it.
        if(!isOpponentView)
        {
            CardRenderUtil.renderDuelCardCentered(ms, zone.getSleeves(), mouseX, mouseY, cardX, cardY, cardWidth, cardHeight, duelCard, faceUp);
        }
        else
        {
            CardRenderUtil.renderDuelCardReversedCentered(ms, zone.getSleeves(), mouseX, mouseY, cardX, cardY, cardWidth, cardHeight, duelCard, faceUp);
        }

        if(isHoveredOrFocused() && mouseX >= hoverX && mouseX < hoverX + hoverWidth && mouseY >= hoverY && mouseY < hoverY + hoverHeight)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
    
    public void addInteractionWidgets(ZoneOwner player, Zone interactor, DuelCard interactorCard, DuelManager m, List<InteractionWidget> list, Consumer<InteractionWidget> onPress, ITooltip onTooltip, boolean isAdvanced)
    {
        List<ZoneInteraction> interactions;
        
        if(!isAdvanced)
        {
            interactions = m.getActionsFor(player, interactor, interactorCard, zone);
        }
        else
        {
            interactions = m.getAdvancedActionsFor(player, interactor, interactorCard, zone);
        }
        
        if(interactions.size() == 0)
        {
            return;
        }
        
        if(interactions.size() == 1)
        {
            list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width, height, onPress, onTooltip));
        }
        else if(interactions.size() == 2)
        {
            if(width <= height)
            {
                // Split them horizontally (1 action on top, 1 on bottom)
                list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width, height / 2, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(1), context, getX(), getY() + height / 2, width, height / 2, onPress, onTooltip));
            }
            else
            {
                // Split them vertically (1 left, 1 right)
                list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width / 2, height, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(1), context, getX() + width / 2, getY(), width / 2, height, onPress, onTooltip));
            }
        }
        else if(interactions.size() == 3)
        {
            if(width == height)
            {
                // 1 on top half, 1 bottom left, 1 bottom right
                list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width, height / 2, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(1), context, getX(), getY() + height / 2, width / 2, height / 2, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(2), context, getX() + width / 2, getY() + height / 2, width / 2, height / 2, onPress, onTooltip));
            }
            else if(width < height)
            {
                // Horizontally split
                list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width, height / 3, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(1), context, getX(), getY() + height / 3, width, height / 3, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(2), context, getX(), getY() + height * 2 / 3, width, height / 3, onPress, onTooltip));
            }
            else //if(this.width > this.height)
            {
                // Vertically split
                list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width / 3, height, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(1), context, getX() + width / 3, getY(), width / 3, height, onPress, onTooltip));
                list.add(new InteractionWidget(interactions.get(2), context, getX() + width * 2 / 3, getY(), width / 3, height, onPress, onTooltip));
            }
        }
        else if(interactions.size() == 4 && width == height)
        {
            // 1 on top left, 1 top right, 1 bottom left, 1 bottom right
            list.add(new InteractionWidget(interactions.get(0), context, getX(), getY(), width / 2, height / 2, onPress, onTooltip));
            list.add(new InteractionWidget(interactions.get(1), context, getX() + width / 2, getY(), width / 2, height / 2, onPress, onTooltip));
            list.add(new InteractionWidget(interactions.get(2), context, getX(), getY() + height / 2, width / 2, height / 2, onPress, onTooltip));
            list.add(new InteractionWidget(interactions.get(3), context, getX() + width / 2, getY() + height / 2, width / 2, height / 2, onPress, onTooltip));
        }
        else
        {
            if(width < height)
            {
                // Horizontally split
                for(int i = 0; i < interactions.size(); ++i)
                {
                    list.add(new InteractionWidget(interactions.get(i), context, getX(), getY() + height * i / interactions.size(), width, height / interactions.size(), onPress, onTooltip));
                }
            }
            else //if(this.width > this.height)
            {
                // Vertically split
                for(int i = 0; i < interactions.size(); ++i)
                {
                    list.add(new InteractionWidget(interactions.get(i), context, getX() + width * i / interactions.size(), getY(), width / interactions.size(), height, onPress, onTooltip));
                }
            }
        }
    }
    
    public int getAnimationSourceX()
    {
        return getX() + width / 2;
    }
    
    public int getAnimationSourceY()
    {
        return getY() + height / 2;
    }
    
    public int getAnimationDestX()
    {
        return getX() + width / 2;
    }
    
    public int getAnimationDestY()
    {
        return getY() + height / 2;
    }
    
    public Component getTranslation()
    {
        return Component.translatable(zone.getType().getRegistryName().getNamespace() + ".zone." + zone.getType().getRegistryName().getPath());
    }
    
    public boolean openAdvancedZoneView()
    {
        return false;
    }
}