package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.CardFaces;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Your hand, during an overworld duel.
 * <p>
 * <b>The one thing that never goes in the world.</b> A board standing on the
 * ground is visible to anyone who walks up to it, so putting a hand on it would
 * turn shoulder-surfing into a game mechanic and hand a bystander a duellist's
 * cards. This is a correctness requirement dressed as a UI decision: the hand
 * stays in a private overlay that only its owner's client draws, reading only
 * that client's own {@code board.self().hand()} and nothing else.
 * <p>
 * A HUD element rather than a screen, because a screen would take the mouse and
 * the whole point of the overworld duel is that the camera stays yours. One
 * consequence worth accepting deliberately: the HUD is not extracted at all
 * while the GUI is hidden, so F1 hides the hand along with everything else.
 */
public final class HandHud implements HudElement
{
    /**
     * Life points, whose turn it is, and what is being asked.
     * <p>
     * On the duel screen all of this is drawn around the board. A board in the
     * world has no frame to put it in, and without it a duellist cannot see
     * their own life total -- which makes the board a nice thing to look at
     * rather than a place to play. Text rather than the screen's art on
     * purpose: it sits over the world and has to stay legible against whatever
     * happens to be behind it.
     */
    private static void drawReadout(GuiGraphicsExtractor extractor, Minecraft client,
        BoardSnapshot board)
    {
        int middle = extractor.guiWidth() / 2;
        // The duel's own instruments -- life bars, the phase case, the answer
        // clock -- in the same art the duel screen uses.
        DuelHud.draw(extractor, client.font, board, ClientDuelField.seat());

        // turnPlayer is ALREADY in the viewer's numbering -- the server writes
        // 0 for "the seat being served" -- so comparing it against the seat
        // index was right for seat 0 by coincidence and backwards for seat 1.
        String turn = "Turn " + board.turn() + "  -  "
            + (board.turnPlayer() == 0 ? "your turn" : "their turn");
        extractor.centeredText(client.font, turn, middle, DuelHud.BELOW, 0xFFC2C9D6);

        // What the engine is waiting for, and the key that answers it. A board
        // with no visible question is a board a player waits at.
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt =
            de.cas_ual_ty.dueldimension.clientutil.DuelClientState.prompt;
        if(prompt == null)
        {
            return;
        }
        String asking = prompt.title() == null || prompt.title().isEmpty()
            ? "Your move" : prompt.title();
        extractor.centeredText(client.font, asking, middle, DuelHud.BELOW + 12, 0xFFFFE84A);
        String hint = "[" + de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.DUEL_ACT
            .getTranslatedKeyMessage().getString() + "] act"
            + (ClientDuelTargeting.actionable() ? "  -  "
                + (ClientDuelTargeting.looking() == null ? ""
                    : ClientDuelTargeting.looking().label()) : "");
        extractor.centeredText(client.font, hint, middle, DuelHud.BELOW + 24, 0xFF7CE38B);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker delta)
    {
        // A spectator has no hand, and the board they were sent has somebody
        // else's -- redacted, but still not theirs to have laid out along the
        // bottom of their screen as though it were.
        if(!ClientDuelField.locked() || ClientDuelField.seat() < 0)
        {
            return;
        }
        // The pointer draws its own copy, because a HUD element is not
        // extracted while a screen is open. Drawing here as well would be
        // drawing it twice on the frames where both could run.
        if(Minecraft.getInstance().gui.screen() instanceof BoardPointerScreen)
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if(client.font == null)
        {
            return;
        }
        BoardSnapshot board = DuelClientState.board;
        if(board == null || board.self() == null)
        {
            return;
        }
        drawReadout(extractor, client, board);
        drawHand(extractor, board, -1);
    }

    /** The game's tick count, which is what the pulse breathes on. */
    private static float ticks()
    {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0F
            : client.level.getGameTime() % 100000L
                + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /**
     * The cards in your hand, drawn where {@link HandLayout} says.
     * <p>
     * Callable because the pointer screen has to draw it as well: a HUD element
     * is not extracted at all while a screen is open, and the pointer IS a
     * screen -- so without this the hand would vanish at exactly the moment the
     * cursor arrived to click it.
     *
     * @param highlighted the card under the cursor, or -1
     */
    public static void drawHand(GuiGraphicsExtractor extractor, BoardSnapshot board,
        int highlighted)
    {
        List<BoardSnapshot.Slot> hand = board.self() == null ? null : board.self().hand();
        if(hand == null || hand.isEmpty())
        {
            return;
        }
        HandLayout.Slot[] slots = HandLayout.slots(extractor.guiWidth(), extractor.guiHeight(),
            hand.size());

        for(int card = 0; card < hand.size(); card++)
        {
            BoardSnapshot.Slot slot = hand.get(card);
            HandLayout.Slot at = slots[card];
            // The hovered card stands up out of the fan, the way a card being
            // considered leaves the hand before it is played.
            int lift = card == highlighted ? HandLayout.hoverLift(extractor.guiHeight()) : 0;
            // A card the engine is offering says so before it is pointed at,
            // which is what stops a turn being a hunt across the whole hand.
            if(DuelHighlight.handCardIsOffered(board, card))
            {
                DuelHighlight.around(extractor, at.x(), at.y() - lift, at.width(), at.height(),
                    ticks());
            }
            // inHand is true: a set card in your OWN hand is one you are
            // allowed to look at, and this overlay is only ever drawn for its
            // owner. The concealment that matters happened on the server.
            Identifier texture = CardFaces.face(slot, true, 0);
            boolean whole = CardFaces.isCardShaped(texture);
            DdBlitUtil.blit(extractor, texture, at.x(), at.y() - lift, at.width(), at.height(),
                whole ? 0F : DuelTextures.CARD_U0, whole ? 0F : DuelTextures.CARD_V0,
                whole ? 1F : DuelTextures.CARD_U1, whole ? 1F : DuelTextures.CARD_V1,
                DdBlitUtil.NO_TINT);
        }
    }
}
