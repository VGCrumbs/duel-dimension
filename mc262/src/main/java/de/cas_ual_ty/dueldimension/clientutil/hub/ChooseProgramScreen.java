package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import de.cas_ual_ty.dueldimension.duel.npc.DuelBotEntity;
import de.cas_ual_ty.dueldimension.duel.npc.DuelBotMessages;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.deck.StructureDecks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Which deck should this Duel Bot play?
 * <p>
 * Three programs, and each opens the list of decks in it: eighteen starter
 * decks, fifty-five structure decks, or whatever the player has built. Asked at
 * the click for the same reason {@link DuelTypeScreen} asks its question there
 * -- a duel against an NPC begins the moment it is asked for, and there is no
 * lobby to settle anything in.
 * <p>
 * <b>The panel is sized to the screen, never to the list.</b> Fifty-five decks
 * at a row each is taller than the window, and a panel that simply grew ran off
 * the top and bottom with its buttons unreachable. The list scrolls inside a
 * fixed viewport instead -- wheel or scrollbar -- which is also how the shops
 * in this hub handle a list that does not fit.
 * <p>
 * <b>Only the player's own deck names travel over the wire.</b> The starter and
 * structure lists live in {@code common} and are compiled into the jar, so
 * sending them would be sending the client something it already has. What it
 * cannot know is what the player has built, and that is what
 * {@link DuelBotMessages.OfferProgram} carries.
 */
public class ChooseProgramScreen extends Screen
{
    /** One row of a deck list: what to send, and what to show. */
    private record Choice(String id, String label)
    {
    }

    private final int botId;
    private final String botName;
    private final List<String> ownDecks;

    /** Which program's list is open, or null while the three programs show. */
    private String openList;
    /** First visible row. */
    private int scrollRow;
    /** Where in the thumb a drag took hold, or -1 when not dragging. */
    private int scrollGrab = -1;

    private static final int ROW = 24;
    private static final int BAR_GRAB = 3;
    /** Leaves the panel clear of the screen edges at any window size. */
    private static final int SCREEN_MARGIN = 24;

    public ChooseProgramScreen(DuelBotMessages.OfferProgram offer)
    {
        super(Component.literal("Choose your program"));
        this.botId = offer.botId();
        this.botName = offer.botName();
        this.ownDecks = offer.ownDecks();
    }

    private static boolean destinyDraw()
    {
        return de.cas_ual_ty.dueldimension.clientutil.DestinyDrawSettings.versusBots();
    }

    private List<Choice> choices()
    {
        List<Choice> out = new ArrayList<>();
        if(DuelBotEntity.STRUCTURE.equals(openList))
        {
            for(StructureDecks.Entry entry : StructureDecks.ALL)
            {
                out.add(new Choice(entry.id(), entry.displayName()));
            }
        }
        else if(DuelBotEntity.CUSTOM.equals(openList))
        {
            for(String name : ownDecks)
            {
                out.add(new Choice(name, name));
            }
        }
        else if(DuelBotEntity.STARTER.equals(openList))
        {
            for(StarterDecks.Entry entry : StarterDecks.ALL)
            {
                out.add(new Choice(entry.id(), entry.displayName()));
            }
        }
        return out;
    }

    private int panelW()
    {
        return Math.min(340, width - SCREEN_MARGIN * 2);
    }

    /** Where each root row starts, measured down from the panel's top. */
    private static final int ROOT_FIRST = 40;
    /** The ordinary step from one root row to the next. */
    private static final int ROOT_STEP = 26;
    /** A wider step, once, to set the setting apart from the three decks. */
    private static final int ROOT_SPLIT = 30;
    private static final int BUTTON_H = 20;
    /** Air under the last root row. */
    private static final int ROOT_FOOT = 12;

    /**
     * The root menu's rows, in the order {@code init} lays them out.
     *
     * <h2>Why this is a list and not five numbers in two places</h2>
     * The height used to be the literal 158, worked out by hand for the four
     * rows there were at the time. Destiny Draws made it five and nothing
     * recomputed anything, so Cancel hung ten pixels below the panel it was
     * supposed to be inside. Now the layout and the height read the same array
     * and a sixth row cannot desynchronise them.
     */
    private static int[] rootRows()
    {
        return new int[] {
            ROOT_FIRST,
            ROOT_FIRST + ROOT_STEP,
            ROOT_FIRST + ROOT_STEP * 2,
            ROOT_FIRST + ROOT_STEP * 2 + ROOT_SPLIT,
            ROOT_FIRST + ROOT_STEP * 3 + ROOT_SPLIT};
    }

    /** Tall enough for the rows it has, but never taller than the window. */
    private int panelH()
    {
        if(openList == null)
        {
            int[] rows = rootRows();
            return rows[rows.length - 1] + BUTTON_H + ROOT_FOOT;
        }
        int wanted = 40 + choices().size() * ROW + 38;
        return Math.min(wanted, height - SCREEN_MARGIN * 2);
    }

    private int panelX()
    {
        return (width - panelW()) / 2;
    }

    private int panelY()
    {
        return (height - panelH()) / 2;
    }

    private int listTop()
    {
        return panelY() + 40;
    }

    private int listH()
    {
        return Math.max(ROW, panelY() + panelH() - 38 - listTop());
    }

    private int visibleRows()
    {
        return Math.max(1, listH() / ROW);
    }

    private int maxScroll()
    {
        return Math.max(0, choices().size() - visibleRows());
    }

    private boolean scrolls()
    {
        return maxScroll() > 0;
    }

    private int trackX()
    {
        return panelX() + panelW() - 14;
    }

    private int thumbH()
    {
        int rows = choices().size();
        return rows <= 0 ? listH()
            : Math.max(12, listH() * visibleRows() / rows);
    }

    private int thumbY()
    {
        int max = maxScroll();
        return max <= 0 ? listTop()
            : listTop() + (listH() - thumbH()) * scrollRow / max;
    }

    @Override
    protected void init()
    {
        int x = panelX() + 12;
        int w = panelW() - 24;

        if(openList == null)
        {
            int[] rows = rootRows();
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[0], w, BUTTON_H,
                Component.literal("Starter Deck"),
                pressed -> open(DuelBotEntity.STARTER)));
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[1], w, BUTTON_H,
                Component.literal("Structure Deck"),
                pressed -> open(DuelBotEntity.STRUCTURE)));
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[2], w, BUTTON_H,
                Component.literal("Custom Deck"),
                pressed -> open(DuelBotEntity.CUSTOM)));
            // The bot's own setting, on the bot's own screen. Remembered
            // between bots by the client, because the answer is about the
            // player and not about which block they happened to place.
            //
            // A TICK BOX rather than a button reading "ON". A button is a thing
            // you press to make something happen; this is a state that is either
            // set or not, and the box says which at a glance without having to
            // read a word at the end of a sentence.
            addRenderableWidget(new HubWidgets.CheckBox(x, panelY() + rows[3], w, BUTTON_H,
                Component.literal("Destiny Draws"), ChooseProgramScreen::destinyDraw,
                pressed ->
                {
                    de.cas_ual_ty.dueldimension.clientutil.DestinyDrawSettings
                        .setVersusBots(!destinyDraw());
                    rebuildWidgets();
                }));
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[4], w, BUTTON_H,
                Component.literal("Cancel"), pressed -> onClose()));
            return;
        }

        // Narrower when a scrollbar is showing, so a row and the bar never
        // overlap and a click near the edge is unambiguously one or the other.
        int rowW = scrolls() ? w - 14 : w;
        List<Choice> all = choices();
        scrollRow = Math.clamp(scrollRow, 0, maxScroll());
        int first = scrollRow;
        for(int i = 0; i < visibleRows() && first + i < all.size(); i++)
        {
            Choice choice = all.get(first + i);
            addRenderableWidget(new HubWidgets.TextureButton(x, listTop() + i * ROW,
                rowW, 20, Component.literal(choice.label()),
                pressed -> choose(openList, choice.id())));
        }

        addRenderableWidget(new HubWidgets.TextureButton(x,
            panelY() + panelH() - 30, w, 20,
            Component.literal("Back"), pressed -> back()));
    }

    private void open(String program)
    {
        openList = program;
        scrollRow = 0;
        rebuild();
    }

    private void back()
    {
        openList = null;
        scrollRow = 0;
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();
        init();
    }

    private void choose(String program, String deckId)
    {
        ClientPlayNetworking.send(
            new DuelBotMessages.ChooseProgram(botId, program, deckId, destinyDraw()));
        onClose();
    }

    private void scrollBy(int rows)
    {
        int was = scrollRow;
        scrollRow = Math.clamp(scrollRow + rows, 0, maxScroll());
        if(scrollRow != was)
        {
            rebuild();
        }
    }

    private boolean grabScrollbar(double mouseX, double mouseY)
    {
        if(!scrolls() || mouseX < trackX() - BAR_GRAB
            || mouseX >= trackX() + 4 + BAR_GRAB
            || mouseY < listTop() || mouseY >= listTop() + listH())
        {
            return false;
        }
        int thumbY = thumbY();
        scrollGrab = mouseY >= thumbY && mouseY < thumbY + thumbH()
            ? (int)(mouseY - thumbY) : thumbH() / 2;
        dragScrollbar(mouseY);
        return true;
    }

    private void dragScrollbar(double mouseY)
    {
        int travel = listH() - thumbH();
        if(travel <= 0 || maxScroll() <= 0)
        {
            return;
        }
        double top = mouseY - scrollGrab - listTop();
        int row = (int)Math.clamp(Math.round(top / travel * maxScroll()), 0, maxScroll());
        if(row != scrollRow)
        {
            scrollRow = row;
            rebuild();
        }
    }

    // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if(event.button() == 0 && openList != null
            && grabScrollbar(event.x(), event.y()))
        {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if(scrollGrab >= 0)
        {
            dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        scrollGrab = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY)
    {
        if(openList != null && scrolls() && deltaY != 0D)
        {
            scrollBy(deltaY > 0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private String title()
    {
        if(DuelBotEntity.STRUCTURE.equals(openList))
        {
            return "Structure decks";
        }
        if(DuelBotEntity.CUSTOM.equals(openList))
        {
            return "Your decks";
        }
        if(DuelBotEntity.STARTER.equals(openList))
        {
            return "Starter decks";
        }
        return "Choose your program";
    }

    private String hint()
    {
        if(openList == null)
        {
            // Nothing. The title says Choose your program and three buttons
            // below it name the three programs, so a line asking the question
            // in words was the third thing on screen saying the same thing.
            return "";
        }
        int count = choices().size();
        if(count == 0)
        {
            return DuelBotEntity.CUSTOM.equals(openList)
                ? "You have not built any decks yet" : "None available";
        }
        return count + (count == 1 ? " deck" : " decks");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX,
        int mouseY, float partialTick)
    {
        // COBALT, WHATEVER THE PLAYER CHOSE ELSEWHERE.
        //
        // The thing asking the question is a machine, and the hub's palette is
        // the human half of the interface. Pinned rather than drawn from its
        // own textures, so the Duel Bot gets the whole theme -- panel, buttons,
        // scrollbar and every colour of type -- from one line.
        //
        // In a finally, and around super.extractRenderState too: the widgets
        // paint in there, and an exception part way through a frame must not
        // leave every other menu in the game wearing blue.
        MenuThemes.pin(MenuTheme.COBALT);
        try
        {
        // fillGradient, not extractBackground: that one BLURS, the blur may
        // only run once a frame, and a screen opening over one that already
        // asked for it took the client down. Every screen here settled on this.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int x = panelX();
        int y = panelY();
        NineSlice.draw(extractor, HubTextures.PANEL, x, y, panelW(), panelH());

        String title = title();
        extractor.text(font, title, x + (panelW() - font.width(title)) / 2, y + 12,
            MenuInk.title(), MenuInk.shadow());
        String hint = hint();
        if(!hint.isEmpty())
        {
            extractor.text(font, hint, x + (panelW() - font.width(hint)) / 2, y + 26,
                MenuInk.body(), MenuInk.shadow());
        }

        if(openList != null && scrolls())
        {
            NineSlice.draw(extractor, HubTextures.SCROLLBAR, trackX(), listTop(),
                4, listH(), 0, 2);
            NineSlice.draw(extractor, HubTextures.SCROLLBAR, trackX(), thumbY(),
                4, thumbH(), 1, 2);
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        }
        finally
        {
            MenuThemes.unpin();
        }
    }

    /** Escape backs out of a list before it closes the whole question. */
    @Override
    public void onClose()
    {
        if(openList != null)
        {
            back();
            return;
        }
        minecraft.setScreenAndShow(null);
    }

    /** The world carries on behind it; this is a question, not a pause. */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
