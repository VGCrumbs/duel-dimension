package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardImageManager;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent;
import de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks one card out of the collection to put on the trade table.
 *
 * <h2>It lists printings, not cards</h2>
 * One tile per {@link Trunk.Held} — this rarity, this artwork — rather than one
 * per passcode. A trade has to name the exact copy being handed over, because
 * that is the copy the other side is being shown and the one
 * {@code Trunk.remove(passcode, rarity, art, copies)} will take. A picker that
 * returned only a passcode would leave the server choosing which of a player's
 * three printings to part with, which is not a choice the server should be
 * making.
 *
 * <h2>It does not decide anything either</h2>
 * Choosing sends {@code SetSlot} and closes. The card does not appear on the
 * table until the server says so, exactly as with every other edit — see
 * {@link TradeScreen}.
 */
public class TradePickScreen extends Screen
{
    private final TradeScreen table;
    private final int slot;

    /** Every printing the player holds, filtered by the search box. */
    private final List<Trunk.Held> all = new ArrayList<>();
    private final List<Trunk.Held> shown = new ArrayList<>();

    private EditBox search;
    private float scroll;
    private float scrollTarget;

    public TradePickScreen(TradeScreen table, int slot)
    {
        super(Component.literal("Choose a card"));
        this.table = table;
        this.slot = slot;
    }

    /**
     * No background from vanilla, because this screen draws before
     * {@code super.render} and vanilla draws the background from inside it.
     * <p>
     * In a level that background is the BLUR and nothing else -- the panorama
     * and {@code renderMenuBackground} are both gated on there being no level --
     * so leaving it in place blurs everything this screen has already put down
     * while the widgets drawn afterwards stay sharp. 26.2 refuses it too, in the
     * same words:
     * <blockquote>fillGradient, not extractBackground: that one blurs.</blockquote>
     * The dim, where this screen wants one, is its own and goes down first.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }

    /** The table this was opened from, which an update still belongs to. */
    public TradeScreen table()
    {
        return table;
    }

    @Override
    protected void init()
    {
        all.clear();
        Trunk trunk = EditorState.trunk();
        if(trunk != null)
        {
            // Sorted by name, because the collection's own order is the order
            // cards were acquired and nobody looks for a card that way.
            for(Integer passcode : trunk.all().keySet())
            {
                all.addAll(trunk.heldPrintings(passcode));
            }
            all.sort(java.util.Comparator.comparing(held -> nameOf(held).toLowerCase()));
        }

        search = new EditBox(font, width / 2 - 110, 30, 220, 16,
            Component.literal("Search"));
        search.setResponder(text -> refilter());
        addRenderableWidget(search);

        addRenderableWidget(new HubWidgets.TextureButton(width / 2 - 48, height - 26, 96, 20,
            Component.literal("Back"), pressed -> onClose()));
        refilter();
    }

    private static String nameOf(Trunk.Held held)
    {
        Properties card = DdDatabase.PROPERTIES_LIST.get((long)held.passcode());
        return card == null ? "" : card.getName();
    }

    private void refilter()
    {
        String needle = search == null ? "" : search.getValue().trim().toLowerCase();
        shown.clear();
        for(Trunk.Held held : all)
        {
            if(needle.isEmpty() || nameOf(held).toLowerCase().contains(needle))
            {
                shown.add(held);
            }
        }
        scroll = 0F;
        scrollTarget = 0F;
    }

    @Override
    public void onClose()
    {
        // Back to the table, not to the world: this screen was opened from it
        // and closing it is not the same as cancelling the trade.
        minecraft.setScreen(table);
    }

    // ------------------------------------------------------------ geometry

    private static final int PAD = 10;
    private static final int GAP = 4;

    private int top()
    {
        return 54;
    }

    private int bottom()
    {
        return height - 34;
    }

    private int tileW()
    {
        int columns = columns();
        return (width - PAD * 2 - (columns - 1) * GAP) / columns;
    }

    private int columns()
    {
        // As many as fit at roughly the icon size the grids elsewhere use.
        return Math.max(4, Math.min(12, (width - PAD * 2 + GAP) / (52 + GAP)));
    }

    private int tileH()
    {
        return Math.round(tileW() / DuelTextures.CARD_ASPECT);
    }

    private int rows()
    {
        return (shown.size() + columns() - 1) / columns();
    }

    private float maxScroll()
    {
        return Math.max(0F, rows() * (tileH() + GAP) - GAP - (bottom() - top()));
    }

    private int indexAt(double mouseX, double mouseY)
    {
        if(mouseY < top() || mouseY >= bottom())
        {
            return -1;
        }
        int columns = columns();
        int pitch = tileH() + GAP;
        int column = (int)((mouseX - PAD) / (tileW() + GAP));
        if(column < 0 || column >= columns)
        {
            return -1;
        }
        int row = (int)((mouseY - top() + scroll) / pitch);
        int index = row * columns + column;
        return index >= 0 && index < shown.size() ? index : -1;
    }

    // -------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        MouseButtonEvent event = new MouseButtonEvent(vanillaX, vanillaY, vanillaButton);

        if(super.mouseClicked(vanillaX, vanillaY, vanillaButton))
        {
            return true;
        }
        int index = indexAt(event.x(), event.y());
        if(index >= 0)
        {
            Trunk.Held held = shown.get(index);
            table.offer(slot, held.passcode(), held.rarity(), held.art());
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        scrollTarget = Mth.clamp(scrollTarget - (float)delta * (tileH() + GAP) * 0.6F,
            0F, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        KeyEvent event = new KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);

        if(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            onClose();
            return true;
        }
        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    // ------------------------------------------------------------- drawing

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX,
        int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        scroll += (scrollTarget - scroll) * 0.35F;

        String title = "Choose a card for slot " + (slot + 1);
        poseStack.text(font, title, width / 2 - font.width(title) / 2, 12, MenuInk.title(), MenuInk.shadow());

        int columns = columns();
        int tileW = tileW();
        int tileH = tileH();
        int pitch = tileH + GAP;
        int hovered = indexAt(mouseX, mouseY);

        poseStack.enableScissor(0, top(), width, bottom());
        int first = (int)(scroll / pitch) * columns;
        for(int index = first; index < shown.size(); index++)
        {
            int column = index % columns;
            int row = index / columns;
            int x = PAD + column * (tileW + GAP);
            int y = top() + row * pitch - Math.round(scroll);
            if(y > bottom())
            {
                break;
            }
            Trunk.Held held = shown.get(index);
            NineSlice.draw(poseStack, TradeScreen.SLOT, x, y, tileW, tileH);
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)held.passcode());
            if(card != null)
            {
                ResourceLocation art = CardImageManager.peekTextureCard(
                    DuelTextures.cardSmooth(card, (byte)held.art(),
                        DuelTextures.ICON_CARD_SIZE),
                    DuelTextures.ICON_CARD_SIZE, true);
                if(art != null && art != DuelTextures.UNKNOWN)
                {
                    DdBlitUtil.blit(poseStack, art, x + 2, y + 2, tileW - 4, tileH - 4,
                        DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                        DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
                }
            }
            if(held.count() > 1)
            {
                String count = "x" + held.count();
                poseStack.text(font, count, x + tileW - font.width(count) - 3,
                    y + tileH - 10, MenuInk.title(), MenuInk.shadow());
            }
            if(index == hovered)
            {
                NineSlice.draw(poseStack, HubTextures.PANEL, x - 2, y - 2,
                    tileW + 4, tileH + 4, NineSlice.HOVER, 3, 0.55F);
            }
        }
        poseStack.disableScissor();

        if(shown.isEmpty())
        {
            String empty = all.isEmpty() ? "Your collection is empty."
                : "Nothing matches that.";
            poseStack.text(font, empty, width / 2 - font.width(empty) / 2,
                top() + 20, MenuInk.dim(), MenuInk.shadow());
        }
        else if(hovered >= 0)
        {
            Trunk.Held held = shown.get(hovered);
            String label = nameOf(held)
                + (held.rarity().isEmpty() ? "" : "  -  " + held.rarity());
            poseStack.text(font, label, width / 2 - font.width(label) / 2,
                height - 44, MenuInk.body(), MenuInk.shadow());
        }
        super.render(vanillaGraphics, mouseX, mouseY, partialTick);
    }
}
