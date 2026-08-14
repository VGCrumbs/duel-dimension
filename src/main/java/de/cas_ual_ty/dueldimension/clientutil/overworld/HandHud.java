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
        String points = board.self().lifePoints() + "   vs   "
            + (board.opponent() == null ? 0 : board.opponent().lifePoints());
        extractor.centeredText(client.font, points, middle, 8, 0xFFF4D089);

        String turn = "Turn " + board.turn() + "  -  "
            + (board.turnPlayer() == ClientDuelField.seat() ? "your turn" : "their turn");
        extractor.centeredText(client.font, turn, middle, 20, 0xFFC2C9D6);

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
        extractor.centeredText(client.font, asking, middle, 34, 0xFFFFE84A);
        String hint = "[" + de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.DUEL_ACT
            .getTranslatedKeyMessage().getString() + "] act"
            + (ClientDuelTargeting.actionable() ? "  -  "
                + (ClientDuelTargeting.looking() == null ? ""
                    : ClientDuelTargeting.looking().label()) : "");
        extractor.centeredText(client.font, hint, middle, 46, 0xFF7CE38B);
    }

    /** How tall a card is drawn, in pixels; its width follows the card's shape. */
    private static final int CARD_H = 54;
    private static final int CARD_W = Math.round(CARD_H * DuelTextures.CARD_ASPECT);
    /** How much of a card's width is visible when the hand is too wide to lay flat. */
    private static final int MIN_STEP = 12;
    private static final int GAP = 3;
    /** Clear of the hotbar, the experience bar and the preload bar above them. */
    private static final int ABOVE_HOTBAR = 76;

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

        List<BoardSnapshot.Slot> hand = board.self().hand();
        if(hand == null || hand.isEmpty())
        {
            return;
        }

        // Overlap the cards when there are too many to lay side by side, the
        // way a hand of cards actually overlaps -- rather than shrinking them
        // until they are unreadable, which is the other way a hand of fifteen
        // could be made to fit.
        int screenW = extractor.guiWidth();
        int usable = Math.max(CARD_W, screenW - 40);
        int step = CARD_W + GAP;
        if(hand.size() * step > usable)
        {
            step = Math.max(MIN_STEP, (usable - CARD_W) / Math.max(1, hand.size() - 1));
        }

        int spread = CARD_W + step * (hand.size() - 1);
        int x = (screenW - spread) / 2;
        int y = extractor.guiHeight() - ABOVE_HOTBAR - CARD_H;

        for(BoardSnapshot.Slot slot : hand)
        {
            // inHand is true: a set card in your OWN hand is one you are
            // allowed to look at, and this overlay is only ever drawn for its
            // owner. The concealment that matters happened on the server.
            Identifier texture = CardFaces.face(slot, true, 0);
            boolean whole = CardFaces.isCardShaped(texture);
            DdBlitUtil.blit(extractor, texture, x, y, CARD_W, CARD_H,
                whole ? 0F : DuelTextures.CARD_U0, whole ? 0F : DuelTextures.CARD_V0,
                whole ? 1F : DuelTextures.CARD_U1, whole ? 1F : DuelTextures.CARD_V1,
                DdBlitUtil.NO_TINT);
            x += step;
        }
    }
}
