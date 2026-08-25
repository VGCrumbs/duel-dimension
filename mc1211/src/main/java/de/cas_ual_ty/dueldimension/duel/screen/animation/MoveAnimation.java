package de.cas_ual_ty.dueldimension.duel.screen.animation;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.duel.playfield.CardPosition;
import de.cas_ual_ty.dueldimension.duel.playfield.DuelCard;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import de.cas_ual_ty.dueldimension.duel.screen.widget.ZoneWidget;

public class MoveAnimation extends Animation
{
    public final ZoneOwner view;
    
    public final DuelCard duelCard;
    public final ZoneWidget sourceZone;
    public final ZoneWidget destinationZone;
    
    public final CardPosition sourcePosition;
    public final CardPosition destinationPosition;
    
    public int sourceX;
    public int sourceY;
    public int destX;
    public int destY;
    
    public MoveAnimation(ZoneOwner view, DuelCard duelCard, ZoneWidget sourceZone, ZoneWidget destinationZone, CardPosition sourcePosition, CardPosition destinationPosition)
    {
        super(ClientProxy.moveAnimationLength);
        
        this.view = view;
        
        this.duelCard = duelCard;
        this.sourceZone = sourceZone;
        this.destinationZone = destinationZone;
        this.sourcePosition = sourcePosition;
        this.destinationPosition = destinationPosition;
        
        sourceX = this.sourceZone.getAnimationSourceX();
        sourceY = this.sourceZone.getAnimationSourceY();
        destX = this.destinationZone.getAnimationDestX();
        destY = this.destinationZone.getAnimationDestY();
    }
    
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

        double relativeTickTime = (double) (tickTime + partialTicks) / maxTickTime;
        float relativePositionRotation;
        float relativeScale;
        
        // [1pi, 2pi]
        double cosTime1 = Math.PI * relativeTickTime + Math.PI;
        // [0, 1]
        relativePositionRotation = (float) ((Math.cos(cosTime1) + 1) * 0.5D);
        
        if(sourcePosition.isFaceUp != destinationPosition.isFaceUp)
        {
            // [0pi, 2pi]
            double cosTime2 = 2 * Math.PI * relativeTickTime;
            // [0, 1]
            relativeScale = (float) ((Math.cos(cosTime2) + 1) * 0.5D);
        }
        else
        {
            relativeScale = 1;
        }
        
        final int cardSize = 32;
        // whole pixels, because a blit takes int bounds now. Only the card's own size is
        // rounded -- where it is on its way to is a translate on the matrix below, so the
        // travel stays as smooth as it was
        int cardWidth = Math.round(relativeScale * cardSize);
        int cardHeight = cardSize;
        
        float posX = sourceX;
        float posY = sourceY;
        
        posX += (destX - sourceX) * relativePositionRotation;
        posY += (destY - sourceY) * relativePositionRotation;
        
        CardPosition cardPosition;
        CardSleevesType sleeves;
        
        if(tickTime >= maxTickTime / 2)
        {
            cardPosition = destinationPosition;
            sleeves = destinationZone.zone.getSleeves();
        }
        else
        {
            cardPosition = sourcePosition;
            sleeves = sourceZone.zone.getSleeves();
        }
        
        float sourceRotation = MoveAnimation.getRotationForPositionAndView(view == sourceZone.zone.getOwner() || !sourceZone.zone.hasOwner(), sourcePosition);
        float targetRotation = MoveAnimation.getRotationForPositionAndView(view == destinationZone.zone.getOwner() || !destinationZone.zone.hasOwner(), destinationPosition);
        
        if(Math.abs(targetRotation - sourceRotation) > Math.abs(targetRotation - sourceRotation + 360))
        {
            sourceRotation -= 360;
        }
        else if(Math.abs(targetRotation - sourceRotation) > Math.abs(targetRotation - sourceRotation - 360))
        {
            targetRotation -= 360;
        }
        
        float rotation = sourceRotation + (targetRotation - sourceRotation) * relativePositionRotation;
        
        while(rotation < 0)
        {
            rotation += 360;
        }
        
        while(rotation >= 360)
        {
            rotation -= 360;
        }
        
        ms.pose().pushMatrix();
        
        ms.pose().translate(posX, posY);
        // getRotationForPositionAndView answers in degrees, which is what the Quaternion
        // this replaces was told to expect; the GUI matrix only takes radians
        ms.pose().rotate((float) Math.toRadians(rotation));
        
        // we always render the card position straight and manually rotate it, thats why we use fullBlit here
        CardRenderUtil.renderDuelCardAdvanced(ms, sleeves, mouseX, mouseY, -cardWidth / 2, -cardHeight / 2, cardWidth, cardHeight, duelCard, cardPosition, DdBlitUtil::fullBlit);
        
        ms.pose().popMatrix();
    }
    
    public static float getRotationForPositionAndView(boolean isOpponentView, CardPosition position)
    {
        if(position.isStraight)
        {
            if(!isOpponentView)
            {
                return 180;
            }
            else
            {
                return 0;
            }
        }
        else
        {
            if(!isOpponentView)
            {
                return 90;
            }
            else
            {
                return 270;
            }
        }
    }
}
