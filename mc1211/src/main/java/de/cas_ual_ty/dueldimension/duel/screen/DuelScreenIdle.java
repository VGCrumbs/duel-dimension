package de.cas_ual_ty.dueldimension.duel.screen;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.widget.ReadyCheckboxWidget;
import de.cas_ual_ty.dueldimension.duel.DuelContainer;
import de.cas_ual_ty.dueldimension.duel.PlayerRole;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessages;
import de.cas_ual_ty.dueldimension.duel.screen.widget.RoleButtonWidget;
import de.cas_ual_ty.dueldimension.duel.screen.widget.RoleOccupantsWidget;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DuelScreenIdle<E extends DuelContainer> extends DuelContainerScreen<E>
{
    protected AbstractButton player1Button;
    protected AbstractButton player2Button;
    protected AbstractButton spectatorButton;
    
    public DuelScreenIdle(E screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn);
    }
    
    @Override
    protected void init()
    {
        super.init();
        
        int x = width / 2;
        int y = height / 2;
        
        //        this.initChat(width, height, y, margin - 4*2, 3 * 32);
        initDefaultChat(width, height);
        
        addRenderableWidget(player1Button = new RoleButtonWidget(x - 100, y - 40, 100, 20, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.player_1"), this::roleButtonClicked, () -> getDuelManager().player1 == null && getPlayerRole() != PlayerRole.PLAYER1, PlayerRole.PLAYER1));
        addRenderableWidget(player2Button = new RoleButtonWidget(x - 100, y - 10, 100, 20, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.player_2"), this::roleButtonClicked, () -> getDuelManager().player2 == null && getPlayerRole() != PlayerRole.PLAYER2, PlayerRole.PLAYER2));
        addRenderableWidget(spectatorButton = new RoleButtonWidget(x - 100, y + 20, 100, 20, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.spectators"), this::roleButtonClicked, () -> getPlayerRole() != PlayerRole.SPECTATOR, PlayerRole.SPECTATOR));
        addRenderableWidget(new RoleOccupantsWidget(x, y - 40, 80, 20, this::getRoleDescription, PlayerRole.PLAYER1));
        addRenderableWidget(new RoleOccupantsWidget(x, y - 10, 80, 20, this::getRoleDescription, PlayerRole.PLAYER2));
        addRenderableWidget(new RoleOccupantsWidget(x, y + 20, 100, 20, this::getRoleDescription, PlayerRole.SPECTATOR));
        addRenderableWidget(new ReadyCheckboxWidget(x + 80, y - 40, 20, 20, "Ready 1", (button) -> ready1ButtonClicked(), () -> getDuelManager().player1Ready, () -> getPlayerRole() == PlayerRole.PLAYER1 && getDuelManager().player2 != null));
        addRenderableWidget(new ReadyCheckboxWidget(x + 80, y - 10, 20, 20, "Ready 2", (button) -> ready2ButtonClicked(), () -> getDuelManager().player2Ready, () -> getPlayerRole() == PlayerRole.PLAYER2 && getDuelManager().player1 != null));
    }
    
    @Override
    protected void renderLabels(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY)
    {
        // 26.2 describes a screen into a render state; 1.21.1 draws it now. The
        // body below is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor ms = new de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor(vanillaGraphics);
        ms.text(font, "Waiting for players...", 8, 6, 0xFF404040, false);
    }
    
    @Override
    public void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY,
        float partialTicks)
    {
        super.extractContents(ms, mouseX, mouseY, partialTicks);
        
        // The parent already describes this exact panel; the Forge original drew
        // it a second time here and the port keeps that rather than deciding on
        // its behalf that the two are interchangeable.
        DdBlitUtil.blitTexels(ms, DuelContainerScreen.DUEL_BACKGROUND_GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, DdBlitUtil.NO_TINT);
    }
    
    public Component getRoleDescription(PlayerRole role)
    {
        if(role == PlayerRole.PLAYER1)
        {
            return Component.literal(getDuelManager().player1 != null ? getDuelManager().player1.getScoreboardName() : "");
        }
        else if(role == PlayerRole.PLAYER2)
        {
            return Component.literal(getDuelManager().player2 != null ? getDuelManager().player2.getScoreboardName() : "");
        }
        else if(role == PlayerRole.SPECTATOR)
        {
            int size = getDuelManager().spectators.size();
            
            if(getPlayerRole() == PlayerRole.SPECTATOR)
            {
                if(size == 1)
                {
                    return Component.literal(ClientProxy.getPlayer().getScoreboardName());
                }
                else
                {
                    return Component.literal(ClientProxy.getPlayer().getScoreboardName() + " + " + (size - 1));
                }
            }
            else
            {
                return Component.literal("" + size);
            }
        }
        
        return Component.empty();
    }
    
    protected void roleButtonClicked(Button button)
    {
        DuelDimension.proxy.sendDuelMessage(new DuelMessages.SelectRole(getHeader(), ((RoleButtonWidget) button).role));
    }
    
    protected void ready1ButtonClicked()
    {
        if(player1Button != null && player2Button != null && getPlayerRole() == PlayerRole.PLAYER1)
        {
            DuelDimension.proxy.sendDuelMessage(new DuelMessages.RequestReady(getHeader(), !getDuelManager().player1Ready));
        }
    }
    
    protected void ready2ButtonClicked()
    {
        if(player1Button != null && player2Button != null && getPlayerRole() == PlayerRole.PLAYER2)
        {
            DuelDimension.proxy.sendDuelMessage(new DuelMessages.RequestReady(getHeader(), !getDuelManager().player2Ready));
        }
    }
}
