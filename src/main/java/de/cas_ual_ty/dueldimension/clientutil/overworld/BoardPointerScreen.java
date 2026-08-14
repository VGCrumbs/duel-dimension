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
 * This is where a duellist LIVES. A duel is an hour of pointing at cards and a
 * few seconds of looking around, so the cursor is the resting state and the
 * camera is what has to be asked for -- hold the camera key and this goes away
 * for as long as it is held. The board is still there to be pointed at either
 * way, which is §9's interface state and the reason the board itself is not a
 * screen: with this open the mouse points, and with it closed it turns the
 * head.
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
    /**
     * True when this pointer was BORROWED to answer one question, rather than
     * being the duel's own resting cursor.
     * <p>
     * A borrowed pointer is one the player asked for from camera mode: they
     * were holding the camera key, clicked a card, and this opened over the top
     * of it carrying that card's menu. It has to survive the key still being
     * held -- otherwise the tick that watches the key would take it away on the
     * very frame it appeared -- and it gives the camera straight back once the
     * question is answered.
     * <p>
     * The resting cursor is neither: it is what a duel looks like when nobody
     * is holding anything, so answering a question with it leaves it exactly
     * where it was.
     */
    private boolean pinned;

    /** A card whose menu should be open the moment this appears, or null. */
    private BoardTarget opening;
    /** That card's rows, or the loose rows for a question about no card. */
    private List<Integer> openingOptions = List.of();

    /** What the cursor is over, recomputed as it moves. */
    private BoardTarget hovered;
    /** Which card of the hand the cursor is over, or -1. */
    private int hoveredCard = -1;
    /** The options for a card that was clicked and had more than one. */
    private List<Integer> choices = List.of();
    private int choicesX;
    private int choicesY;

    /**
     * The duel screen's own menu metrics: rows 15 apart, each two shorter than
     * its pitch, and never narrower than 70. Copied rather than chosen so the
     * menu on the board is the menu a player already knows -- the same rows,
     * the same icons, the same colours, in the same order.
     */
    private static final int ROW_H = 15;
    private static final int ROW_INSET = 2;
    private static final int ROW_W_MIN = 70;
    /** Room for the command icon and the padding either side of the label. */
    private static final int ROW_LABEL_PAD = 30;

    /** Width of the menu currently open, measured from its widest row. */
    private int choicesW = ROW_W_MIN;
    /** The card the open menu is about, which decides its icons' posture. */
    private BoardTarget chosenAnchor;

    /** The duel's resting cursor: no menu up, and it stays until the key takes it. */
    public BoardPointerScreen()
    {
        super(Component.literal("Duel"));
    }

    /**
     * Opens straight onto a menu, borrowed from camera mode.
     * <p>
     * The player has already pointed at the card with their whole head and
     * clicked it. Handing them a cursor and asking them to find the same card
     * again would be asking the same question twice -- so the menu is already
     * open, already about that card, and already where the card is.
     *
     * @param target the card the menu is about, or null for a question that is
     *               about no card at all
     */
    public BoardPointerScreen(BoardTarget target, List<Integer> options)
    {
        super(Component.literal("Duel"));
        this.pinned = true;
        this.opening = target;
        this.openingOptions = options;
    }

    /** Was this borrowed for one question, rather than being the duel's cursor? */
    public boolean isPinned()
    {
        return pinned;
    }

    @Override
    protected void init()
    {
        if(!openingOptions.isEmpty())
        {
            hovered = opening;
            // Centre as the fallback anchor, for a question about no card.
            openChoices(openingOptions, width / 2D, height / 2D);
            opening = null;
            openingOptions = List.of();
        }
    }

    /**
     * What happens once a question has been answered.
     * <p>
     * A borrowed pointer hands the camera back, because that is the whole of
     * what it was for. The resting cursor only closes the menu: closing the
     * cursor itself would have the tick reopen it a frame later, which is a
     * flicker and a lost click.
     */
    private void dismiss()
    {
        choices = List.of();
        chosenAnchor = null;
        surrenderArmed = false;
        if(pinned)
        {
            onClose();
        }
    }

    /** How far down the board the cursor can reach, in blocks. */
    private static final double REACH = 32D;

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /**
     * No blur, and no dim.
     * <p>
     * The default draws a blurred, darkened copy of the world behind a screen,
     * which is right for a menu that replaces what is behind it and wrong for
     * this one: what is behind it is the board being played on, and blurring
     * the thing the cursor is pointing at defeats the pointer. Overridden here
     * rather than left to the base class, which reaches
     * {@code extractBlurredBackground} through {@code extractBackground}.
     * <p>
     * Nothing is drawn in its place either: a dim that appears the instant the
     * cursor does is a step change in brightness, and the point of freeing the
     * mouse is that it is the same view with a pointer in it.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        // Nothing at all. Even a trace of shade is a step change the moment the
        // cursor appears, and freeing the mouse is meant to be the same view
        // with a pointer in it rather than a different screen. The rows and the
        // hand carry their own backing where they need one.
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
        // Before everything, including an open menu. A way out that only worked
        // when no menu happened to be sitting over it is a way out a player
        // cannot trust -- and the menu is part of the action being backed out
        // of, not something in the way of it.
        int[] cancel = DuelHud.cancelBounds(width, height, !choices.isEmpty());
        if(cancel != null && event.x() >= cancel[0] && event.x() < cancel[0] + cancel[2]
            && event.y() >= cancel[1] && event.y() < cancel[1] + cancel[3])
        {
            // One step at a time. With a menu up, the card that was clicked is
            // what gets let go of -- not the whole prompt, which the player has
            // not answered yet and may still want to. With nothing up, and only
            // if the engine will take it, the prompt itself is declined.
            if(!choices.isEmpty())
            {
                dismiss();
                return true;
            }
            decline();
            return true;
        }

        // A click inside an open list picks from it; anywhere else dismisses it
        // and starts again from whatever is under the cursor.
        if(!choices.isEmpty())
        {
            int row = (int)((event.y() - choicesY) / ROW_H);
            if(event.x() >= choicesX && event.x() < choicesX + choicesW
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
        int phase = DuelHud.phaseAt(width, height, event.x(), event.y());
        int phaseOption = DuelHud.optionForPhase(phase);
        if(phaseOption >= 0)
        {
            answer(phaseOption);
            return true;
        }

        updateHover(event.x(), event.y());

        List<Integer> options = PromptOptions.optionsFor(DuelClientState.prompt, false, hovered);

        // Your own deck is where the duel's own controls live, the way the 2D
        // board puts them there: looking at it is looking at your deck, and
        // conceding is a thing you do to your own deck rather than a button
        // sitting next to the cards you click all turn.
        //
        // Only when the duel is not asking about it, though. A prompt can offer
        // cards from inside the deck -- "add one of these to your hand" -- and
        // those are answered by clicking the very same stack. Taking this
        // branch first put View Deck and Surrender in front of the question and
        // left no way at all to answer it.
        if(options.isEmpty() && hovered != null && hovered.isPile() && hovered.controller() == 0
            && hovered.location() == OcgConstants.LOCATION_DECK)
        {
            openChoices(List.of(VIEW_DECK, SURRENDER), event.x(), event.y());
            return true;
        }

        if(options.isEmpty())
        {
            return super.mouseClicked(event, doubled);
        }
        // An empty square with one thing to do is not a menu. Clicking it
        // has already SAID the thing -- "here" is the whole of the answer to
        // "where do you want it" -- so a list offering one row called "place
        // here" is a dialog asking a question that has just been answered.
        if(options.size() == 1 && !hovered.isPile() && !hovered.hasCard())
        {
            answer(options.get(0));
            return true;
        }
        // A card always asks, even with one row. A trap in hand can be Set and
        // nothing else, so a click on one used to Set it outright -- no menu,
        // no confirmation, and a card face-down on the field because a cursor
        // was a few pixels off. The menu costs one click and buys the chance to
        // change your mind, which on a board you point at with your head is
        // worth every bit of it.
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
        dismiss();
    }

    /** Is the engine willing to take "nothing" for an answer right now? */
    private static boolean canDecline()
    {
        return PromptOptions.canDecline(DuelClientState.prompt);
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
            // The deck list is a screen of its own, so this one steps aside
            // whether it was borrowed or not.
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
            dismiss();
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
        dismiss();
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        // Escape means "put this away", and the resting cursor is not a thing
        // that can be put away -- closing it only opens it again on the next
        // tick. So it dismisses an open menu, and with no menu open it reaches
        // the pause screen, which would otherwise be unreachable for the whole
        // duel.
        if(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            if(!choices.isEmpty())
            {
                dismiss();
                return true;
            }
            if(pinned)
            {
                onClose();
                return true;
            }
            // Stand down FIRST, then ask. Gui.setPauseScreen returns on the
            // spot if a screen is already open, and the cursor is one -- so
            // asking politely from inside it did precisely nothing, and Escape
            // became a dead key for the whole duel. Asked this way round the
            // pause screen is built by the game's own code, with the sound and
            // the singleplayer pause that belong to it.
            onClose();
            minecraft.pauseGame(false);
            return true;
        }
        // Swapping between the board and the duel screen is a key the tick
        // handler polls, and a key mapping is not polled at all while a screen
        // is open. The cursor is now open for the whole duel, so the swap
        // stopped working the moment it became the resting state -- the same
        // trap the duel screen already sidesteps by handling this key itself.
        if(HubKeybinds.DUEL_VIEW.matches(event))
        {
            ClientDuelField.toggleScreen(minecraft);
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
            // Only here, and not from the HUD. The HUD is what a duel looks
            // like while the camera key is held, and a button drawn where there
            // is no cursor to click it with is a promise the screen cannot
            // keep.
            DuelHud.drawCancel(extractor, font, mouseX, mouseY, !choices.isEmpty());
        }

        // Last, and never INSTEAD of the rest. Opening a menu used to return
        // before the hand and the instruments were drawn, so the moment a card
        // offered a choice the whole hand vanished behind the menu asking about
        // it -- which is the opposite of what a contextual menu is for.
        if(!choices.isEmpty())
        {
            drawChoices(extractor, mouseX, mouseY);
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
        chosenAnchor = hovered;
        choicesW = ROW_W_MIN;
        for(int index : rows)
        {
            choicesW = Math.max(choicesW, font.width(label(index)) + ROW_LABEL_PAD);
        }
        int tall = rows.size() * ROW_H;

        // Anchored to the CARD, as the duel screen anchors it: above the thing
        // being acted on and centred over it, so the card stays visible while
        // its own menu is being read. The cursor is only the fallback, for a
        // card the camera cannot see the middle of.
        int[] anchor = anchorOf(chosenAnchor, mouseX, mouseY);
        int cardX = anchor[0];
        int cardTop = anchor[1];
        int cardBottom = anchor[2];

        choicesX = Math.max(4, Math.min(cardX - choicesW / 2, width - choicesW - 4));
        int above = cardTop - tall - 4;
        // No room above, so below instead -- the screen's own rule, and for the
        // same reason: a menu clipped by the top edge is a menu with rows that
        // cannot be clicked.
        choicesY = above >= DuelHud.below(width, height) ? above : cardBottom + 4;
        choicesY = Math.max(4, Math.min(choicesY, height - tall - 4));
    }

    /**
     * Where on the screen a target is, as {x centre, top, bottom}.
     * <p>
     * A card in the hand knows its own rectangle. A card on the board is
     * somewhere in the world, so its middle is projected back to the screen by
     * REVERSING the very formula the picker uses to turn a cursor into a ray --
     * which means the anchor and the pick cannot disagree about where a card
     * is, whatever the camera is doing.
     */
    private int[] anchorOf(BoardTarget target, double mouseX, double mouseY)
    {
        int[] fallback = {(int)mouseX, (int)mouseY, (int)mouseY};
        if(target == null)
        {
            return fallback;
        }
        if(target.location() == OcgConstants.LOCATION_HAND)
        {
            BoardSnapshot board = DuelClientState.board;
            List<BoardSnapshot.Slot> hand = board == null || board.self() == null ? null
                : board.self().hand();
            if(hand == null || target.sequence() < 0 || target.sequence() >= hand.size())
            {
                return fallback;
            }
            HandLayout.Slot slot = HandLayout.slots(width, height, hand.size())[target.sequence()];
            return new int[] {slot.x() + slot.width() / 2,
                slot.y() - HandLayout.hoverLift(height), slot.y() + slot.height()};
        }

        FieldSiting siting = ClientDuelField.siting();
        if(siting == null)
        {
            return fallback;
        }
        FieldTransform transform = new FieldTransform(siting);
        int half = FieldTransform.controllerFor(Math.max(0, ClientDuelField.seat()),
            target.controller() == 0);
        de.cas_ual_ty.dueldimension.clientutil.FieldLayout.Rect zone =
            de.cas_ual_ty.dueldimension.clientutil.FieldLayout.zone(half, target.location(),
                Math.max(0, target.sequence()));
        if(zone == null)
        {
            return fallback;
        }
        double[] middle = project(transform.at(zone.x() + zone.w() / 2F,
            zone.y() + zone.h() / 2F, 0D));
        if(middle == null)
        {
            return fallback;
        }
        // A card is about a card's height on screen; taking half of it either
        // way gives the menu something to sit above or below.
        int half2 = Math.max(12, HandLayout.cardHeight(height) / 3);
        return new int[] {(int)middle[0], (int)middle[1] - half2, (int)middle[1] + half2};
    }

    /**
     * A world point as screen coordinates, or null when it is behind the
     * camera.
     * <p>
     * The exact inverse of {@code rayThroughCursor}: that turns a screen point
     * into a direction by adding the camera's right and up axes scaled by the
     * normalised coordinates, so this takes the direction apart along the same
     * three axes and divides out the same field of view.
     */
    private double[] project(Vec3 world)
    {
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 delta = world.subtract(camera.position());
        Vec3 look = viewVector(camera.xRot(), camera.yRot());
        Vec3 right = viewVector(0F, camera.yRot() + 90F);
        Vec3 up = viewVector(camera.xRot() - 90F, camera.yRot());

        double along = delta.dot(look);
        if(along <= 1e-4D)
        {
            return null;
        }
        double half = Math.tan(Math.toRadians(camera.getFov()) / 2D);
        double aspect = (double)width / Math.max(1, height);
        double ndcX = delta.dot(right) / along / (aspect * half);
        double ndcY = delta.dot(up) / along / half;
        return new double[] {(ndcX + 1D) * width / 2D, (1D - ndcY) * height / 2D};
    }

    /**
     * The menu, in the duel screen's own art: a dark row with a gold top edge,
     * the command's icon at its left, and the label beside it. The icon is what
     * makes a row readable at a glance -- a sword is an attack wherever it is
     * drawn -- so it comes from the same DuelTextures.commandIcon the screen
     * uses, told the same two things about the card it belongs to.
     */
    private void drawChoices(GuiGraphicsExtractor extractor, int mouseX, int mouseY)
    {
        int rowH = ROW_H - ROW_INSET;
        for(int row = 0; row < choices.size(); row++)
        {
            int index = choices.get(row);
            int x = choicesX;
            int y = choicesY + row * ROW_H;
            boolean over = mouseX >= x && mouseX < x + choicesW && mouseY >= y && mouseY < y + rowH;

            extractor.fill(x, y, x + choicesW, y + rowH, over ? 0xF0473A22 : 0xE01A1A1E);
            extractor.fill(x, y, x + choicesW, y + 1, over ? 0xFFFFD700 : 0x60FFD700);

            int textX = x + 5;
            net.minecraft.resources.Identifier icon = iconFor(index);
            if(icon != null)
            {
                int size = rowH - 4;
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(extractor, icon,
                    x + 3, y + 2, size, size);
                textX = x + 6 + size;
            }
            extractor.text(font, label(index), textX, y + (rowH - 8) / 2,
                over ? 0xFFFFFFCC : 0xFFE8E8E8, false);
        }
    }

    /**
     * The icon for a row, or null for the rows that are this mod's rather than
     * the engine's. Which posture and which face the card is showing decides
     * between the flip and set icons, exactly as it does on the screen.
     */
    private net.minecraft.resources.Identifier iconFor(int index)
    {
        EnginePrompt prompt = DuelClientState.prompt;
        if(index < 0 || prompt == null || index >= prompt.options().size())
        {
            return null;
        }
        boolean faceDown = false;
        boolean attackPosition = true;
        BoardSnapshot board = DuelClientState.board;
        if(chosenAnchor != null && !chosenAnchor.isPile() && board != null)
        {
            BoardSnapshot.Side side = chosenAnchor.controller() == 0 ? board.self()
                : board.opponent();
            List<BoardSnapshot.Slot> zone = side == null ? null
                : chosenAnchor.location() == OcgConstants.LOCATION_MZONE ? side.monsters()
                    : side.spells();
            if(zone != null && chosenAnchor.sequence() >= 0
                && chosenAnchor.sequence() < zone.size())
            {
                BoardSnapshot.Slot slot = zone.get(chosenAnchor.sequence());
                faceDown = slot.faceDown();
                attackPosition = !slot.defence();
            }
        }
        return de.cas_ual_ty.dueldimension.clientutil.DuelTextures.commandIcon(
            prompt.options().get(index).command(), faceDown, attackPosition);
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
        // gui.setScreen, not setScreenAndShow: the latter is that plus a forced
        // renderFrame, and a frame drawn in the middle of taking the cursor
        // back is a frame with the pointer half gone -- which is the flicker
        // felt on every hold and release. The same reason DuelClientState opens
        // the duel screen this way.
        minecraft.gui.setScreen(null);
    }
}
