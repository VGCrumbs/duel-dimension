package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.DuelActionController;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.DuelSelection;
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

    /**
     * True when the open list IS the prompt -- a Yes/No, or a posture -- rather
     * than a menu about something on the board.
     * <p>
     * Drawn differently because it is a different thing: centred, with the
     * question written above it, because there is no card for it to hang off
     * and nothing else on screen says what is being asked.
     */
    private boolean asking;

    /**
     * The pile a duellist has opened to read, and what to call it.
     * <p>
     * A graveyard is public and always has been -- the duel screen lets either
     * player read either one at any time, and a duellist who cannot check what
     * is in a graveyard is playing a different game. The board could not, so
     * the one thing the world board could not do that the screen could was the
     * most ordinary thing in a duel.
     */
    private List<BoardSnapshot.Slot> pileView = List.of();
    private String pileViewLabel = "";
    /**
     * The question last seen, by identity.
     * <p>
     * Kept so that a NEW question can be told from the same one still standing.
     * A duel spends most of a player's turn with a prompt up, so "is there a
     * question" is not the test -- it would slam a graveyard shut the instant it
     * was opened. Each question arrives as its own object, so a changed
     * reference is a changed question.
     */
    private EnginePrompt lastPrompt;

    /**
     * When the open menu is a pile's, the options each of its rows stands for.
     * <p>
     * A pile's rows are verbs rather than cards, so a row does not answer the
     * prompt -- it names a group of cards the verb could mean, which the duel
     * screen's picker then shows. Null whenever the menu is an ordinary card's.
     */
    private List<List<Integer>> pileGroups;

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
     * A question with nowhere to point at opens itself.
     * <p>
     * Nothing on the board could ever open it -- that is what makes it this
     * kind of question -- so waiting for a click would be waiting forever, and
     * waiting forever is exactly what used to send these to the duel screen.
     * Reopened if dismissed while it still stands, because a duel does not go
     * on until it is answered and a list nobody can get back is a duel nobody
     * can finish.
     */
    @Override
    public void tick()
    {
        DuelSelection.sync(DuelClientState.prompt);
        // The pile viewer draws through the same grid and shares its scroll
        // position, so it counts as open for this: resetting under it would put
        // a duellist reading the bottom of a graveyard back at the top on the
        // next tick, every tick.
        if(!CardChooser.open() && pileView.isEmpty())
        {
            CardChooser.reset();
        }

        // Holding the right button waves chain windows through, which is what
        // the caption under one has been promising. A long chain asks the same
        // question after every link, and a player who has decided not to
        // respond to any of it should be able to say so once by holding rather
        // than clicking through each in turn. Only skippable windows go: a
        // forced response is not a question, and the core would refuse an empty
        // answer to it. The duel screen's rule exactly, and its reasons.
        EnginePrompt open = DuelClientState.prompt;
        // A new question displaces whatever was being read.
        //
        // The pile viewer draws over the menu and the chooser draws over both,
        // so an effect asking something while a graveyard was open left the
        // duellist looking at the graveyard with an unanswered question
        // underneath it -- and the duel waiting on them. What the engine has
        // just asked is the more urgent of the two, so the browsing gives way.
        if(open != lastPrompt)
        {
            lastPrompt = open;
            if(open != null && !pileView.isEmpty())
            {
                pileView = List.of();
                pileViewLabel = "";
                // Safe to reset here and nowhere else in this method: the pile
                // that shared the scroll position has just been closed, so
                // there is nothing left to scroll out from under.
                CardChooser.reset();
            }
        }
        if(open != null && open.chainWindow() && open.cancelable() && rightButtonHeld())
        {
            decline();
            return;
        }

        // The answer to "View Deck", which arrives whenever the server gets
        // round to it rather than on the click: unlike every other pile the
        // deck's contents are not in the board packet, they are asked for and
        // shuffled before they are sent. Taken rather than read, so one answer
        // opens the panel exactly once -- and read HERE as well as in the duel
        // screen, because the board is where it was asked for.
        List<BoardSnapshot.Slot> deck = DuelClientState.deckView;
        if(deck != null)
        {
            DuelClientState.deckView = null;
            if(!deck.isEmpty())
            {
                pileView = List.copyOf(deck);
                // The same wording the deck's own pile label carries, and the
                // same number: the list IS the count, so the two cannot drift.
                pileViewLabel = "Deck (" + deck.size() + ")";
                CardChooser.reset();
            }
        }

        if(choices.isEmpty() && PromptOptions.needsList(DuelClientState.prompt))
        {
            openQuestion(PromptOptions.unanchoredOptions(DuelClientState.prompt, false));
        }
    }

    /**
     * Whether the right mouse button is down right now.
     * <p>
     * Asked of the window rather than tracked from click events, exactly as the
     * duel screen asks it: the button may already have been held when the
     * prompt arrived, and a press that happened before this prompt existed
     * produces no event for it to have seen.
     */
    private boolean rightButtonHeld()
    {
        return minecraft != null && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
            minecraft.getWindow().handle(),
            org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /**
     * The prompt's own list: centred, and clear of both the instruments above
     * and the hand below, since it belongs to neither.
     */
    /**
     * The prompt's own words, and how to say no to them.
     * <p>
     * Only for a chain window that may be declined, and in the same words the
     * duel screen uses. Right-click has always passed one here, but a gesture
     * nobody is told about is a gesture nobody uses -- and a long chain asks
     * this after every link, which is precisely when it is worth knowing that
     * one held button answers all of them.
     */
    private static String caption(EnginePrompt prompt)
    {
        if(prompt == null)
        {
            return "";
        }
        return prompt.chainWindow() && prompt.cancelable()
            ? prompt.title() + "   [hold right-click to pass]" : prompt.title();
    }

    private void openQuestion(List<Integer> rows)
    {
        hovered = null;
        openChoices(rows, width / 2D, height / 2D);
        chosenAnchor = null;
        asking = true;
        choicesX = (width - choicesW) / 2;

        // Wrapped, not cut. The caption exists to say what is being agreed to,
        // and lopping the tail off a sentence leaves two rows reading Yes and
        // No under half a question -- with nothing to show that anything went
        // missing. Measured first, because the lines it takes are room the list
        // below has to make for it.
        questionLines = wrap(caption(DuelClientState.prompt), width - 16);
        int caption = questionLines.size() * LINE_H + QUESTION_GAP;
        int tall = rows.size() * ROW_H;
        // Clear of the instruments above AND the hand below. HandLayout knows
        // where the cards start for exactly this reason.
        int floor = HandLayout.topEdge(height) - 4;
        choicesY = Math.max(DuelHud.below(width, height) + caption,
            Math.min((height - tall) / 2, floor - tall));
    }

    /** Room above the list for the question itself. */
    private static final int QUESTION_GAP = 6;
    private static final int LINE_H = 10;

    /** The question, already broken into lines that fit. */
    private List<String> questionLines = List.of();

    /**
     * Breaks a sentence at its spaces into lines no wider than the room given.
     * <p>
     * A word longer than the whole line is left to overhang rather than being
     * broken mid-word: a card name split across two rows is harder to read than
     * one that runs a little wide, and card names are most of what these
     * questions are made of.
     */
    private List<String> wrap(String text, int room)
    {
        List<String> lines = new java.util.ArrayList<>();
        if(text == null || text.isEmpty())
        {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for(String word : text.split(" "))
        {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if(!line.isEmpty() && font.width(candidate) > room)
            {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
            else
            {
                line = new StringBuilder(candidate);
            }
        }
        if(!line.isEmpty())
        {
            lines.add(line.toString());
        }
        return lines;
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
        pileGroups = null;
        asking = false;
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
        return BoardProjection.viewVector(pitch, yaw);
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
        int[] confirm = DuelHud.confirmBounds(width, height);
        if(confirm != null && event.x() >= confirm[0] && event.x() < confirm[0] + confirm[2]
            && event.y() >= confirm[1] && event.y() < confirm[1] + confirm[3])
        {
            if(DuelSelection.ready(DuelClientState.prompt))
            {
                DuelActionController.answer(DuelSelection.answer(), 0);
                DuelSelection.clear();
                dismiss();
            }
            return true;
        }

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

        // A pile being read closes on any click. There is nothing in it to
        // pick -- it is a list, not a question -- so the first click a player
        // makes is them saying they have finished reading it.
        if(!pileView.isEmpty())
        {
            pileView = List.of();
            pileViewLabel = "";
            CardChooser.reset();
            return true;
        }

        // The card picker is modal while it is up: it covers the board, and a
        // click that fell through it would act on a card the player cannot see
        // and did not aim at.
        if(CardChooser.open())
        {
            int cell = CardChooser.at(CardChooser.optionsOf(DuelClientState.prompt).size(),
                width, height, event.x(), event.y());
            if(cell >= 0 && event.button() == 0)
            {
                answer(cell);
            }
            return true;
        }

        // A click inside an open list picks from it; anywhere else dismisses it
        // and starts again from whatever is under the cursor.
        if(!choices.isEmpty())
        {
            // Left button, and inside the list. Both mattered the moment the
            // list started opening BY ITSELF: it appears under wherever the
            // cursor was resting, so a reflexive right-click -- the gesture
            // that passes a chain window, used dozens of times a duel -- would
            // have activated the effect it meant to decline. And the cast
            // truncated towards zero, so the fifteen pixels ABOVE the list read
            // as row zero and picked "Yes" from empty board.
            int row = Math.floorDiv((int)event.y() - choicesY, ROW_H);
            if(event.button() == 0 && event.y() >= choicesY
                && event.x() >= choicesX && event.x() < choicesX + choicesW
                && row >= 0 && row < choices.size())
            {
                if(pileGroups != null && row < pileGroups.size())
                {
                    List<Integer> group = pileGroups.get(row);
                    String verb = label(group.get(0));
                    dismiss();
                    openPileList(verb, group);
                    return true;
                }
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
        // Reading a pile is what a pile does when the duel is not asking about
        // it. Your own deck is the exception below: it holds the duel's own
        // controls, and its contents are not yours to browse mid-duel anyway.
        if(options.isEmpty() && hovered != null && hovered.isPile() && hovered.count() > 0
            && CardChooser.viewable(hovered.location(), hovered.controller()))
        {
            openPileView(hovered);
            return true;
        }

        if(options.isEmpty() && isOwnDeck(hovered))
        {
            openChoices(deckMenu(), event.x(), event.y());
            return true;
        }

        if(options.isEmpty())
        {
            return super.mouseClicked(event, doubled);
        }
        // A pile is a stack of face-down cards, so what it can offer are VERBS
        // and not cards: three summonable monsters gave three rows all reading
        // "Special Summon" with nothing to tell them apart. Picking the verb
        // opens the list of cards it could mean -- always, even when there is
        // only one, because a player told "Special Summon" and then asked for
        // tributes has been made to pay for something they were never shown.
        // Exactly what the duel screen does, and for the same reason.
        //
        // Every click on a pile goes that way, INCLUDING a click that is one
        // pick of several.
        //
        // Toggling a pile by engine order was blind in both directions: nothing
        // was drawn to say which card had been taken, and DuelSelection.pick
        // hands back the first option not already chosen, so with three
        // candidates and a maximum of two there was no way to reach {2,3} from
        // {1,2}. A third click was a silent no-op and the card already taken
        // could not be given back by clicking the same stack. The only way out
        // was to cancel the whole prompt.
        //
        // The list has none of that trouble: each card is its own row, its own
        // tick, and its own option index, so clicking one toggles exactly the
        // card that was clicked. It is what the flat board has always done with
        // a pile, and it works here now that both share one running selection.
        if(hovered.isPile())
        {
            openPile(options, event.x(), event.y());
            return true;
        }

        // A prompt that wants SEVERAL things is answered by picking them and
        // saying so, not by picking one and being taken at your word. Every
        // click toggles, and the Confirm button in the corner is what ends it.
        if(DuelSelection.wantsSeveral(DuelClientState.prompt))
        {
            DuelSelection.toggle(DuelClientState.prompt, DuelSelection.pick(options));
            // A placement is its own confirmation: the engine asked for exactly
            // this many zones and they have all been named.
            if(DuelSelection.placementComplete(DuelClientState.prompt))
            {
                DuelActionController.answer(DuelSelection.answer(), 0);
                DuelSelection.clear();
                dismiss();
            }
            return true;
        }

        // Some clicks are already the whole answer: an empty square asked
        // "where", a tribute asked "which", an attack asked "what are you
        // hitting". Those go straight through. Anything a card can DO asks
        // first, even with one row, so a trap is not Set on the field and a
        // monster's battle position is not silently flipped by a cursor a few
        // pixels off. The duel screen's own test, so the two boards cannot come
        // to different conclusions about the same click.
        if(PromptOptions.answersOutright(DuelClientState.prompt, hovered, options))
        {
            answer(options.get(0));
            return true;
        }

        openChoices(options, event.x(), event.y());
        return true;
    }

    /**
     * The wheel, which reaches the bottom of a pile too tall to draw at once.
     * <p>
     * The pile viewer first, for the same reason mouseClicked tests it first:
     * it is drawn OVER the picker and the two are centred on the same point, so
     * the wheel belongs to whichever is on top. Exactly the duel screen's
     * order, which had this and the board did not -- a forty-card graveyard
     * simply ran off the bottom edge.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if(!pileView.isEmpty()
            && CardChooser.wheel(pileView.size(), width, height, delta))
        {
            return true;
        }
        if(CardChooser.open() && CardChooser.wheel(
            CardChooser.optionsOf(DuelClientState.prompt).size(), width, height, delta))
        {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
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
    /**
     * The duel's own controls, which belong to no prompt: they are asked of
     * your own deck, the way the duel screen asks them, rather than sitting in
     * a strip of buttons under the board.
     */
    public static java.util.List<Integer> deckMenu()
    {
        // Exactly the duel screen's rows, in its order and for its reasons.
        // View Deck first because it is the harmless one, and a menu whose
        // first row concedes the duel is a menu people learn not to open.
        // The chain setting and the music sit between them, which is where
        // EDOPro keeps the chain toggles too -- next to Surrender, as duel-long
        // preferences rather than plays. A board with no way to reach them left
        // a duellist unable to change how often the duel stopped to ask.
        return DuelClientState.over ? java.util.List.of(CLOSE)
            : java.util.List.of(VIEW_DECK, CHAIN_PREF, MUSIC, VOLUME, SURRENDER);
    }

    /** Is this target the deck those controls belong to -- yours? */
    public static boolean isOwnDeck(BoardTarget target)
    {
        return target != null && target.isPile() && target.controller() == 0
            && target.location() == OcgConstants.LOCATION_DECK;
    }

    private static final int DECLINE = -1;
    /** Nor this one: conceding the duel outright. */
    private static final int SURRENDER = -2;
    /** Nor this: looking through your own deck, which the server shuffles first. */
    private static final int VIEW_DECK = -3;

    /** Finished with a decided duel, which is what the deck offers once it is over. */
    private static final int CLOSE = -4;

    /** How often the duel should stop to ask about a chain. */
    private static final int CHAIN_PREF = -5;
    /** The duel music, on or off. */
    private static final int MUSIC = -6;
    /** And how loud, in steps, since a menu row cannot be a slider. */
    private static final int VOLUME = -7;

    /** One press of the volume row, as a fraction. */
    private static final float VOLUME_STEP = 0.2F;

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
        if(index == CLOSE)
        {
            // The duel screen's own ending, reached the same way: the reward
            // screen when there is one to collect, and the world back when
            // there is not.
            if(DuelClientState.hasReward())
            {
                de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result reward =
                    DuelClientState.takeReward();
                DuelClientState.reset();
                minecraft.gui.setScreen(new de.cas_ual_ty.dueldimension.clientutil.hub
                    .DuelResultScreen(reward));
                return;
            }
            DuelClientState.reset();
            onClose();
            return;
        }
        surrenderArmed = index == SURRENDER && surrenderArmed;
        if(index == VIEW_DECK)
        {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ViewOwnDeck());
            // And WAIT for it, with the menu put away. This used to close the
            // pointer instead, on the reasoning that the deck list is a screen
            // of its own -- but the only thing that reads the server's answer
            // is the duel screen's tick, and the duel screen is not the one
            // that asked. The row cost a duellist their cursor and showed them
            // nothing. The list opens here, in the same pile viewer a graveyard
            // opens in, as soon as the answer lands.
            choices = List.of();
            return;
        }
        // The three preferences leave the menu open: they are cycled rather
        // than chosen, and a menu that closed on each press would have to be
        // reopened to see what the press did. Re-measured, because the labels
        // they cycle through are not all the same width.
        if(index == CHAIN_PREF)
        {
            DuelClientState.chainPreference = DuelClientState.chainPreference.next();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference(
                    DuelClientState.chainPreference));
            remeasure();
            return;
        }
        if(index == MUSIC)
        {
            de.cas_ual_ty.dueldimension.clientutil.DuelMusic.toggleMuted();
            remeasure();
            return;
        }
        if(index == VOLUME)
        {
            // Round before stepping: the slider sets values a step never lands
            // on, and adding a fifth to 0.37 forever would leave the row
            // reading a number nobody chose.
            float now = Math.round(
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.volume() / VOLUME_STEP)
                * VOLUME_STEP;
            de.cas_ual_ty.dueldimension.clientutil.DuelMusic.setVolume(
                now >= 1F - 1e-3F ? 0F : now + VOLUME_STEP);
            remeasure();
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
            if(!pileView.isEmpty())
            {
                pileView = List.of();
                pileViewLabel = "";
                CardChooser.reset();
                return true;
            }
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
        // Chat, for the same reason and by the same trick.
        //
        // This cursor is open for the whole duel, and a screen holds the
        // keyboard -- so the game never reaches the point where it would notice
        // the chat key and open chat itself. A duel between two people is
        // exactly when they most want to say something, and it was the one
        // stretch of play where they could not.
        //
        // The cursor is the resting state and the tick handler puts it back, so
        // there is nothing to restore afterwards: closing chat returns to the
        // board on its own.
        if(minecraft.options.keyChat.matches(event))
        {
            minecraft.setScreenAndShow(
                new net.minecraft.client.gui.screens.ChatScreen("", false));
            return true;
        }
        if(minecraft.options.keyCommand.matches(event))
        {
            // Opened already carrying the slash, which is what the key means.
            minecraft.setScreenAndShow(
                new net.minecraft.client.gui.screens.ChatScreen("/", false));
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
            drawChoices(extractor, mouseX, mouseY);
        }

        if(!pileView.isEmpty())
        {
            drawPileView(extractor, mouseX, mouseY);
        }

        // Over everything, because it is the question rather than a note about
        // one: while it is up there is nothing else on screen to be doing.
        if(CardChooser.open())
        {
            CardChooser.draw(extractor, font, DuelClientState.prompt,
                CardChooser.optionsOf(DuelClientState.prompt), mouseX, mouseY);
        }

        // After the panels, not before them. Both the picker and the pile
        // viewer dim the whole window on their way in, and the HUD used to draw
        // these two first -- so Cancel and Confirm, the only way out of a
        // picker and the only way to finish a selection, sat behind the dim
        // that opening the picker had put there. Still only from here and never
        // from the HUD: the HUD is what a duel looks like while the camera key
        // is held, and a button drawn where there is no cursor to click it with
        // is a promise the screen cannot keep.
        if(board != null && ClientDuelField.seat() >= 0)
        {
            DuelHud.drawCancel(extractor, font, mouseX, mouseY, !choices.isEmpty());
            DuelHud.drawConfirm(extractor, font, mouseX, mouseY);
        }

        // Shift shows the card's own words, the same as the deck builder's
        // preview and for the same reason: the wording is what a duellist is
        // squinting at mid-turn, and the card is already on screen at the size
        // the board draws it.
        if(hovered != null && shiftHeld())
        {
            CardBubble.draw(extractor, font, hovered.code(), mouseX, mouseY, width, height,
                de.cas_ual_ty.dueldimension.clientutil.CardFacts.liveRace(hovered.controller(),
                    hovered.location(), hovered.sequence()));
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
        // Whatever the last menu's rows stood for, these are not it. Cleared
        // here rather than at each call site, so a menu can never be read
        // against the groups of the one before it.
        pileGroups = null;
        asking = false;
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
        // The hand is the floor, not the window. A menu that stopped at the
        // bottom edge was still written across the player's own cards -- and
        // because an open list is hit-tested before the hand, the cards
        // underneath stopped answering too.
        choicesY = Math.max(4, Math.min(choicesY,
            Math.max(4, HandLayout.topEdge(height) - 4 - tall)));
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
        return BoardProjection.project(world, width, height);
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
        if(asking)
        {
            drawQuestion(extractor);
        }
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
     * The question itself, in the panel art its answers are drawn in.
     * <p>
     * A card's menu needs no caption -- the card is right there under it, and
     * the rows say what can be done to it. A question has nothing under it at
     * all, so without this a duellist gets two unlabelled rows reading Yes and
     * No and no way to know what they are agreeing to.
     */
    private void drawQuestion(GuiGraphicsExtractor extractor)
    {
        if(questionLines.isEmpty())
        {
            return;
        }
        int widest = 0;
        for(String line : questionLines)
        {
            widest = Math.max(widest, font.width(line));
        }
        int top = choicesY - QUESTION_GAP - questionLines.size() * LINE_H;
        int left = (width - widest) / 2;
        extractor.fill(left - 5, top - 4, left + widest + 5,
            top + questionLines.size() * LINE_H + 1, 0xE01A1A1E);
        extractor.fill(left - 5, top - 4, left + widest + 5, top - 3, 0x60FFD700);
        for(int line = 0; line < questionLines.size(); line++)
        {
            String text = questionLines.get(line);
            extractor.text(font, text, (width - font.width(text)) / 2, top + line * LINE_H,
                0xFFFFE8A8, false);
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





    /**
     * A pile's menu: one row per verb, each opening the cards it could mean.
     * <p>
     * One verb and there is nothing to choose between, so the list opens
     * straight away -- the choosing that matters is WHICH card, and that is the
     * list itself.
     */
    private void openPile(List<Integer> options, double mouseX, double mouseY)
    {
        EnginePrompt prompt = DuelClientState.prompt;
        java.util.LinkedHashMap<Integer, List<Integer>> byCommand = new java.util.LinkedHashMap<>();
        for(int index : options)
        {
            byCommand.computeIfAbsent(prompt.options().get(index).command(),
                command -> new java.util.ArrayList<>()).add(index);
        }
        if(byCommand.size() == 1)
        {
            java.util.Map.Entry<Integer, List<Integer>> only =
                byCommand.entrySet().iterator().next();
            List<Integer> group = only.getValue();
            // A selection carries no verb, so its rows are cards and the list
            // is all of them -- heading it with whichever card happened to be
            // first told the player they were acting on that one. The stack's
            // own name is the truthful heading, which is what the flat board
            // settled on for the same reason.
            openPileList(only.getKey() == 0 && hovered != null ? hovered.label()
                : label(group.get(0)), group);
            return;
        }
        List<Integer> rows = new java.util.ArrayList<>();
        for(List<Integer> group : byCommand.values())
        {
            rows.add(group.get(0));
        }
        openChoices(rows, mouseX, mouseY);
        pileGroups = List.copyOf(byCommand.values());
    }

    /**
     * Hands the question to the duel screen's picker.
     * <p>
     * Not a second picker built on the board. The screen already draws a grid
     * of cards with their artwork, names and a way back, and a player choosing
     * between three monsters they cannot see needs exactly that -- so the board
     * steps aside for as long as the choosing takes and gets itself back the
     * moment it is done.
     */
    private void openPileList(String verb, List<Integer> group)
    {
        de.cas_ual_ty.dueldimension.clientutil.EngineDuelScreen screen =
            new de.cas_ual_ty.dueldimension.clientutil.EngineDuelScreen();
        minecraft.gui.setScreen(screen);
        screen.showPileChoices(verb, group);
    }

    /**
     * Opens a pile to be read.
     * <p>
     * Straight from the board snapshot, which already holds both graveyards and
     * both banished piles -- they are public, so the server sends them, and
     * nothing here has to ask for anything.
     */
    private void openPileView(BoardTarget target)
    {
        BoardSnapshot board = DuelClientState.board;
        if(board == null)
        {
            return;
        }
        BoardSnapshot.Side side = target.controller() == 0 ? board.self() : board.opponent();
        if(side == null)
        {
            return;
        }
        List<BoardSnapshot.Slot> cards = switch(target.location())
        {
            case OcgConstants.LOCATION_GRAVE -> side.grave();
            case OcgConstants.LOCATION_REMOVED -> side.banished();
            case OcgConstants.LOCATION_EXTRA -> side.extra();
            default -> List.of();
        };
        if(cards == null || cards.isEmpty())
        {
            return;
        }
        pileView = List.copyOf(cards);
        pileViewLabel = (target.controller() == 0 ? "Your " : "Opponent's ") + target.label();
        CardChooser.reset();
    }

    /**
     * The pile, in the picker's own grid.
     * <p>
     * Every face asked of {@link de.cas_ual_ty.dueldimension.clientutil.CardFaces}
     * rather than taken from the code, so a card banished FACE DOWN is a back
     * here exactly as it is on the board. A viewer that quietly showed one
     * would be a viewer that leaked it.
     */
    private void drawPileView(GuiGraphicsExtractor extractor, int mouseX, int mouseY)
    {
        List<net.minecraft.resources.Identifier> faces =
            new java.util.ArrayList<>(pileView.size());
        List<String> names = new java.util.ArrayList<>(pileView.size());
        List<Integer> codes = new java.util.ArrayList<>(pileView.size());
        for(BoardSnapshot.Slot slot : pileView)
        {
            // Known, not upturned.
            //
            // A pile shows what this client was TOLD, which is a different
            // question from which way the card is lying. The engine keeps extra
            // deck cards face down as a matter of storage, so asking after the
            // position turned your own Extra Deck into nine card backs -- a
            // player being kept from information they are holding.
            //
            // Reading the code alone leaks nothing, and that is a property of
            // the data rather than of this call: the server only ever sends the
            // code of a face-down card to someone entitled to it, so the
            // opponent's Extra Deck arrives with no code and still draws backs.
            boolean back = de.cas_ual_ty.dueldimension.clientutil.CardFaces.showsBack(slot, true);
            faces.add(de.cas_ual_ty.dueldimension.clientutil.CardFaces.face(slot, true, 0));
            de.cas_ual_ty.dueldimension.card.properties.Properties card = back ? null
                : de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)slot.code());
            names.add(card == null ? "" : card.getName());
            // A back has nothing to read, and saying what it hides would be
            // the leak the face itself is careful not to be.
            codes.add(back ? 0 : slot.code());
        }
        CardChooser.drawGrid(extractor, font, pileViewLabel, faces, names, codes, mouseX, mouseY,
            false);
    }

    private String label(int index)
    {
        if(index == CLOSE)
        {
            return "Close";
        }
        if(index == VIEW_DECK)
        {
            return "View Deck";
        }
        if(index == CHAIN_PREF)
        {
            return DuelClientState.chainPreference.label();
        }
        if(index == MUSIC)
        {
            return de.cas_ual_ty.dueldimension.clientutil.DuelMusic.muted()
                ? "Music: off" : "Music: on";
        }
        if(index == VOLUME)
        {
            return "Volume: " + Math.round(
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.volume() * 100F) + "%";
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
     * The open menu's width, taken again from its rows.
     * <p>
     * A row whose label changes under it -- "Chain: ON" becoming "Chain:
     * default" -- would otherwise write past the panel it is drawn in, since
     * the width was measured once when the menu opened.
     */
    private void remeasure()
    {
        choicesW = ROW_W_MIN;
        for(int index : choices)
        {
            choicesW = Math.max(choicesW, font.width(label(index)) + ROW_LABEL_PAD);
        }
    }

    /**
     * Shift, asked of the window rather than of a key event, because this is a
     * question about a key being HELD while the mouse moves and not about one
     * having been pressed. The same test the deck builder's preview uses.
     */
    private static boolean shiftHeld()
    {
        return ClientDuelField.shiftHeld();
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
