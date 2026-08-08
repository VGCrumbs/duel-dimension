package de.cas_ual_ty.dueldimension.duel.screen.animation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;


public class TextAnimation extends Animation
{
    public Component message;
    public float centerPosX;
    public float centerPosY;
    
    public TextAnimation(Component message, float centerPosX, float centerPosY)
    {
        super(ClientProxy.announcementAnimationLength);
        
        this.message = message;
        this.centerPosX = centerPosX;
        this.centerPosY = centerPosY;
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        Font f = ClientProxy.getMinecraft().font;
        
        double relativeTickTime = (tickTime + partialTicks) / maxTickTime;
        
        // [0, 1/2pi]
        double cosTime1 = 0.5D * Math.PI * relativeTickTime;
        // [0, 1]
        float alpha = (float) (Math.cos(cosTime1));
        
        ms.pose().pushMatrix();
        
        ms.pose().translate(centerPosX, centerPosY - f.lineHeight / 2);
        
        int j = 16777215; //See TextWidget
        ms.centeredText(f, message, 0, 0, j | Mth.ceil(alpha * 255.0F) << 24);
        
        
        ms.pose().popMatrix();
    }
}
