package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.duel.playfield.DuelCard;
import de.cas_ual_ty.dueldimension.duel.screen.DuelScreenDueling;
import de.cas_ual_ty.dueldimension.duel.screen.IDuelScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class ViewCardStackWidget extends Button
{
    public final IDuelScreenContext context;
    public DuelCard hoverCard;
    protected int cardsTextureSize;
    protected int rows;
    protected int columns;
    protected int currentRow;
    protected List<DuelCard> cards;
    protected boolean forceFaceUp;

    /** Forge's {@code Button} held this; the constructor argument is unchanged. */
    protected final ITooltip onTooltip;

    public ViewCardStackWidget(IDuelScreenContext context, int x, int y, int width, int height, Component title, Consumer<ViewCardStackWidget> onPress, ITooltip onTooltip)
    {
        super(x, y, width, height, title, (button) -> onPress.accept((ViewCardStackWidget) button), DEFAULT_NARRATION);
        this.onTooltip = onTooltip;
        this.context = context;
        hoverCard = null;
        rows = 0;
        columns = 0;
        currentRow = 0;
        deactivate();
    }
    
    public ViewCardStackWidget setRowsAndColumns(int cardsTextureSize, int rows, int columns)
    {
        this.cardsTextureSize = cardsTextureSize;
        this.rows = Math.max(1, rows);
        this.columns = Math.max(1, columns);
        return this;
    }
    
    public void activate(List<DuelCard> cards, boolean forceFaceUp)
    {
        active = true;
        visible = true;
        currentRow = 0;
        this.cards = cards;
        this.forceFaceUp = forceFaceUp;
    }
    
    public void forceFaceUp()
    {
        forceFaceUp = true;
    }
    
    public void deactivate()
    {
        cards = null;
        visible = false;
        active = false;
    }
    
    public int getCurrentRow()
    {
        return currentRow;
    }
    
    public int getMaxRows()
    {
        if(cards != null && columns > 0)
        {
            return Math.max(0, Mth.ceil(cards.size() / (float) columns) - rows);
        }
        else
        {
            return 0;
        }
    }
    
    public void decreaseCurrentRow()
    {
        currentRow = Math.max(0, currentRow - 1);
    }
    
    public void increaseCurrentRow()
    {
        currentRow = Math.min(getMaxRows(), currentRow + 1);
    }
    
    public boolean getForceFaceUp()
    {
        return forceFaceUp;
    }
    
    public List<DuelCard> getCards()
    {
        return cards;
    }
    
    /** The fade-in, as the tint the old {@code setShaderColor} amounted to. */
    protected int fadeTint()
    {
        return DdBlitUtil.alpha(alpha);
    }

    /**
     * Forge's {@code AbstractWidget.getFGColor}, which is a Forge patch and has no
     * counterpart here. It is white unless the widget is inactive, and this one is
     * only ever drawn while active — {@link #deactivate()} clears {@code visible}
     * along with {@code active}.
     */
    protected int getFGColor()
    {
        return 16777215;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;

        if(onTooltip != null && isHovered())
        {
            onTooltip.onTooltip(this, ms, mouseX, mouseY);
        }

        // PORT-NOTE: the fade this widget's alpha describes now reaches the label
        // only. Forge set a shader colour of (1, 1, 1, alpha) here and everything
        // drawn afterwards inherited it, including the cards; the draws below go
        // out to CardRenderUtil, DuelScreenDueling and ScreenUtil, none of which
        // take a tint, so a partly faded stack draws its cards at full opacity.
        // fadeTint() is the argument those calls want once they grow one. Nothing
        // calls setAlpha in this mod today, so alpha is always 1 and the loss does
        // not show.

        if(!cards.isEmpty())
        {
            hoverCard = renderCards(ms, mouseX, mouseY);
        }
        else
        {
            hoverCard = null;
        }

        int j = getFGColor();
        ms.centeredText(fontrenderer, getMessage(), getX(), getY(), (j & 0x00FFFFFF) | (fadeTint() & 0xFF000000));
    }

    @Nullable
    public DuelCard renderCards(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        DuelCard hoveredCard = null;
        int hoverX = 0, hoverY = 0;
        
        int index = currentRow * columns;
        int x, y;
        DuelCard c;
        
        for(int i = 0; i < rows; ++i)
        {
            for(int j = 0; j < columns && index < cards.size(); ++j)
            {
                x = getX() + j * cardsTextureSize;
                y = getY() + i * cardsTextureSize;
                
                c = cards.get(index++);
                
                if(drawCard(ms, c, x, y, cardsTextureSize, cardsTextureSize, mouseX, mouseY))
                {
                    hoverX = x;
                    hoverY = y;
                    hoveredCard = c;
                }
            }
        }
        
        if(hoveredCard != null)
        {
            if(hoveredCard.getCardPosition().isFaceUp || forceFaceUp || (context.getClickedZone() != null && context.getZoneOwner() == context.getClickedZone().getOwner() && !context.getClickedZone().getType().getIsSecret()))
            {
                context.renderCardInfo(ms, hoveredCard);
            }
            
            ScreenUtil.renderHoverRect(ms, hoverX, hoverY, cardsTextureSize, cardsTextureSize);
        }
        
        if(!active)
        {
            return null;
        }
        else
        {
            return hoveredCard;
        }
    }
    
    protected boolean drawCard(GuiGraphicsExtractor ms, DuelCard duelCard, int renderX, int renderY, int renderWidth, int renderHeight, int mouseX, int mouseY)
    {
        if(context.getClickedCard() == duelCard)
        {
            if(context.getOpponentClickedCard() == duelCard)
            {
                DuelScreenDueling.renderBothSelectedRect(ms, renderX, renderY, renderWidth, renderHeight);
            }
            else
            {
                DuelScreenDueling.renderSelectedRect(ms, renderX, renderY, renderWidth, renderHeight);
            }
        }
        else
        {
            if(context.getOpponentClickedCard() == duelCard)
            {
                DuelScreenDueling.renderEnemySelectedRect(ms, renderX, renderY, renderWidth, renderHeight);
            }
            else
            {
                //
            }
        }
        
        CardRenderUtil.renderDuelCardCentered(ms, context.getClickedZone() != null ? context.getClickedZone().getSleeves() : CardSleevesType.CARD_BACK, mouseX, mouseY, renderX, renderY, renderWidth, renderHeight, duelCard, forceFaceUp);
        
        return isHoveredOrFocused() && mouseX >= renderX && mouseX < renderX + renderWidth && mouseY >= renderY && mouseY < renderY + renderHeight;
    }
}