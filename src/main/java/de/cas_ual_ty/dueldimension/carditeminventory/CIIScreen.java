package de.cas_ual_ty.dueldimension.carditeminventory;

import de.cas_ual_ty.dueldimension.clientutil.widget.ImprovedButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * A paged card-inventory screen.
 * <p>
 * The retained-mode container-screen shape, taken straight from vanilla's own
 * {@code ContainerScreen}: the panel texture is described in
 * {@link #extractBackground} (absolute coordinates, after {@code super} draws
 * the darkened world behind it), while the base's {@code extractContents}
 * translates to {@code leftPos, topPos} and calls {@link #extractLabels} and the
 * slots itself — so a title drawn here uses panel-local coordinates, exactly as
 * the Forge {@code renderLabels} did. {@code imageWidth}/{@code imageHeight} are
 * final now, so the size is passed to the constructor rather than assigned.
 */
public class CIIScreen<T extends CIIContainer> extends AbstractContainerScreen<T>
{
    private static final Identifier CHEST_GUI_TEXTURE =
        Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    private final int inventoryRows;

    protected Button prevButton;
    protected Button nextButton;

    public CIIScreen(T container, Inventory playerInventory, Component title)
    {
        super(container, playerInventory, title, 176, 114 + 6 * 18);
        inventoryRows = 6;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init()
    {
        super.init();

        addRenderableWidget(prevButton = new ImprovedButton(leftPos + imageWidth - 24 - 8, topPos + 4, 12, 12, Component.translatable("generic.dueldimension.left_arrow"), this::onButtonClicked));
        addRenderableWidget(nextButton = new ImprovedButton(leftPos + imageWidth - 12 - 8, topPos + 4, 12, 12, Component.translatable("generic.dueldimension.right_arrow"), this::onButtonClicked));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick)
    {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);
        extractor.blit(RenderPipelines.GUI_TEXTURED, CHEST_GUI_TEXTURE, leftPos, topPos,
            0F, 0F, imageWidth, inventoryRows * 18 + 17, 256, 256);
        extractor.blit(RenderPipelines.GUI_TEXTURED, CHEST_GUI_TEXTURE, leftPos, topPos + inventoryRows * 18 + 17,
            0F, 126F, imageWidth, 96, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY)
    {
        MutableComponent title = Component.literal(this.title.getString());
        title = title.append(" ").append(Component.literal((menu.getPage() + 1) + "/" + menu.getMaxPage()));
        extractor.text(font, title, 8, 6, 0xFF404040);
    }

    protected void onButtonClicked(Button button)
    {
        if(button == prevButton)
        {
            ClientPlayNetworking.send(new CIIMessages.ChangePage(false));
        }
        else if(button == nextButton)
        {
            ClientPlayNetworking.send(new CIIMessages.ChangePage(true));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent)
    {
        // Let the number-row keys through to the container's hotbar swap rather
        // than being eaten as a menu shortcut.
        if(keyEvent.key() <= 57 && keyEvent.key() >= 49)
        {
            return false;
        }

        return super.keyPressed(keyEvent);
    }
}
