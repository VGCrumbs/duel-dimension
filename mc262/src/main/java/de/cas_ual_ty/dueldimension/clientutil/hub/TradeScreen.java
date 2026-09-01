package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardImageManager;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.duel.trade.TradeMessages;
import de.cas_ual_ty.dueldimension.duel.trade.TradeSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The trade table: what each side is putting up, and how close it is to going
 * through.
 *
 * <h2>It shows, it does not decide</h2>
 * Nothing on this screen changes what is being traded. Every click sends a
 * request and the screen redraws when the server answers with the whole table —
 * see {@link TradeMessages}. That is why there is no local copy of the offers
 * to keep in step, and why a click the server refuses simply produces no change
 * rather than a state the two players disagree about.
 *
 * <h2>Every size is measured, none is written down</h2>
 * The first version of this screen used fixed pixel sizes — a 78-unit slot, a
 * grid starting 66 units down — and ran off the bottom of the window, because
 * at the client's 920p and a GUI scale of 3 there are only about 307 units of
 * height to put things in and three rows of 78 do not fit in them. {@link #Geom}
 * measures instead: the slots take whatever room is left once the title, the
 * headers, the points row and the buttons have had theirs, and shrink to the
 * tighter of the two constraints so the grids fit across as well as down.
 *
 * <h2>Both sides are drawn the same</h2>
 * Deliberately symmetrical. A trade screen that gives your own offer more room
 * than theirs is one you read less carefully, and what the other side is
 * putting up is the half that actually needs reading.
 */
public class TradeScreen extends Screen
{
    private static Identifier gui(String path)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/" + path);
    }

    /** Package-visible: the picker draws its tiles in the same wells. */
    static final Identifier SLOT = gui("trade/slot_empty.png");
    private static final Identifier SLOT_READY = gui("trade/slot_ready.png");
    private static final Identifier READY = gui("trade/ready.png");
    private static final Identifier WAITING = gui("trade/waiting.png");
    private static final Identifier DP = gui("trade/dp.png");
    private static final Identifier SIDE = gui("trade/side.png");
    private static final Identifier SIDE_READY = gui("trade/side_ready.png");
    private static final Identifier HEAD = gui("trade/head.png");
    private static final Identifier HEAD_READY = gui("trade/head_ready.png");
    private static final Identifier BANNER = gui("trade/banner.png");
    private static final Identifier ARROWS = gui("trade/arrows.png");

    private static final int COLUMNS = 3;
    private static final int ROWS = 3;

    /** The table, exactly as the server last described it. */
    private TradeMessages.State state;

    private int hovered = -1;

    private net.minecraft.client.gui.components.EditBox points;
    private HubWidgets.TextureButton ready;

    public TradeScreen(TradeMessages.State state)
    {
        super(Component.literal("Trade"));
        this.state = state;
    }

    /**
     * Takes a fresh description of the table.
     * <p>
     * The points field is NOT overwritten while it has focus: the server echoes
     * back what it clamped the offer to, and rewriting the box under a player
     * who is mid-number would fight their typing.
     */
    public void update(TradeMessages.State fresh)
    {
        this.state = fresh;
        if(points != null && !points.isFocused())
        {
            points.setValue(Integer.toString(fresh.myPoints()));
        }
    }

    public TradeMessages.State state()
    {
        return state;
    }

    // ------------------------------------------------------------ geometry

    /**
     * Every size on the screen, worked out once per frame from the window.
     * <p>
     * One ordered pass, in the order the regions actually stack: the fixed
     * furniture first, then whatever is left over goes to the grids. Nothing
     * here measures from a constant, which is what stopped it fitting before.
     */
    private final class Geom
    {
        static final int PAD = 10;
        static final int GAP = 3;
        static final int GUTTER = 58;
        /** The name strip at the top of a side. */
        static final int HEAD_H = 16;
        /** The points row at the bottom of a side. */
        static final int FOOT_H = 22;
        /** Inset between a side's frame and what it holds. */
        static final int INSET = 5;

        final int titleY = 8;
        final int panelTop = 24;
        final int panelBottom;
        final int panelW;
        final int myX;
        final int theirX;
        final int slotW;
        final int slotH;
        final int gridW;
        final int gridH;
        final int gridY;
        final int footY;
        final int bannerY;
        final int buttonY;

        Geom()
        {
            buttonY = height - PAD - 20;
            bannerY = buttonY - 24;
            panelBottom = bannerY - 6;

            panelW = (width - PAD * 2 - GUTTER) / 2;
            myX = width / 2 - GUTTER / 2 - panelW;
            theirX = width / 2 + GUTTER / 2;

            int gridTop = panelTop + HEAD_H + INSET;
            int gridBottom = panelBottom - FOOT_H - INSET;
            int room = panelW - INSET * 2;
            int byWidth = (room - (COLUMNS - 1) * GAP) / COLUMNS;
            int byHeight = Math.round(
                ((gridBottom - gridTop - (ROWS - 1) * GAP) / (float)ROWS)
                    * DuelTextures.CARD_ASPECT);
            slotW = Math.max(18, Math.min(byWidth, byHeight));
            slotH = Math.round(slotW / DuelTextures.CARD_ASPECT);

            gridW = COLUMNS * slotW + (COLUMNS - 1) * GAP;
            gridH = ROWS * slotH + (ROWS - 1) * GAP;
            // Centred in what is left, so a short window packs the grids
            // against the furniture rather than letting them overrun it.
            gridY = gridTop + Math.max(0, (gridBottom - gridTop - gridH) / 2);
            footY = panelBottom - FOOT_H + 3;
        }

        /** A side's grid is centred in its own panel. */
        int gridX(boolean mine)
        {
            return (mine ? myX : theirX) + (panelW - gridW) / 2;
        }

        int x(boolean mine, int slot)
        {
            return gridX(mine) + (slot % COLUMNS) * (slotW + GAP);
        }

        int y(int slot)
        {
            return gridY + (slot / COLUMNS) * (slotH + GAP);
        }
    }

    private Geom geom()
    {
        return new Geom();
    }

    /** Which of MY slots the cursor is over, or -1. */
    private int slotUnder(double mouseX, double mouseY)
    {
        Geom g = geom();
        for(int slot = 0; slot < TradeSession.SLOTS; slot++)
        {
            int x = g.x(true, slot);
            int y = g.y(slot);
            if(mouseX >= x && mouseX < x + g.slotW && mouseY >= y && mouseY < y + g.slotH)
            {
                return slot;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------- widgets

    @Override
    protected void init()
    {
        Geom g = geom();
        points = new net.minecraft.client.gui.components.EditBox(font,
            g.myX + Geom.INSET + 20, g.footY, Math.min(96, g.panelW - Geom.INSET * 2 - 22),
            15, Component.literal("DP"));
        points.setMaxLength(9);
        points.setValue(Integer.toString(state.myPoints()));
        // Digits only. 26.2's EditBox has no filter hook, so the responder is
        // where a stray character is undone.
        points.setResponder(text ->
        {
            String digits = text.replaceAll("[^0-9]", "");
            if(!digits.equals(text))
            {
                points.setValue(digits);
                return;
            }
            ClientPlayNetworking.send(
                new TradeMessages.SetPoints(digits.isEmpty() ? 0 : parse(digits)));
        });
        addRenderableWidget(points);

        ready = new HubWidgets.TextureButton(width / 2 - 104, g.buttonY, 96, 20,
            Component.literal(state.myReady() ? "Not ready" : "Ready"),
            pressed -> ClientPlayNetworking.send(new TradeMessages.SetReady(!state.myReady())));
        addRenderableWidget(ready);

        addRenderableWidget(new HubWidgets.TextureButton(width / 2 + 8, g.buttonY, 96, 20,
            Component.literal("Cancel"),
            pressed -> ClientPlayNetworking.send(new TradeMessages.Cancel())));
    }

    private static int parse(String text)
    {
        try
        {
            return Integer.parseInt(text);
        }
        catch(NumberFormatException tooBig)
        {
            // The server clamps to the balance anyway.
            return Integer.MAX_VALUE;
        }
    }

    // -------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if(super.mouseClicked(event, doubleClick))
        {
            return true;
        }
        int slot = slotUnder(event.x(), event.y());
        if(slot < 0)
        {
            return false;
        }
        if(event.button() == 1)
        {
            // Right-click takes a card back off the table.
            ClientPlayNetworking.send(new TradeMessages.SetSlot(slot, 0, "", 0));
            return true;
        }
        minecraft.setScreenAndShow(new TradePickScreen(this, slot));
        return true;
    }

    /** Puts a card up for trade. Called by {@link TradePickScreen}. */
    public void offer(int slot, int passcode, String rarity, int art)
    {
        ClientPlayNetworking.send(new TradeMessages.SetSlot(slot, passcode, rarity, art));
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            ClientPlayNetworking.send(new TradeMessages.Cancel());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    // ------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY,
        float partialTick)
    {
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        Geom g = geom();
        hovered = slotUnder(mouseX, mouseY);

        String title = "Trade";
        poseStack.text(font, title, width / 2 - font.width(title) / 2, g.titleY,
            MenuInk.title(), MenuInk.shadow());

        drawSide(poseStack, g, true, state.mine(), state.myReady(),
            "You", state.myPoints());
        drawSide(poseStack, g, false, state.theirs(), state.theirReady(),
            state.other(), state.theirPoints());

        // The exchange mark between the two halves, which is the one thing on
        // screen that says these two columns are going to swap places.
        int arrowW = Math.min(40, Geom.GUTTER - 10);
        DdBlitUtil.fullBlit(poseStack, ARROWS, width / 2 - arrowW / 2,
            g.gridY + g.gridH / 2 - arrowW / 4, arrowW, arrowW / 2);

        drawCountdown(poseStack, g);

        if(ready != null)
        {
            ready.setMessage(Component.literal(state.myReady() ? "Not ready" : "Ready"));
        }
        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
    }

    /**
     * One side of the table, frame and all.
     * <p>
     * <b>The whole panel changes when a side agrees</b>, rather than a tick
     * appearing beside its name. A twelve-pixel mark in a corner is not
     * something anyone notices while looking at nine cards, and "have they
     * agreed yet" is the one question this screen exists to answer at a glance.
     * The frame, the header strip and every slot all carry it.
     */
    private void drawSide(GuiGraphicsExtractor poseStack, Geom g, boolean mine,
        java.util.List<TradeMessages.State.Slot> slots, boolean sideReady,
        String caption, int sidePoints)
    {
        int x = mine ? g.myX : g.theirX;
        NineSlice.draw(poseStack, sideReady ? SIDE_READY : SIDE,
            x, g.panelTop, g.panelW, g.panelBottom - g.panelTop);
        NineSlice.draw(poseStack, sideReady ? HEAD_READY : HEAD,
            x + 2, g.panelTop + 2, g.panelW - 4, Geom.HEAD_H);

        poseStack.text(font, caption, x + Geom.INSET + 2, g.panelTop + 7,
            sideReady ? 0xFF9FE0AC : MenuInk.body(), MenuInk.shadow());
        DdBlitUtil.fullBlit(poseStack, sideReady ? READY : WAITING,
            x + g.panelW - Geom.INSET - 12, g.panelTop + 5, 11, 11);

        for(int slot = 0; slot < TradeSession.SLOTS; slot++)
        {
            int sx = g.x(mine, slot);
            int sy = g.y(slot);
            NineSlice.draw(poseStack, sideReady ? SLOT_READY : SLOT, sx, sy, g.slotW, g.slotH);

            TradeMessages.State.Slot held = slot < slots.size() ? slots.get(slot) : null;
            if(held != null && held.passcode() != 0)
            {
                Properties card = DdDatabase.PROPERTIES_LIST.get((long)held.passcode());
                if(card != null)
                {
                    Identifier art = CardImageManager.peekTextureCard(
                        DuelTextures.cardSmooth(card, (byte)held.art(),
                            DuelTextures.ICON_CARD_SIZE),
                        DuelTextures.ICON_CARD_SIZE, true);
                    if(art != null && art != DuelTextures.UNKNOWN)
                    {
                        DdBlitUtil.blit(poseStack, art, sx + 2, sy + 2,
                            g.slotW - 4, g.slotH - 4,
                            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                            DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
                    }
                }
            }
            if(mine && slot == hovered)
            {
                NineSlice.draw(poseStack, HubTextures.PANEL, sx - 2, sy - 2,
                    g.slotW + 4, g.slotH + 4, NineSlice.HOVER, 3, 0.55F);
            }
        }

        // The points row. Mine is the edit box, added as a widget; only the
        // coin and the other side's figure are drawn here.
        DdBlitUtil.fullBlit(poseStack, DP, x + Geom.INSET, g.footY, 15, 15);
        if(!mine)
        {
            poseStack.text(font, sidePoints + " DP", x + Geom.INSET + 20, g.footY + 4,
                MenuInk.title(), MenuInk.shadow());
        }

        // What this side adds up to, said in words at the far end of the row --
        // nine slots are quicker to count once something has counted them.
        int cards = 0;
        for(TradeMessages.State.Slot held : slots)
        {
            if(held != null && held.passcode() != 0)
            {
                cards++;
            }
        }
        String tally = cards == 1 ? "1 card" : cards + " cards";
        poseStack.text(font, tally, x + g.panelW - Geom.INSET - font.width(tally),
            g.footY + 4, MenuInk.dim(), MenuInk.shadow());
    }

    /**
     * The centre banner: the one line that says where the trade has got to.
     * <p>
     * Given a panel of its own rather than being loose text, because during the
     * countdown it is the most important thing on screen and it has to be found
     * without looking for it.
     */
    private void drawCountdown(GuiGraphicsExtractor poseStack, Geom g)
    {
        String line;
        int colour;
        if(state.countdown() >= 0)
        {
            // Rounded up, so the last second reads as 1 rather than 0 for a
            // twentieth of a second before the trade goes through.
            int seconds = (state.countdown() + 19) / 20;
            line = "Trading in " + seconds + "   -   cancel or unready to stop";
            colour = 0xFF9FE0AC;
        }
        else if(state.myReady() && !state.theirReady())
        {
            line = "Waiting for " + state.other();
            colour = MenuInk.body();
        }
        else if(hovered >= 0)
        {
            line = "Click to choose a card   -   right-click to take one back";
            colour = 0xFF9FA6B4;
        }
        else
        {
            line = "Click a slot to put a card up";
            colour = MenuInk.dim();
        }
        int w = Math.min(width - Geom.PAD * 2, font.width(line) + 28);
        NineSlice.draw(poseStack, BANNER, width / 2 - w / 2, g.bannerY, w, 18);
        poseStack.text(font, line, width / 2 - font.width(line) / 2, g.bannerY + 5,
            colour, true);
    }
    /**
     * The hub screen this was opened from, or null. See {@link HubReturn}.
     * <p>
     * Read at construction, because that is the one moment the screen it is
     * replacing is still on show.
     */
    private final net.minecraft.client.gui.screens.Screen dueldimension$parent =
        HubReturn.parent();

    /** Back to the hub if that is where this came from, otherwise to the world. */
    @Override
    public void onClose()
    {
        if(!HubReturn.back(dueldimension$parent))
        {
            super.onClose();
        }
    }
}

