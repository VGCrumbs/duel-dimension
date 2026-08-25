package de.cas_ual_ty.dueldimension.deckbox;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;


public class DeckBoxScreen extends AbstractContainerScreen<DeckBoxContainer>
{
    public static final ResourceLocation DECK_BOX_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/deck_box.png");

    public DeckBoxScreen(DeckBoxContainer screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn, 284, 250);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        super.extractBackground(ms, mouseX, mouseY, partialTicks);
        // The panel is a 284x250 window inside a 512x256 file.
        DdBlitUtil.blit(ms, DeckBoxScreen.DECK_BOX_GUI_TEXTURE, leftPos, topPos, imageWidth, imageHeight,
            0F, 0F, imageWidth / 512F, imageHeight / 256F, DdBlitUtil.NO_TINT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        Slot s;
        int amount;

        // main deck

        amount = 0;
        for(int i = DeckHolder.MAIN_DECK_INDEX_START; i < DeckHolder.MAIN_DECK_INDEX_END; ++i)
        {
            s = getMenu().getSlot(i);

            if(s != null && s.hasItem())
            {
                amount++;
            }
        }

        //drawString
        ms.text(font, Component.translatable("container.dueldimension.deck_box.main").append(" " + amount + "/" + DeckHolder.MAIN_DECK_SIZE), 8, 6, 0xFF404040);

        // extra deck

        amount = 0;
        for(int i = DeckHolder.EXTRA_DECK_INDEX_START; i < DeckHolder.EXTRA_DECK_INDEX_END; ++i)
        {
            s = getMenu().getSlot(i);

            if(s != null && s.hasItem())
            {
                amount++;
            }
        }

        //drawString
        ms.text(font, Component.translatable("container.dueldimension.deck_box.extra").append(" " + amount + "/" + DeckHolder.EXTRA_DECK_SIZE), 8, 92, 0xFF404040);

        // side deck

        amount = 0;
        for(int i = DeckHolder.SIDE_DECK_INDEX_START; i < DeckHolder.SIDE_DECK_INDEX_END; ++i)
        {
            s = getMenu().getSlot(i);

            if(s != null && s.hasItem())
            {
                amount++;
            }
        }

        //drawString
        ms.text(font, Component.translatable("container.dueldimension.deck_box.side").append(" " + amount + "/" + DeckHolder.SIDE_DECK_SIZE), 8, 124, 0xFF404040);

        ms.text(font, Component.translatable("container.dueldimension.deck_box.sleeves"), 224, imageHeight - 96 + 2, 0xFF404040);

        ms.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040);
    }
}
