package de.cas_ual_ty.dueldimension.duel.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.clientutil.SwitchableContainerScreen;
import de.cas_ual_ty.dueldimension.clientutil.widget.TextureButton;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxScreen;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.duel.*;
import de.cas_ual_ty.dueldimension.duel.action.Action;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeader;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessages;
import de.cas_ual_ty.dueldimension.duel.playfield.PlayField;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import de.cas_ual_ty.dueldimension.duel.screen.widget.DisplayChatWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public abstract class DuelContainerScreen<E extends DuelContainer> extends SwitchableContainerScreen<E>
{
    public static final ResourceLocation DUEL_FOREGROUND_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/duel_foreground.png");
    public static final ResourceLocation DUEL_BACKGROUND_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/duel_background.png");
    
    public static final ResourceLocation DECK_BACKGROUND_GUI_TEXTURE = DeckBoxScreen.DECK_BOX_GUI_TEXTURE;
    
    protected DuelScreenConstructor<E>[] screensForEachState;
    
    protected Button chatUpButton;
    protected Button chatDownButton;
    protected DisplayChatWidget chatWidget;
    protected EditBox textFieldWidget;
    
    protected Button duelChatButton;
    protected Button worldChatButton;
    protected boolean duelChat;
    
    protected List<Component> worldChatMessages;
    
    protected Inventory playerInv;
    
    @SuppressWarnings("unchecked")
    public DuelContainerScreen(E screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn, 234, 250);

        worldChatMessages = new ArrayList<>(32);
        textFieldWidget = null;
        duelChat = true;
        
        //default
        screensForEachState = new DuelScreenConstructor[DuelState.VALUES.length];
        screensForEachState[DuelState.IDLE.getIndex()] = DuelScreenIdle::new;
        screensForEachState[DuelState.PREPARING.getIndex()] = DuelScreenPreparing::new;
        screensForEachState[DuelState.END.getIndex()] = DuelScreenPreparing::new;
        screensForEachState[DuelState.DUELING.getIndex()] = DuelScreenDueling::new;
        screensForEachState[DuelState.SIDING.getIndex()] = DuelScreenDueling::new;
        
        playerInv = inv;
    }
    
    public DuelContainerScreen<E> setScreenForState(DuelState state, DuelScreenConstructor<E> screen)
    {
        screensForEachState[state.getIndex()] = screen;
        return this;
    }
    
    protected DuelContainerScreen<E> createNewScreenForState(DuelState state)
    {
        return screensForEachState[state.getIndex()].construct(menu, playerInv, title);
    }
    
    public final void duelStateChanged()
    {
        switchScreen(createNewScreenForState(getState()));
    }
    
    public final void reInit()
    {
        // Forge's init(Minecraft, int, int) on an already-initialised screen took
        // the repositionElements branch, which is this and nothing else.
        rebuildWidgets();
    }

    /**
     * The old {@code renderBg} chain, called from where a background belongs.
     * <p>
     * On Forge {@code renderBg} ran <em>before</em> the widgets and the slots,
     * and the duel screens depend on it: the field art is drawn in this chain and
     * the zone widgets put cards on top of it. 26.2's
     * {@code AbstractContainerScreen.extractContents} is the widget, slot and
     * label pass itself, so anything a subclass adds around a {@code super} call
     * to it lands above them instead of below.
     * <p>
     * So the chain keeps the name the subclasses already override, and is invoked
     * from here -- {@link #extractRenderState} then does the container's own work
     * afterwards. That is also where vanilla draws a container panel
     * ({@code ContainerScreen}, and {@code DeckBoxScreen} and {@code CIIScreen}
     * here).
     * <p>
     * {@code super.extractBackground} is deliberately not called: it dims the
     * world, and this screen dims it itself, with the fill below that Forge used
     * in place of {@code renderBackground}.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor ms, int mouseX, int mouseY,
        float partialTicks)
    {
        extractContents(ms, mouseX, mouseY, partialTicks);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY,
        float partialTicks)
    {
        // AbstractContainerScreen's own version, minus the extractContents call
        // extractBackground has already made. super.extractContents reaches that
        // class's implementation -- widgets, slots, labels -- not this chain.
        super.extractContents(ms, mouseX, mouseY, partialTicks);
        extractCarriedItem(ms, mouseX, mouseY);
        extractTooltip(ms, mouseX, mouseY);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY,
        float partialTicks)
    {
        ScreenUtil.renderDisabledRect(ms, 0, 0, width, height);

        DdBlitUtil.blitTexels(ms, DUEL_BACKGROUND_GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, DdBlitUtil.NO_TINT);

        // The base's extractContents is what describes the slots and calls
        // extractLabels. Forge's renderBg was only the backdrop and the parent
        // ran the rest around it; here the two are one method, so overriding it
        // without this line silently loses every slot and label on this screen
        // and on all four that extend it.
        super.extractContents(ms, mouseX, mouseY, partialTicks);
    }

    @Override
    public void switchScreen(AbstractContainerScreen<E> s)
    {
        super.switchScreen(s);
        
        if(s instanceof DuelContainerScreen)
        {
            DuelContainerScreen<E> screen = (DuelContainerScreen<E>) s;
            screen.screensForEachState = screensForEachState;
            screen.worldChatMessages = worldChatMessages;
        }
    }
    
    @Override
    protected void onGuiClose()
    {
        super.onGuiClose();
        getDuelManager().reset();
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if(textFieldWidget != null && textFieldWidget.isFocused() && !textFieldWidget.isMouseOver(event.x(), event.y()))
        {
            textFieldWidget.setFocused(false);
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if(textFieldWidget != null && textFieldWidget.isFocused())
        {
            if(event.key() == GLFW.GLFW_KEY_ENTER)
            {
                sendChat();
                return true;
            }
            else
            {
                return textFieldWidget.keyPressed(event);
            }
        }
        else
        {
            return super.keyPressed(event);
        }
    }
    
    public void renderDisabledTooltip(GuiGraphicsExtractor ms, List<FormattedCharSequence> tooltips, int mouseX, int mouseY)
    {
        tooltips.add(Component.literal("DISABLED").withStyle((s) -> s.applyFormat(ChatFormatting.ITALIC).applyFormat(ChatFormatting.RED)).getVisualOrderText());
        tooltips.add(Component.literal("COMING SOON").withStyle((s) -> s.applyFormat(ChatFormatting.ITALIC).applyFormat(ChatFormatting.RED)).getVisualOrderText());
        ms.setTooltipForNextFrame(font, tooltips, mouseX, mouseY);
    }
    
    public void renderDisabledTooltip(GuiGraphicsExtractor ms, @Nullable Component text, int mouseX, int mouseY)
    {
        List<FormattedCharSequence> tooltips = new LinkedList<>();
        
        if(text != null)
        {
            tooltips.add(text.getVisualOrderText());
        }
        
        renderDisabledTooltip(ms, tooltips, mouseX, mouseY);
    }
    
    protected void initDefaultChat(int width, int height)
    {
        final int margin = 4;
        final int buttonHeight = 20;
        
        int x = leftPos + imageWidth + margin;
        int y = topPos + margin;
        
        int maxWidth = Math.min(160, (this.width - imageWidth) / 2 - 2 * margin);
        int maxHeight = imageHeight;
        
        int chatWidth = maxWidth;
        int chatHeight = (maxHeight - 4 * (buttonHeight + margin) - 2 * margin);
        
        initChat(width, height, x, y, maxWidth, maxHeight, chatWidth, chatHeight, margin, buttonHeight);
    }
    
    protected void initChat(int width, int height, int x, int y, int w, int h, int chatWidth, int chatHeight, int margin, int buttonHeight)
    {
        final int offset = buttonHeight + margin;
        
        int halfW = w / 2;
        int extraOff = halfW % 2;
        
        addRenderableWidget(duelChatButton = Button.builder(Component.translatable("container." + DuelDimension.MOD_ID + ".duel.duel_chat"), (b) -> switchChat()).bounds(x, y, halfW, buttonHeight).build());
        addRenderableWidget(worldChatButton = Button.builder(Component.translatable("container." + DuelDimension.MOD_ID + ".duel.world_chat"), (b) -> switchChat()).bounds(x + halfW - extraOff, y, halfW + extraOff, buttonHeight).build());
        y += offset;
        
        // The arrows explain themselves while hovered, which a plain Button has
        // no hook for any more; TextureButton with no texture set is one that
        // does, and draws the same standard sprite.
        addRenderableWidget(chatUpButton = new TextureButton(x, y, w, buttonHeight, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.up_arrow"), this::chatScrollButtonClicked, this::chatScrollButtonHovered));
        y += offset;
        
        addRenderableWidget(chatWidget = new DisplayChatWidget(x, y - (chatHeight % font.lineHeight) / 2, chatWidth, chatHeight, Component.empty()));
        y += chatHeight + margin;
        
        addRenderableWidget(chatDownButton = new TextureButton(x, y, w, buttonHeight, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.down_arrow"), this::chatScrollButtonClicked, this::chatScrollButtonHovered));
        y += offset;
        
        addRenderableWidget(textFieldWidget = new EditBox(font, x + 1, y + 1, w - 2, buttonHeight - 2, Component.empty()));
        textFieldWidget.setMaxLength(64);
        y += offset;
        
        appendToInitChat(width, height, extraOff, y, w, halfW, chatWidth, chatHeight, margin);
        
        duelChat = !duelChat;
        switchChat();
        
        chatUpButton.active = false;
        chatDownButton.active = false; //TODO remove these
        
        makeChatVisible();
    }
    
    protected void appendToInitChat(int width, int height, int x, int y, int w, int h, int chatWidth, int chatHeight, int margin)
    {
        
    }
    
    protected void changeChatFlags(boolean flag)
    {
        chatUpButton.visible = flag;
        chatDownButton.visible = flag;
        chatWidget.visible = flag;
        textFieldWidget.visible = flag;
        duelChatButton.visible = flag;
        worldChatButton.visible = flag;
    }
    
    public void makeChatVisible()
    {
        changeChatFlags(true);
    }
    
    public void makeChatInvisible()
    {
        changeChatFlags(false);
    }
    
    protected void sendChat()
    {
        String text = textFieldWidget.getValue().trim();
        
        if(!text.isEmpty())
        {
            if(duelChat)
            {
                DuelDimension.proxy.sendDuelMessage(new DuelMessages.SendMessageToServer(getHeader(), Component.literal(text)));
            }
            else
            {
                // chatSigned is gone; it only ever forwarded to the connection,
                // which now does the signing itself.
                minecraft.player.connection.sendChat(text);
            }
        }
        
        textFieldWidget.setValue("");
    }
    
    protected void switchChat()
    {
        if(chatWidget.visible)
        {
            Button toEnable;
            Button toDisable;
            
            if(duelChat)
            {
                toEnable = duelChatButton;
                toDisable = worldChatButton;
                chatWidget.setTextSupplier(getLevelMessagesSupplier());
            }
            else
            {
                toEnable = worldChatButton;
                toDisable = duelChatButton;
                chatWidget.setTextSupplier(getDuelMessagesSupplier());
            }
            
            toEnable.active = true;
            toDisable.active = false;
            duelChat = !duelChat;
        }
    }
    
    protected Supplier<List<Component>> getDuelMessagesSupplier()
    {
        return () -> //TODO
        {
            List<Component> list = new ArrayList<>(getDuelManager().getMessages().size());
            
            for(DuelChatMessage msg : getDuelManager().getMessages())
            {
                list.add(msg.generateStyledMessage(getPlayerRole(), ChatFormatting.BLUE, ChatFormatting.RED, ChatFormatting.WHITE));
            }
            
            return list;
        };
    }
    
    protected Supplier<List<Component>> getLevelMessagesSupplier()
    {
        // PORT-NOTE: ClientProxy.chatMessages does not exist in this tree yet.
        // It is the rolling window of world chat that the Forge ClientProxy filled
        // from ClientChatReceivedEvent, and it has to come back with the rest of
        // that class -- the field plus a Fabric ClientReceiveMessageEvents hook,
        // including the isBlocked check the Forge handler made. Pointing this at
        // the screen's own worldChatMessages instead would compile and show an
        // empty world chat forever, so it stays as it is.
        return () -> ClientProxy.chatMessages;
    }
    
    protected void chatScrollButtonClicked(Button button)
    {
        //TODO
    }
    
    protected void chatScrollButtonHovered(AbstractWidget w, GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        //TODO
        renderDisabledTooltip(ms, (Component) null, mouseX, mouseY);
    }
    
    public void populateDeckSources(List<DeckSource> deckSources)
    {
    }
    
    public void receiveDeck(int index, DeckHolder deck)
    {
    }
    
    public void deckAccepted(PlayerRole role)
    {
    }
    
    public void handleAction(Action action)
    {
        action.initClient(getDuelManager().getPlayField());
        action.doAction();
    }
    
    public DuelManager getDuelManager()
    {
        return menu.getDuelManager();
    }
    
    public PlayField getPlayField()
    {
        return getDuelManager().getPlayField();
    }
    
    public DuelMessageHeader getHeader()
    {
        return getDuelManager().headerFactory.get();
    }
    
    public DuelState getState()
    {
        return getDuelManager().getDuelState();
    }
    
    public PlayerRole getPlayerRole()
    {
        // PORT-NOTE: ClientProxy.getPlayer() is not in this tree's ClientProxy
        // yet either (it is getMinecraft().player). DuelScreenIdle calls it too,
        // so it belongs in that class rather than being inlined here.
        return getDuelManager().getRoleFor(ClientProxy.getPlayer());
    }
    
    public ZoneOwner getZoneOwner()
    {
        PlayerRole role = getPlayerRole();
        
        if(ZoneOwner.PLAYER1.player == role)
        {
            return ZoneOwner.PLAYER1;
        }
        else if(ZoneOwner.PLAYER2.player == role)
        {
            return ZoneOwner.PLAYER2;
        }
        else
        {
            return ZoneOwner.NONE;
        }
    }
    
    public interface DuelScreenConstructor<E extends DuelContainer>
    {
        DuelContainerScreen<E> construct(E container, Inventory inv, Component title);
    }
}
