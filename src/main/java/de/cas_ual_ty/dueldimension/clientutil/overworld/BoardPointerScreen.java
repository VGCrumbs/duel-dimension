package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.DuelActionController;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PromptOptions;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Pointing at the board with a freed cursor.
 * <p>
 * The act key hands over the MOUSE, it does not put a list on the screen. While
 * this is open the camera stops turning, the cursor appears, and the board is
 * still there to be pointed at -- which is §9's interface state, and the reason
 * the board itself is not a screen: with nothing open the mouse turns the head,
 * and with this open it points.
 * <p>
 * A screen is how the cursor is freed, because that is how the game frees it.
 * But this one is a pane of glass: no dim, no panel, no rows until a card is
 * clicked that has more than one thing that could be done to it. The world is
 * the interface; this only lends it a pointer.
 * <p>
 * Nothing here decides what is legal. The engine sent the options, and
 * {@link PromptOptions} says which of them are about the card under the cursor.
 */
public class BoardPointerScreen extends Screen
{
    /** What the cursor is over, recomputed as it moves. */
    private BoardTarget hovered;
    /** Which card of the hand the cursor is over, or -1. */
    private int hoveredCard = -1;
    /** The options for a card that was clicked and had more than one. */
    private List<Integer> choices = List.of();
    private int choicesX;
    private int choicesY;

    private static final int ROW_H = 14;
    private static final int ROW_W = 132;

    public BoardPointerScreen()
    {
        super(Component.literal("Duel"));
    }

    /** How far down the board the cursor can reach, in blocks. */
    private static final double REACH = 32D;

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /**
     * No blur, and barely any dim.
     * <p>
     * The default draws a blurred, darkened copy of the world behind a screen,
     * which is right for a menu that replaces what is behind it and wrong for
     * this one: what is behind it is the board being played on, and blurring
     * the thing the cursor is pointing at defeats the pointer. Overridden here
     * rather than left to the base class, which reaches
     * {@code extractBlurredBackground} through {@code extractBackground}.
     * <p>
     * A trace of shade stays, so the cards and the rows read against a bright
     * sky without hiding the board under them.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        extractor.fillGradient(0, 0, width, height, 0x18101010, 0x28101010);
    }

    /**
     * The world ray under the cursor.
     * <p>
     * Built from the camera's own facing and field of view rather than by
     * unprojecting a matrix, because the two things needed -- where the camera
     * is and which way it looks -- are both public on {@link Camera}, and a
     * matrix taken from the render thread mid-frame is not.
     */
    private Vec3 rayThroughCursor(double mouseX, double mouseY)
    {
        Camera camera = minecraft.gameRenderer.mainCamera();
        float yaw = camera.yRot();
        float pitch = camera.xRot();

        Vec3 look = viewVector(pitch, yaw);
        // The two axes across the view, taken as view vectors of their own so
        // the handedness comes from the same formula as the forward one and
        // cannot be got backwards independently of it.
        Vec3 right = viewVector(0F, yaw + 90F);
        Vec3 up = viewVector(pitch - 90F, yaw);

        double half = Math.tan(Math.toRadians(camera.getFov()) / 2D);
        double aspect = (double)width / Math.max(1, height);
        // Gui coordinates rather than pixels: the ratio is the same either way,
        // and this avoids caring what the gui scale happens to be.
        double ndcX = 2D * mouseX / Math.max(1, width) - 1D;
        double ndcY = 1D - 2D * mouseY / Math.max(1, height);

        return look.add(right.scale(ndcX * aspect * half)).add(up.scale(ndcY * half)).normalize();
    }

    /** Minecraft's own view-vector formula, from {@code Entity.calculateViewVector}. */
    private static Vec3 viewVector(float pitch, float yaw)
    {
        float f = pitch * ((float)Math.PI / 180F);
        float g = -yaw * ((float)Math.PI / 180F);
        float cosYaw = net.minecraft.util.Mth.cos(g);
        float sinYaw = net.minecraft.util.Mth.sin(g);
        float cosPitch = net.minecraft.util.Mth.cos(f);
        float sinPitch = net.minecraft.util.Mth.sin(f);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    /**
     * The cards in your own hand, as things that can be pointed at.
     * <p>
     * A hand is not on the board -- it must never be, since the board is
     * visible to anyone who walks up to it -- but it is still where half a
     * turn's decisions are made, so it has to be clickable or the pointer can
     * only play cards that are already down.
     */
    private BoardTarget handTargetAt(double mouseX, double mouseY)
    {
        BoardSnapshot board = DuelClientState.board;
        if(board == null || board.self() == null || ClientDuelField.seat() < 0)
        {
            return null;
        }
        List<BoardSnapshot.Slot> hand = board.self().hand();
        if(hand == null || hand.isEmpty())
        {
            return null;
        }
        HandLayout.Slot[] slots = HandLayout.slots(width, height, hand.size());
        hoveredCard = HandLayout.at(slots, mouseX, mouseY);
        if(hoveredCard < 0)
        {
            return null;
        }
        BoardSnapshot.Slot card = hand.get(hoveredCard);
        // Controller 0: the engine numbers the seat it is asking, and this is
        // that seat's own hand. The same convention BoardRenderer's hand hits
        // use, so the legality filter matches them identically.
        return new BoardTarget(card.code(), 0, OcgConstants.LOCATION_HAND, hoveredCard, -1,
            "Hand", 1, card.art());
    }

    private void updateHover(double mouseX, double mouseY)
    {
        // The hand is drawn over the board, so a cursor on a card in hand is
        // pointing at that card and not at whatever zone is behind it.
        hoveredCard = -1;
        BoardTarget inHand = handTargetAt(mouseX, mouseY);
        if(inHand != null)
        {
            hovered = inHand;
            ClientDuelTargeting.point(null);
            return;
        }

        FieldSiting siting = ClientDuelField.siting();
        if(siting == null || minecraft.player == null)
        {
            hovered = null;
            return;
        }
        FieldTransform transform = new FieldTransform(siting);
        Vec3 from = minecraft.gameRenderer.mainCamera().position();
        float[] field = BoardPicker.aim(transform, from, rayThroughCursor(mouseX, mouseY), REACH);
        hovered = BoardPicker.at(ClientDuelField.boardToDraw(),
            Math.max(0, ClientDuelField.seat()), field);
        // Tell the board, so the zone under the cursor lights up out there
        // rather than only in here. The highlight is the whole feedback that
        // pointing is working.
        ClientDuelTargeting.point(hovered);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        // A click inside an open list picks from it; anywhere else dismisses it
        // and starts again from whatever is under the cursor.
        if(!choices.isEmpty())
        {
            int row = (int)((event.y() - choicesY) / ROW_H);
            if(event.x() >= choicesX && event.x() < choicesX + ROW_W
                && row >= 0 && row < choices.size())
            {
                answer(choices.get(row));
                return true;
            }
            choices = List.of();
            return true;
        }

        // Right-click declines, the same as it does on the duel screen, so
        // passing a chain window is one click and not a hunt for a row.
        if(event.button() == 1 && canDecline())
        {
            decline();
            return true;
        }

        // The phase bar first: ending a turn IS the phase bar, and it is drawn
        // over everything else at the top of the screen.
        int phase = DuelHud.phaseAt(width, event.x(), event.y());
        int phaseOption = DuelHud.optionForPhase(phase);
        if(phaseOption >= 0)
        {
            answer(phaseOption);
            return true;
        }

        updateHover(event.x(), event.y());

        // Your own deck is where the duel's own controls live, the way the 2D
        // board puts them there: looking at it is looking at your deck, and
        // conceding is a thing you do to your own deck rather than a button
        // sitting next to the cards you click all turn.
        if(hovered != null && hovered.isPile() && hovered.controller() == 0
            && hovered.location() == OcgConstants.LOCATION_DECK)
        {
            openChoices(List.of(VIEW_DECK, SURRENDER), event.x(), event.y());
            return true;
        }

        List<Integer> options = PromptOptions.optionsFor(DuelClientState.prompt, false, hovered);
        if(options.isEmpty())
        {
            return super.mouseClicked(event, doubled);
        }
        if(options.size() == 1)
        {
            // One legal thing to do with it, so pointing at it and clicking IS
            // the instruction. Asking which of one is a dialog for its own sake.
            answer(options.get(0));
            return true;
        }
        openChoices(options, event.x(), event.y());
        return true;
    }

    /**
     * Declining, which is not one of the engine's options: it is an EMPTY
     * answer.
     * <p>
     * The 2D screen synthesises this in six places -- a Cancel button, Escape,
     * a held right-click during a chain window -- and the board had none of
     * them, so any prompt a duellist was allowed to pass on simply had no
     * answer available. A chain window is exactly that prompt, and it arrives
     * constantly: the duel would sit parked on a question with no way to say
     * "nothing, thank you".
     */
    private void decline()
    {
        DuelActionController.answer(new int[0], 0);
        choices = List.of();
        onClose();
    }

    /** Is the engine willing to take "nothing" for an answer right now? */
    private static boolean canDecline()
    {
        EnginePrompt prompt = DuelClientState.prompt;
        return prompt != null && prompt.cancelable();
    }


    /** Not an option index: the answer that is no options at all. */
    private static final int DECLINE = -1;
    /** Nor this one: conceding the duel outright. */
    private static final int SURRENDER = -2;
    /** Nor this: looking through your own deck, which the server shuffles first. */
    private static final int VIEW_DECK = -3;

    /**
     * Surrender is armed by one click and taken by the next.
     * <p>
     * Every other row here is recoverable -- a wrong option loses a play, and
     * the duel goes on. This one ends the game, and it sits a few pixels from
     * the rows a duellist clicks all turn, so it asks twice.
     */
    private boolean surrenderArmed;

    private void answer(int index)
    {
        if(index == VIEW_DECK)
        {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ViewOwnDeck());
            choices = List.of();
            onClose();
            return;
        }
        if(index == SURRENDER)
        {
            if(!surrenderArmed)
            {
                surrenderArmed = true;
                return;
            }
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender());
            onClose();
            return;
        }
        surrenderArmed = false;
        if(index == DECLINE)
        {
            decline();
            return;
        }
        // The shared sender, which quotes the prompt's serial back: an answer
        // with a stale serial is dropped in silence and the duel thread stays
        // parked on it.
        DuelActionController.answer(new int[] {index}, 0);
        choices = List.of();
        onClose();
    }

    /** The act key closes it again, so one key both takes and returns the mouse. */
    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if(HubKeybinds.DUEL_ACT.matches(event))
        {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        // No dim and no panel. The board behind this is the thing being used;
        // every other screen in the mod covers what it replaces, and this one
        // replaces nothing.
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        if(choices.isEmpty())
        {
            updateHover(mouseX, mouseY);
        }


        // The HUD does not run while a screen is open, so the pointer draws
        // both the hand and the instruments itself -- otherwise the cards, the
        // life bars and the phase bar all disappear at the moment the cursor
        // arrives to use them.
        BoardSnapshot board = DuelClientState.board;
        if(board != null && ClientDuelField.seat() >= 0)
        {
            DuelHud.draw(extractor, font, board, ClientDuelField.seat());
            HandHud.drawHand(extractor, board, hoveredCard);
        }

        // Last, and never INSTEAD of the rest. Opening a menu used to return
        // before the hand and the instruments were drawn, so the moment a card
        // offered a choice the whole hand vanished behind the menu asking about
        // it -- which is the opposite of what a contextual menu is for.
        if(!choices.isEmpty())
        {
            drawChoices(extractor);
        }

        // Shift shows the card's own words, the same as the deck builder's
        // preview and for the same reason: the wording is what a duellist is
        // squinting at mid-turn, and the card is already on screen at the size
        // the board draws it.
        if(hovered != null && shiftHeld())
        {
            CardBubble.draw(extractor, font, hovered.code(), mouseX, mouseY, width, height);
        }

        // No label and no strip. What a zone is called is written on the board
        // in front of the player, and the things that are not on the board are
        // reachable where they belong: a phase on the phase bar, the duel's own
        // controls on the deck, and anything else in the same popup a card uses.
    }

    /** The board-less options, in the same popup a card's actions use. */
    private void openLoose(double mouseX, double mouseY)
    {
        List<Integer> loose = PromptOptions.looseOptions(DuelClientState.prompt, false);
        if(loose.isEmpty())
        {
            return;
        }
        if(loose.size() == 1)
        {
            answer(loose.get(0));
            return;
        }
        openChoices(loose, mouseX, mouseY);
    }

    /**
     * Opens a menu beside the cursor, kept on the screen.
     * <p>
     * Anchored at the click and then pulled back inside the window, the same as
     * the deck builder's preview and the duel screen's own menu: a menu that
     * opens under the bottom edge is a menu whose last row cannot be clicked,
     * and the rows nearest the bottom are the ones a hand card produces.
     */
    private void openChoices(List<Integer> rows, double mouseX, double mouseY)
    {
        choices = rows;
        int tall = rows.size() * ROW_H;
        choicesX = (int)Math.max(4, Math.min(mouseX, width - ROW_W - 4));
        choicesY = (int)Math.max(4, Math.min(mouseY, height - tall - 4));
    }

    private void drawChoices(GuiGraphicsExtractor extractor)
    {
        for(int row = 0; row < choices.size(); row++)
        {
            int y = choicesY + row * ROW_H;
            extractor.fill(choicesX, y, choicesX + ROW_W, y + ROW_H, 0xE0101820);
            extractor.text(font, label(choices.get(row)), choicesX + 4, y + 3, 0xFFF4D089, false);
        }
    }





    private String label(int index)
    {
        if(index == VIEW_DECK)
        {
            return "View deck";
        }
        if(index == SURRENDER)
        {
            return surrenderArmed ? "Surrender -- click again" : "Surrender";
        }
        if(index == DECLINE)
        {
            return "Pass";
        }
        var prompt = DuelClientState.prompt;
        return prompt == null || index < 0 || index >= prompt.options().size() ? "?"
            : prompt.options().get(index).label();
    }

    /**
     * Shift, asked of the window rather than of a key event, because this is a
     * question about a key being HELD while the mouse moves and not about one
     * having been pressed. The same test the deck builder's preview uses.
     */
    private static boolean shiftHeld()
    {
        com.mojang.blaze3d.platform.Window window =
            net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onClose()
    {
        // The board stops being pointed at when the pointer goes away, or the
        // last hovered zone would stay lit with nothing hovering it.
        ClientDuelTargeting.point(null);
        minecraft.setScreenAndShow(null);
    }
}
